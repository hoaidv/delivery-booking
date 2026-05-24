package com.delivery.dbcommon.models;

import java.time.Instant;
import java.util.UUID;

public record DeliveryOpportunity(
		UUID id,
		int regionId,
		int zoneId,
		Instant bookingWindowStart,
		Instant bookingWindowEnd,
		int capacity,
		Instant createdAt) {
}
