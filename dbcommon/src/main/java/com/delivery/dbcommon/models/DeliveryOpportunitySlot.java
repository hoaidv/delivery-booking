package com.delivery.dbcommon.models;

import java.time.Instant;
import java.util.UUID;

/**
 * @param version Assigned by the database ({@code DEFAULT 1} on insert, incremented on update).
 *                Not set by application code on write paths.
 */
public record DeliveryOpportunitySlot(
		UUID id,
		UUID opportunityId,
		int opportunitySlot,
		UUID claimedBy,
		Instant claimedAt,
		int version) {
}
