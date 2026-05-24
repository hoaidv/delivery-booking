package com.delivery.dbcommon.models;

import java.util.UUID;

/**
 * Claim row shared by {@code delivery_opportunity_claims_timeline} and
 * {@code delivery_opportunity_claims} in ScyllaDB.
 */
public record DeliveryOpportunityClaim(
		UUID driverId,
		UUID opportunityId,
		// 01, 02, 03, ...
		// DIDNOT decide to use slot number or slot token or slot id yet
		// Keep string for simple replacement later.
		// Default to compute & save slot number here for driver in this opportunity.
		String slot,

		// When insert, must be a time-based UUID; 
		// generate upstream with com.datastax.oss.driver.api.core.uuid.Uuids.timeBased()
		UUID requestedAt,
		String metadata
) {
}
