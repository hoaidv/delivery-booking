package com.delivery.dbworker.appcore;

import com.delivery.dbcommon.dtos.ClaimMessageKeys;
import com.delivery.dbcommon.models.DeliveryOpportunitySlot;
import com.delivery.dbcommon.repository.DeliveryOpportunityRepository;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Per-consumer in-memory slot state. Cleared on Kafka partition rebalance.
 */
public class LocalSlotCache {

	private final ConcurrentMap<String, DeliveryOpportunitySlot> slots = new ConcurrentHashMap<>();
	private final DeliveryOpportunityRepository opportunityRepository;

	public LocalSlotCache(DeliveryOpportunityRepository opportunityRepository) {
		this.opportunityRepository = opportunityRepository;
	}

	public Optional<DeliveryOpportunitySlot> get(UUID opportunityId, int opportunitySlot) {
		return Optional.ofNullable(slots.get(ClaimMessageKeys.slotKey(opportunityId, opportunitySlot)));
	}

	public DeliveryOpportunitySlot getOrLoad(UUID opportunityId, int opportunitySlot) {
		String key = ClaimMessageKeys.slotKey(opportunityId, opportunitySlot);
		return slots.computeIfAbsent(
				key,
				ignored -> opportunityRepository
						.findSlotByOpportunityIdAndSlot(opportunityId, opportunitySlot)
						.orElseThrow(() -> new IllegalStateException("Delivery opportunity slot not found: " + key)));
	}

	public void put(DeliveryOpportunitySlot slot) {
		slots.put(ClaimMessageKeys.slotKey(slot.opportunityId(), slot.opportunitySlot()), slot);
	}

	public void reload(UUID opportunityId, int opportunitySlot) {
		String key = ClaimMessageKeys.slotKey(opportunityId, opportunitySlot);
		opportunityRepository
				.findSlotByOpportunityIdAndSlot(opportunityId, opportunitySlot)
				.ifPresent(slot -> slots.put(key, slot));
	}

	public void clear() {
		slots.clear();
	}
}
