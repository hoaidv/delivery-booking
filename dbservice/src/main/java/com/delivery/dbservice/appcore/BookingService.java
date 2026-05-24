package com.delivery.dbservice.appcore;

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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class BookingService {

	public static final int DEFAULT_CLAIM_PAGE_SIZE = 10;
	public static final int MAX_CLAIM_PAGE_SIZE = 100;

	private final DeliveryOpportunityCache opportunityCache;
	private final DeliveryOpportunityClaimRepository claimRepository;
	private final KafkaClaimProducer claimProducer;

	public BookingService(
			DeliveryOpportunityCache opportunityCache,
			DeliveryOpportunityClaimRepository claimRepository,
			KafkaClaimProducer claimProducer) {
		this.opportunityCache = opportunityCache;
		this.claimRepository = claimRepository;
		this.claimProducer = claimProducer;
	}

	public DeliveryOpportunityClaimDto processClaim(DeliveryOpportunityClaimRequest request) {
		// Optional<DeliveryOpportunityClaim> existing = claimRepository.findByDriverIdAndOpportunityId(
		// 		request.driverId(), request.opportunityId());
		// if (existing.isPresent()) {
		// 	return toClaimDto(existing.get());
		// }

		DeliveryOpportunity opportunity = opportunityCache
				.findOpportunity(request.opportunityId())
				.orElseThrow(() -> new DeliveryOpportunityNotFoundException(request.opportunityId()));

		assertBookingWindowOpen(opportunity);

		int opportunitySlot =
				computeSlotNumber(request.opportunityId(), request.driverId(), opportunity.capacity());

		DeliveryOpportunitySlot slot = opportunityCache
				.findSlot(request.opportunityId(), opportunitySlot)
				.orElseThrow(() -> new DeliveryOpportunitySlotUnavailableException(
						request.opportunityId(), opportunitySlot));

		assertSlotAvailable(slot, request.driverId());

		String slotLabel = formatSlot(opportunitySlot);
		UUID requestedAt = Uuids.timeBased();

		DeliveryOpportunityClaim claim = new DeliveryOpportunityClaim(
				request.driverId(),
				request.opportunityId(),
				slotLabel,
				requestedAt,
				request.metadata());

		claimRepository.insertTimeline(claim);
		claimRepository.upsertLookup(claim);

		DeliveryOpportunityClaimMessage message = KafkaClaimProducer.toMessage(
				request.driverId(), request.opportunityId(), opportunitySlot, requestedAt);
		claimProducer.publish(message);

		return toClaimDto(claim);
	}

	public List<DeliveryOpportunityClaimDto> listClaims(
			UUID driverId, Optional<UUID> beforeRequestedAt, int limit) {
		int pageSize = normalizePageSize(limit);
		List<DeliveryOpportunityClaim> timelineRows = claimRepository.findByDriverIdAndRequestedAtRange(
				driverId, beforeRequestedAt, pageSize);
		return dedupeByOpportunityId(timelineRows).stream().map(this::toClaimDto).toList();
	}

	public Optional<DeliveryOpportunityClaimDto> getClaim(UUID driverId, UUID opportunityId) {
		return claimRepository
				.findByDriverIdAndOpportunityId(driverId, opportunityId)
				.map(this::toClaimDto);
	}

	private DeliveryOpportunityClaimDto toClaimDto(DeliveryOpportunityClaim claim) {
		return DeliveryOpportunityClaimDto.from(claim, resolveClaimStatus(claim));
	}

	static DeliveryOpportunityClaimStatus resolveClaimStatus(
			DeliveryOpportunityCache cache, DeliveryOpportunityClaim claim) {
		int slotNumber = Integer.parseInt(claim.slot());

		Optional<DeliveryOpportunitySlot> slot = cache.findSlot(claim.opportunityId(), slotNumber);
		if (slot.isEmpty()) {
			return DeliveryOpportunityClaimStatus.PROCESSING;
		}

		UUID claimedBy = slot.get().claimedBy();
		if (claimedBy == null) {
			return DeliveryOpportunityClaimStatus.PROCESSING;
		}
		if (claimedBy.equals(claim.driverId())) {
			return DeliveryOpportunityClaimStatus.SUCCESSFUL;
		}
		return DeliveryOpportunityClaimStatus.REJECTED;
	}

	private DeliveryOpportunityClaimStatus resolveClaimStatus(DeliveryOpportunityClaim claim) {
		return resolveClaimStatus(opportunityCache, claim);
	}

	private static void assertBookingWindowOpen(DeliveryOpportunity opportunity) {
		Instant now = Instant.now();
		if (now.isBefore(opportunity.bookingWindowStart()) || !now.isBefore(opportunity.bookingWindowEnd())) {
			throw new BookingWindowClosedException(opportunity.id());
		}
	}

	private static void assertSlotAvailable(DeliveryOpportunitySlot slot, UUID driverId) {
		if (slot.claimedBy() != null && !slot.claimedBy().equals(driverId)) {
			throw new DeliveryOpportunitySlotUnavailableException(slot.opportunityId(), slot.opportunitySlot());
		}
	}

	static List<DeliveryOpportunityClaim> dedupeByOpportunityId(List<DeliveryOpportunityClaim> rows) {
		Map<UUID, DeliveryOpportunityClaim> latestByOpportunity = new LinkedHashMap<>();
		for (DeliveryOpportunityClaim row : rows) {
			latestByOpportunity.putIfAbsent(row.opportunityId(), row);
		}
		return new ArrayList<>(latestByOpportunity.values());
	}

	static int normalizePageSize(int limit) {
		if (limit <= 0) {
			return DEFAULT_CLAIM_PAGE_SIZE;
		}
		return Math.min(limit, MAX_CLAIM_PAGE_SIZE);
	}

	/**
	 * {@code hash(opportunity_id, driver_id) % capacity} → slot number in {@code 1..capacity}.
	 */
	static int computeSlotNumber(UUID opportunityId, UUID driverId, int capacity) {
		int hash = Objects.hash(opportunityId, driverId);
		return Math.floorMod(hash, capacity) + 1;
	}

	static String formatSlot(int slotNumber) {
		return String.format("%d", slotNumber);
	}
}
