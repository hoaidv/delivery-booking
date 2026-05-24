package com.delivery.dbservice.dtos;

import com.delivery.dbcommon.models.DeliveryOpportunity;
import java.time.Instant;
import java.util.UUID;

public record DeliveryOpportunityDto(
		UUID id,
		int regionId,
		int zoneId,
		Instant bookingWindowStart,
		Instant bookingWindowEnd,
		int capacity,
		Instant createdAt) {

	public static DeliveryOpportunityDto from(DeliveryOpportunity opportunity) {
		return new DeliveryOpportunityDto(
				opportunity.id(),
				opportunity.regionId(),
				opportunity.zoneId(),
				opportunity.bookingWindowStart(),
				opportunity.bookingWindowEnd(),
				opportunity.capacity(),
				opportunity.createdAt());
	}
}
