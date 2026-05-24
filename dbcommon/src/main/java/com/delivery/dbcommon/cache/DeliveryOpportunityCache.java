package com.delivery.dbcommon.cache;

import com.delivery.dbcommon.models.DeliveryOpportunity;
import com.delivery.dbcommon.models.DeliveryOpportunitySlot;
import java.util.Optional;
import java.util.UUID;

/**
 * Cache-aside access to delivery opportunities and slots. 
 * Implementations hide Redis / Database from callers.
 * <br><br>
 * This is shared between `dbservice` and `dbworker`.
 *
 * @see RedisDeliveryOpportunityCache
 * @see DirectDeliveryOpportunityCache
 */
public interface DeliveryOpportunityCache {

	Optional<DeliveryOpportunity> findOpportunity(UUID opportunityId);

	Optional<DeliveryOpportunitySlot> findSlot(UUID opportunityId, int opportunitySlot);

	/** Best-effort write of slot state to the shared cache (e.g. Redis). */
	void writeSlot(DeliveryOpportunitySlot slot);
}
