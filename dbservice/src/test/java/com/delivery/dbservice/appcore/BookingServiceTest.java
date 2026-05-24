package com.delivery.dbservice.appcore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.delivery.dbcommon.cache.DeliveryOpportunityCache;
import com.delivery.dbcommon.dtos.DeliveryOpportunityClaimMessage;
import com.delivery.dbcommon.models.DeliveryOpportunity;
import com.delivery.dbcommon.models.DeliveryOpportunityClaim;
import com.delivery.dbcommon.models.DeliveryOpportunitySlot;
import com.delivery.dbcommon.repository.DeliveryOpportunityClaimRepository;
import com.delivery.dbservice.dtos.DeliveryOpportunityClaimDto;
import com.delivery.dbservice.dtos.DeliveryOpportunityClaimRequest;
import com.delivery.dbservice.dtos.DeliveryOpportunityClaimStatus;
import com.delivery.dbservice.infra.KafkaClaimProducer;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

	@Mock
	private DeliveryOpportunityCache opportunityCache;

	@Mock
	private DeliveryOpportunityClaimRepository claimRepository;

	@Mock
	private KafkaClaimProducer claimProducer;

	@InjectMocks
	private BookingService bookingService;

	@Test
	void processClaim_writesTimelineLookupAndKafka() {
		UUID driverId = UUID.randomUUID();
		UUID opportunityId = UUID.randomUUID();
		Instant windowStart = Instant.parse("2026-01-01T00:00:00Z");
		Instant windowEnd = Instant.parse("2027-01-01T00:00:00Z");
		DeliveryOpportunity opportunity = new DeliveryOpportunity(
				opportunityId, 1, 1, windowStart, windowEnd, 5, Instant.now());
		int slotNumber = BookingService.computeSlotNumber(opportunityId, driverId, 5);
		DeliveryOpportunitySlot slot = new DeliveryOpportunitySlot(
				UUID.randomUUID(), opportunityId, slotNumber, null, null, 1);

		when(claimRepository.findByDriverIdAndOpportunityId(driverId, opportunityId))
				.thenReturn(Optional.empty());
		when(opportunityCache.findOpportunity(opportunityId)).thenReturn(Optional.of(opportunity));
		when(opportunityCache.findSlot(opportunityId, slotNumber)).thenReturn(Optional.of(slot));

		DeliveryOpportunityClaimRequest request =
				new DeliveryOpportunityClaimRequest(driverId, opportunityId, "{\"source\":\"test\"}");

		DeliveryOpportunityClaimDto result = bookingService.processClaim(request);

		assertEquals(BookingService.formatSlot(slotNumber), result.slot());
		assertEquals(driverId, result.driverId());
		assertEquals(opportunityId, result.opportunityId());
		assertEquals("{\"source\":\"test\"}", result.metadata());
		assertEquals(DeliveryOpportunityClaimStatus.PROCESSING, result.status());

		ArgumentCaptor<DeliveryOpportunityClaim> claimCaptor =
				ArgumentCaptor.forClass(DeliveryOpportunityClaim.class);
		verify(claimRepository).insertTimeline(claimCaptor.capture());
		verify(claimRepository).upsertLookup(claimCaptor.getValue());

		ArgumentCaptor<DeliveryOpportunityClaimMessage> messageCaptor =
				ArgumentCaptor.forClass(DeliveryOpportunityClaimMessage.class);
		verify(claimProducer).publish(messageCaptor.capture());

		DeliveryOpportunityClaim persisted = claimCaptor.getValue();
		DeliveryOpportunityClaimMessage message = messageCaptor.getValue();
		assertEquals(result.driverId(), persisted.driverId());
		assertEquals(result.opportunityId(), persisted.opportunityId());
		assertEquals(result.slot(), persisted.slot());
		assertEquals(result.requestedAt(), persisted.requestedAt());
		assertEquals(slotNumber, message.payload().opSlot());
		assertEquals(
				KafkaClaimProducer.claimKey(opportunityId, slotNumber),
				KafkaClaimProducer.claimKey(message.payload().opId(), message.payload().opSlot()));
	}

	@Test
	void processClaim_isIdempotentWhenLookupExists() {
		UUID driverId = UUID.randomUUID();
		UUID opportunityId = UUID.randomUUID();
		UUID requestedAt = Uuids.timeBased();
		DeliveryOpportunityClaim existing =
				new DeliveryOpportunityClaim(driverId, opportunityId, "03", requestedAt, null);

		when(claimRepository.findByDriverIdAndOpportunityId(driverId, opportunityId))
				.thenReturn(Optional.of(existing));
		when(opportunityCache.findSlot(opportunityId, 3)).thenReturn(Optional.empty());

		DeliveryOpportunityClaimDto result = bookingService.processClaim(
				new DeliveryOpportunityClaimRequest(driverId, opportunityId, null));

		assertEquals(driverId, result.driverId());
		assertEquals(opportunityId, result.opportunityId());
		assertEquals("03", result.slot());
		assertEquals(requestedAt, result.requestedAt());
		assertEquals(DeliveryOpportunityClaimStatus.PROCESSING, result.status());
		verify(opportunityCache, never()).findOpportunity(any());
		verify(claimRepository, never()).insertTimeline(any());
		verify(claimProducer, never()).publish(any());
	}

	@Test
	void processClaim_rejectsClosedBookingWindow() {
		UUID driverId = UUID.randomUUID();
		UUID opportunityId = UUID.randomUUID();
		DeliveryOpportunity opportunity = new DeliveryOpportunity(
				opportunityId,
				1,
				1,
				Instant.parse("2099-01-01T00:00:00Z"),
				Instant.parse("2099-01-02T00:00:00Z"),
				5,
				Instant.now());

		when(claimRepository.findByDriverIdAndOpportunityId(driverId, opportunityId))
				.thenReturn(Optional.empty());
		when(opportunityCache.findOpportunity(opportunityId)).thenReturn(Optional.of(opportunity));

		assertThrows(
				BookingWindowClosedException.class,
				() -> bookingService.processClaim(
						new DeliveryOpportunityClaimRequest(driverId, opportunityId, null)));
	}

	@Test
	void processClaim_rejectsSlotClaimedByAnotherDriver() {
		UUID driverId = UUID.randomUUID();
		UUID otherDriver = UUID.randomUUID();
		UUID opportunityId = UUID.randomUUID();
		DeliveryOpportunity opportunity = new DeliveryOpportunity(
				opportunityId,
				1,
				1,
				Instant.parse("2026-01-01T00:00:00Z"),
				Instant.parse("2027-01-01T00:00:00Z"),
				5,
				Instant.now());
		int slotNumber = BookingService.computeSlotNumber(opportunityId, driverId, 5);
		DeliveryOpportunitySlot slot = new DeliveryOpportunitySlot(
				UUID.randomUUID(), opportunityId, slotNumber, otherDriver, Instant.now(), 2);

		when(claimRepository.findByDriverIdAndOpportunityId(driverId, opportunityId))
				.thenReturn(Optional.empty());
		when(opportunityCache.findOpportunity(opportunityId)).thenReturn(Optional.of(opportunity));
		when(opportunityCache.findSlot(opportunityId, slotNumber)).thenReturn(Optional.of(slot));

		assertThrows(
				DeliveryOpportunitySlotUnavailableException.class,
				() -> bookingService.processClaim(
						new DeliveryOpportunityClaimRequest(driverId, opportunityId, null)));
	}

	@Test
	void listClaims_dedupesByOpportunityIdKeepingNewest() {
		UUID driverId = UUID.randomUUID();
		UUID opportunityId = UUID.randomUUID();
		UUID older = Uuids.startOf(Instant.parse("2026-01-01T00:00:00Z").toEpochMilli());
		UUID newer = Uuids.startOf(Instant.parse("2026-01-02T00:00:00Z").toEpochMilli());

		when(claimRepository.findByDriverIdAndRequestedAtRange(driverId, Optional.empty(), 10))
				.thenReturn(List.of(
						new DeliveryOpportunityClaim(driverId, opportunityId, "1", newer, null),
						new DeliveryOpportunityClaim(driverId, opportunityId, "2", older, null)));
		when(opportunityCache.findSlot(opportunityId, 1))
				.thenReturn(Optional.of(new DeliveryOpportunitySlot(
						UUID.randomUUID(), opportunityId, 1, driverId, Instant.now(), 2)));

		List<DeliveryOpportunityClaimDto> result = bookingService.listClaims(driverId, Optional.empty(), 10);

		assertEquals(1, result.size());
		assertEquals(newer, result.getFirst().requestedAt());
		assertEquals("1", result.getFirst().slot());
		assertEquals(DeliveryOpportunityClaimStatus.SUCCESSFUL, result.getFirst().status());
		verify(claimRepository).findByDriverIdAndRequestedAtRange(eq(driverId), eq(Optional.empty()), eq(10));
	}

	@Test
	void getClaim_returnsStatusFromCachedSlot() {
		UUID driverId = UUID.randomUUID();
		UUID otherDriver = UUID.randomUUID();
		UUID opportunityId = UUID.randomUUID();
		UUID requestedAt = Uuids.timeBased();
		DeliveryOpportunityClaim claim =
				new DeliveryOpportunityClaim(driverId, opportunityId, "2", requestedAt, null);

		when(claimRepository.findByDriverIdAndOpportunityId(driverId, opportunityId))
				.thenReturn(Optional.of(claim));
		when(opportunityCache.findSlot(opportunityId, 2))
				.thenReturn(Optional.of(new DeliveryOpportunitySlot(
						UUID.randomUUID(), opportunityId, 2, otherDriver, Instant.now(), 2)));

		Optional<DeliveryOpportunityClaimDto> result = bookingService.getClaim(driverId, opportunityId);

		assertTrue(result.isPresent());
		assertEquals(DeliveryOpportunityClaimStatus.REJECTED, result.get().status());
	}

	@Test
	void resolveClaimStatus_processingWhenSlotUnclaimed() {
		UUID driverId = UUID.randomUUID();
		UUID opportunityId = UUID.randomUUID();
		DeliveryOpportunityClaim claim =
				new DeliveryOpportunityClaim(driverId, opportunityId, "1", Uuids.timeBased(), null);
		DeliveryOpportunitySlot slot = new DeliveryOpportunitySlot(
				UUID.randomUUID(), opportunityId, 1, null, null, 1);

		when(opportunityCache.findSlot(opportunityId, 1)).thenReturn(Optional.of(slot));

		assertEquals(
				DeliveryOpportunityClaimStatus.PROCESSING,
				BookingService.resolveClaimStatus(opportunityCache, claim));
	}

	@Test
	void dedupeByOpportunityId_preservesRequestedAtOrder() {
		UUID driverId = UUID.randomUUID();
		UUID op1 = UUID.randomUUID();
		UUID op2 = UUID.randomUUID();
		UUID t1 = Uuids.timeBased();
		UUID t2 = Uuids.timeBased();

		List<DeliveryOpportunityClaim> deduped = BookingService.dedupeByOpportunityId(List.of(
				new DeliveryOpportunityClaim(driverId, op1, "01", t1, null),
				new DeliveryOpportunityClaim(driverId, op2, "02", t2, null),
				new DeliveryOpportunityClaim(driverId, op1, "03", Uuids.timeBased(), null)));

		assertEquals(2, deduped.size());
		assertEquals(op1, deduped.get(0).opportunityId());
		assertEquals("01", deduped.get(0).slot());
		assertEquals(op2, deduped.get(1).opportunityId());
	}

	@Test
	void computeSlotNumber_isInRangeOneToCapacity() {
		UUID opportunityId = UUID.fromString("00000000-0000-0000-0000-000000000001");
		UUID driverId = UUID.fromString("00000000-0000-0000-0000-000000000002");
		int slot = BookingService.computeSlotNumber(opportunityId, driverId, 10);
		assertTrue(slot >= 1 && slot <= 10);
	}
}
