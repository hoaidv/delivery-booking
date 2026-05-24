package com.delivery.dbcommon.infra.redis;

import com.delivery.dbcommon.models.DeliveryOpportunitySlot;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

final class SlotCacheRedisScript {

	static final String FIELD_VERSION = "version";
	static final String FIELD_CLAIMED_BY = "claimed_by";
	static final String FIELD_CLAIMED_AT = "claimed_at";
	static final String FIELD_ID = "id";

	private static final DefaultRedisScript<Long> SET_IF_NEWER = new DefaultRedisScript<>();

	static {
		SET_IF_NEWER.setResultType(Long.class);
		SET_IF_NEWER.setScriptSource(new ResourceScriptSource(new ClassPathResource("redis/set-slot-if-newer.lua")));
	}

	private SlotCacheRedisScript() {
	}

	static DefaultRedisScript<Long> setIfNewer() {
		return SET_IF_NEWER;
	}

	static List<String> writeArgs(DeliveryOpportunitySlot slot) {
		return List.of(
				Integer.toString(slot.version()),
				slot.claimedBy() == null ? "" : slot.claimedBy().toString(),
				slot.claimedAt() == null ? "" : slot.claimedAt().toString(),
				slot.id().toString());
	}
}
