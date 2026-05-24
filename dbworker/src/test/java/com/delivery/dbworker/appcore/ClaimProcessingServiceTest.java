package com.delivery.dbworker.appcore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.delivery.dbcommon.cache.DeliveryOpportunityCache;
import com.delivery.dbcommon.dtos.DeliveryOpportunityClaimMessage;
import com.delivery.dbcommon.models.DeliveryOpportunitySlot;
import com.delivery.dbcommon.repository.DeliveryOpportunityRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClaimProcessingServiceTest {

	private static final UUID OPPORTUNITY_ID = UUID.randomUUID();
	private static final int SLOT_NUMBER = 3;
	private static final UUID DRIVER_A = UUID.randomUUID();
	private static final UUID DRIVER_B = UUID.randomUUID();
	private static final UUID SLOT_ID = UUID.randomUUID();

	@Mock
	private DeliveryOpportunityRepository opportunityRepository;

	@Mock
	private DeliveryOpportunityCache sharedCache;

	private LocalSlotCache slotCache;
	private ClaimProcessingService service;

	@BeforeEach
	void setUp() {
		slotCache = new LocalSlotCache(opportunityRepository);
		service = new ClaimProcessingService(slotCache, opportunityRepository, sharedCache);
	}

	@Test
	void process_rejectsWhenSlotAlreadyClaimedInLocalCache() {
		DeliveryOpportunitySlot claimed = slot(SLOT_ID, DRIVER_A, 1);
		when(opportunityRepository.findSlotByOpportunityIdAndSlot(OPPORTUNITY_ID, SLOT_NUMBER))
				.thenReturn(Optional.of(claimed));

		assertEquals(ClaimProcessingResult.REJECTED, service.process(message(DRIVER_B)));
		verify(opportunityRepository, never())
				.claimSlotIfUnclaimed(any(), eq(SLOT_NUMBER), any(), any());
	}

	@Test
	void process_claimsSlotAndUpdatesCaches() {
		DeliveryOpportunitySlot open = slot(SLOT_ID, null, 1);
		DeliveryOpportunitySlot won = slot(SLOT_ID, DRIVER_A, 2);
		when(opportunityRepository.findSlotByOpportunityIdAndSlot(OPPORTUNITY_ID, SLOT_NUMBER))
				.thenReturn(Optional.of(open));
		when(opportunityRepository.claimSlotIfUnclaimed(
						eq(OPPORTUNITY_ID), eq(SLOT_NUMBER), eq(DRIVER_A), any(Instant.class)))
				.thenReturn(Optional.of(won));

		assertEquals(ClaimProcessingResult.CLAIMED, service.process(message(DRIVER_A)));

		verify(sharedCache).writeSlot(won);
		assertEquals(Optional.of(won), slotCache.get(OPPORTUNITY_ID, SLOT_NUMBER));
	}

	@Test
	void process_rejectsWhenDatabaseClaimLostRace() {
		DeliveryOpportunitySlot open = slot(SLOT_ID, null, 1);
		DeliveryOpportunitySlot claimedByOther = slot(SLOT_ID, DRIVER_B, 2);
		when(opportunityRepository.findSlotByOpportunityIdAndSlot(OPPORTUNITY_ID, SLOT_NUMBER))
				.thenReturn(Optional.of(open))
				.thenReturn(Optional.of(claimedByOther));
		when(opportunityRepository.claimSlotIfUnclaimed(
						eq(OPPORTUNITY_ID), eq(SLOT_NUMBER), eq(DRIVER_A), any(Instant.class)))
				.thenReturn(Optional.empty());

		assertEquals(ClaimProcessingResult.REJECTED, service.process(message(DRIVER_A)));

		verify(sharedCache, never()).writeSlot(any());
		assertEquals(Optional.of(claimedByOther), slotCache.get(OPPORTUNITY_ID, SLOT_NUMBER));
	}

	private static DeliveryOpportunityClaimMessage message(UUID driverId) {
		return new DeliveryOpportunityClaimMessage(
				UUID.randomUUID().toString(),
				"driver.claim.opportunity",
				1,
				new DeliveryOpportunityClaimMessage.Payload(driverId, OPPORTUNITY_ID, SLOT_NUMBER, 1_700_000_000L));
	}

	private static DeliveryOpportunitySlot slot(UUID id, UUID claimedBy, int version) {
		return new DeliveryOpportunitySlot(
				id, OPPORTUNITY_ID, SLOT_NUMBER, claimedBy, claimedBy == null ? null : Instant.now(), version);
	}
}
