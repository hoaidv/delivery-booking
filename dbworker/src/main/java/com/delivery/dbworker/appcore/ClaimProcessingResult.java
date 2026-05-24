package com.delivery.dbworker.appcore;

public enum ClaimProcessingResult {
	/** 
	 * Slot was not claimed. Tried to claim but failed because of contention. 
	 * THIS CANNOT HAPPEN as we SERIALIZE ALL CLAIMS TO THE SAME SLOT.
	 */
	FAILED,
	/** Slot was already claimed; no database write. */
	REJECTED,
	/** This driver won the slot (database row updated). */
	CLAIMED
}
