package com.delivery.dbservice.appcore;

import java.util.UUID;

public class DeliveryOpportunityNotFoundException extends RuntimeException {

	public DeliveryOpportunityNotFoundException(UUID id) {
		super("Delivery opportunity not found: " + id);
	}
}
