package com.delivery.dbcommon.dtos;

import java.util.UUID;

/**
 * Kafka value for driver claim events. Producer key format: {@code {op_id}#{op_slot}}.
 */
public record DeliveryOpportunityClaimMessage(
		String eventId,
		String eventType,
		int version,
		Payload payload) {

	public record Payload(
			UUID driverId,
			UUID opId,
			int opSlot,
			long requestedAt) {
	}
}
