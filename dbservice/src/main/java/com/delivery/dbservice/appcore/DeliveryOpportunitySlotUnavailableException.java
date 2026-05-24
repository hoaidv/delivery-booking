package com.delivery.dbservice.appcore;

import java.util.UUID;

public class DeliveryOpportunitySlotUnavailableException extends RuntimeException {

	public DeliveryOpportunitySlotUnavailableException(UUID opportunityId, int opportunitySlot) {
		super("delivery opportunity slot is unavailable: opportunity="
				+ opportunityId
				+ " slot="
				+ opportunitySlot);
	}
}
