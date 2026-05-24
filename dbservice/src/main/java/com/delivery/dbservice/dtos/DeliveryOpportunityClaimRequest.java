package com.delivery.dbservice.dtos;

import java.util.UUID;

public record DeliveryOpportunityClaimRequest(
		UUID driverId,
		UUID opportunityId,
		String metadata) {
}
