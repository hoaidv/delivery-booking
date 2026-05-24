package com.delivery.dbservice.appcore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.delivery.dbservice.dtos.CreateDeliveryOpportunityReq;
import com.delivery.dbservice.dtos.DeliveryOpportunityDto;
import com.delivery.dbservice.dtos.UpdateDeliveryOpportunityReq;
import java.time.Instant;
import com.delivery.dbservice.infra.KafkaClaimProducer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class DeliveryOpportunityServiceTest {

	@MockitoBean
	private KafkaClaimProducer claimProducer;

	@Autowired
	private DeliveryOpportunityService deliveryOpportunityService;

	@Test
	void createListGetAndUpdate() {
		Instant windowStart = Instant.now().plusSeconds(7200);
		Instant windowEnd = windowStart.plusSeconds(3600);

		CreateDeliveryOpportunityReq createReq = new CreateDeliveryOpportunityReq(
				1, 2, windowStart, windowEnd, 3);
		DeliveryOpportunityDto created = deliveryOpportunityService.create(createReq);

		assertNotNull(created.id());
		assertNotNull(created.createdAt());
		assertEquals(1, created.regionId());
		assertEquals(3, created.capacity());

		assertEquals(1, deliveryOpportunityService.list().size());
		assertTrue(deliveryOpportunityService.findById(created.id()).isPresent());

		UpdateDeliveryOpportunityReq updateReq = new UpdateDeliveryOpportunityReq(
				1, 2, windowStart, windowEnd, 5);
		DeliveryOpportunityDto updated =
				deliveryOpportunityService.update(created.id(), updateReq);
		assertEquals(5, updated.capacity());
	}

	@Test
	void rejectsInvalidRegionId() {
		Instant windowStart = Instant.now().plusSeconds(7200);
		Instant windowEnd = windowStart.plusSeconds(3600);

		assertThrows(
				DeliveryOpportunityValidationException.class,
				() -> deliveryOpportunityService.create(
						new CreateDeliveryOpportunityReq(0, 1, windowStart, windowEnd, 1)));
	}

	@Test
	void rejectsUpdateWithinOneHourOfWindow() {
		Instant windowStart = Instant.now().plusSeconds(1800);
		Instant windowEnd = windowStart.plusSeconds(3600);

		DeliveryOpportunityDto created = deliveryOpportunityService.create(
				new CreateDeliveryOpportunityReq(1, 1, windowStart, windowEnd, 1));

		assertThrows(
				DeliveryOpportunityUpdateForbiddenException.class,
				() -> deliveryOpportunityService.update(
						created.id(),
						new UpdateDeliveryOpportunityReq(1, 1, windowStart, windowEnd, 1)));
	}
}
