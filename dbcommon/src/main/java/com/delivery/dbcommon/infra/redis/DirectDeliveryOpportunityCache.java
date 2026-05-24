package com.delivery.dbcommon.infra.redis;

import com.delivery.dbcommon.cache.DeliveryOpportunityCache;
import com.delivery.dbcommon.models.DeliveryOpportunity;
import com.delivery.dbcommon.models.DeliveryOpportunitySlot;
import com.delivery.dbcommon.repository.DeliveryOpportunityRepository;
import java.util.Optional;
import java.util.UUID;

/** Pass-through cache for tests when Redis is not available. */
public class DirectDeliveryOpportunityCache implements DeliveryOpportunityCache {

	private final DeliveryOpportunityRepository opportunityRepository;

	public DirectDeliveryOpportunityCache(DeliveryOpportunityRepository opportunityRepository) {
		this.opportunityRepository = opportunityRepository;
	}

	@Override
	public Optional<DeliveryOpportunity> findOpportunity(UUID opportunityId) {
		return opportunityRepository.findById(opportunityId);
	}

	@Override
	public Optional<DeliveryOpportunitySlot> findSlot(UUID opportunityId, int opportunitySlot) {
		return opportunityRepository.findSlotByOpportunityIdAndSlot(opportunityId, opportunitySlot);
	}

	@Override
	public void writeSlot(DeliveryOpportunitySlot slot) {
		// no-op: tests and non-Redis profiles have no shared cache to update
	}
}
