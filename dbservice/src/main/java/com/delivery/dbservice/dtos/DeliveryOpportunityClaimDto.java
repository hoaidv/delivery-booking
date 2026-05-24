package com.delivery.dbservice.dtos;

import com.delivery.dbcommon.models.DeliveryOpportunityClaim;
import java.util.UUID;

public record DeliveryOpportunityClaimDto(
		UUID driverId,
		UUID opportunityId,
		String slot,
		UUID requestedAt,
		String metadata,
		DeliveryOpportunityClaimStatus status) {

	public static DeliveryOpportunityClaimDto from(
			DeliveryOpportunityClaim claim, DeliveryOpportunityClaimStatus status) {
		return new DeliveryOpportunityClaimDto(
				claim.driverId(),
				claim.opportunityId(),
				claim.slot(),
				claim.requestedAt(),
				claim.metadata(),
				status);
	}
}
