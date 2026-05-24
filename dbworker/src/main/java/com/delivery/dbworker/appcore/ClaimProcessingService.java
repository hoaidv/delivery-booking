package com.delivery.dbworker.appcore;

import com.delivery.dbcommon.cache.DeliveryOpportunityCache;
import com.delivery.dbcommon.dtos.DeliveryOpportunityClaimMessage;
import com.delivery.dbcommon.models.DeliveryOpportunitySlot;
import com.delivery.dbcommon.repository.DeliveryOpportunityRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClaimProcessingService {

	private static final Logger log = LoggerFactory.getLogger(ClaimProcessingService.class);

	private final LocalSlotCache slotCache;
	private final DeliveryOpportunityRepository opportunityRepository;
	private final DeliveryOpportunityCache sharedCache;

	public ClaimProcessingService(
			LocalSlotCache slotCache,
			DeliveryOpportunityRepository opportunityRepository,
			DeliveryOpportunityCache sharedCache) {
		this.slotCache = slotCache;
		this.opportunityRepository = opportunityRepository;
		this.sharedCache = sharedCache;
	}

	public ClaimProcessingResult process(DeliveryOpportunityClaimMessage message) {
		UUID opportunityId = message.payload().opId();
		int opportunitySlot = message.payload().opSlot();
		UUID driverId = message.payload().driverId();

		DeliveryOpportunitySlot slot = slotCache.getOrLoad(opportunityId, opportunitySlot);
		if (slot.claimedBy() != null) {
			return slot.claimedBy().equals(driverId)
					? ClaimProcessingResult.CLAIMED
					: ClaimProcessingResult.REJECTED;
		}

		Instant claimedAt = Instant.now();
		Optional<DeliveryOpportunitySlot> claimed = opportunityRepository.claimSlotIfUnclaimed(
				opportunityId, opportunitySlot, driverId, claimedAt);
		if (claimed.isEmpty()) {
			return ClaimProcessingResult.REJECTED;
		}

		DeliveryOpportunitySlot updated = claimed.get();
		slotCache.put(updated);
		writeSlotToRedisBestEffort(updated);
		return ClaimProcessingResult.CLAIMED;
	}

	private void writeSlotToRedisBestEffort(DeliveryOpportunitySlot slot) {
		try {
			sharedCache.writeSlot(slot);
		} catch (RuntimeException ex) {
			log.warn(
					"Failed to update Redis slot cache for {}#{}; continuing",
					slot.opportunityId(),
					slot.opportunitySlot(),
					ex);
		}
	}
}
