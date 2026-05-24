package com.delivery.dbcommon.repository;

import com.delivery.dbcommon.models.DeliveryOpportunity;
import com.delivery.dbcommon.models.DeliveryOpportunitySlot;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliveryOpportunityRepository {

	DeliveryOpportunity create(DeliveryOpportunity opportunity);

	Optional<DeliveryOpportunity> findById(UUID id);

	List<DeliveryOpportunity> list();

	DeliveryOpportunity update(DeliveryOpportunity opportunity);

	void delete(UUID id);

	DeliveryOpportunitySlot createSlot(DeliveryOpportunitySlot slot);

	Optional<DeliveryOpportunitySlot> findSlot(UUID slotId);

	Optional<DeliveryOpportunitySlot> findSlotByOpportunityIdAndSlot(
			UUID opportunityId, int opportunitySlot);

	/**
	 * Idempotent claim: updates the row only when {@code claimed_by} is still null.
	 *
	 * @return the updated row when this invocation won the slot, otherwise empty
	 */
	Optional<DeliveryOpportunitySlot> claimSlotIfUnclaimed(
			UUID opportunityId, int opportunitySlot, UUID driverId, Instant claimedAt);

	List<DeliveryOpportunitySlot> listSlotsByOpportunityId(UUID opportunityId);

	DeliveryOpportunitySlot updateSlot(DeliveryOpportunitySlot slot);

	void deleteSlot(UUID slotId);

	int countClaimedSlotsAbove(UUID opportunityId, int maxSlotNumber);

	void deleteUnclaimedSlotsAbove(UUID opportunityId, int maxSlotNumber);
}
