package com.delivery.dbcommon.repository;

import com.delivery.dbcommon.models.DeliveryOpportunityClaim;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliveryOpportunityClaimRepository {

	void insertTimeline(DeliveryOpportunityClaim claim);

	void upsertLookup(DeliveryOpportunityClaim claim);

	List<DeliveryOpportunityClaim> findByDriverId(UUID driverId);

	List<DeliveryOpportunityClaim> findByDriverIdAndRequestedAtRange(
			UUID driverId, Optional<UUID> beforeRequestedAt, int limit);

	Optional<DeliveryOpportunityClaim> findByDriverIdAndOpportunityId(
			UUID driverId, UUID opportunityId);
}
