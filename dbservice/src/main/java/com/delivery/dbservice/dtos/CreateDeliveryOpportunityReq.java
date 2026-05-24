package com.delivery.dbservice.dtos;

import java.time.Instant;

public record CreateDeliveryOpportunityReq(
		int regionId,
		int zoneId,
		Instant bookingWindowStart,
		Instant bookingWindowEnd,
		int capacity) {
}
