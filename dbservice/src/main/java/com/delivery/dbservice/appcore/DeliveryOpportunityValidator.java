package com.delivery.dbservice.appcore;

import com.delivery.dbservice.dtos.CreateDeliveryOpportunityReq;
import com.delivery.dbservice.dtos.UpdateDeliveryOpportunityReq;
import java.time.Instant;

final class DeliveryOpportunityValidator {

	private DeliveryOpportunityValidator() {
	}

	static void validateCreate(CreateDeliveryOpportunityReq request) {
		validateFields(
				request.regionId(),
				request.zoneId(),
				request.bookingWindowStart(),
				request.bookingWindowEnd(),
				request.capacity());
	}

	static void validateUpdate(UpdateDeliveryOpportunityReq request) {
		validateFields(
				request.regionId(),
				request.zoneId(),
				request.bookingWindowStart(),
				request.bookingWindowEnd(),
				request.capacity());
	}

	private static void validateFields(
			int regionId,
			int zoneId,
			Instant bookingWindowStart,
			Instant bookingWindowEnd,
			int capacity) {
		if (regionId <= 0) {
			throw new DeliveryOpportunityValidationException("regionId must be greater than 0");
		}
		if (zoneId <= 0) {
			throw new DeliveryOpportunityValidationException("zoneId must be greater than 0");
		}
		if (capacity <= 0) {
			throw new DeliveryOpportunityValidationException("capacity must be greater than 0");
		}
		if (bookingWindowStart == null || bookingWindowEnd == null) {
			throw new DeliveryOpportunityValidationException(
					"bookingWindowStart and bookingWindowEnd are required (ISO-8601 with timezone)");
		}
		if (!bookingWindowStart.isBefore(bookingWindowEnd)) {
			throw new DeliveryOpportunityValidationException(
					"bookingWindowStart must be before bookingWindowEnd");
		}
	}
}
