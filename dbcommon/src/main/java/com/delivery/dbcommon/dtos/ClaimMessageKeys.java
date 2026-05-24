package com.delivery.dbcommon.dtos;

import java.util.UUID;

/** Kafka message key format for delivery opportunity claims: {@code {op_id}#{op_slot}}. */
public final class ClaimMessageKeys {

	private ClaimMessageKeys() {
	}

	public static String slotKey(UUID opportunityId, int opportunitySlot) {
		return opportunityId + "#" + opportunitySlot;
	}
}
