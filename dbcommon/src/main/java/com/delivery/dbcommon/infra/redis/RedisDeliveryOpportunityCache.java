package com.delivery.dbcommon.infra.redis;

import com.delivery.dbcommon.cache.DeliveryOpportunityCache;
import com.delivery.dbcommon.models.DeliveryOpportunity;
import com.delivery.dbcommon.models.DeliveryOpportunitySlot;
import com.delivery.dbcommon.repository.DeliveryOpportunityRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisDeliveryOpportunityCache implements DeliveryOpportunityCache {

	static final String OPPORTUNITY_KEY_PREFIX = "delivery:opportunity:";
	static final String SLOT_KEY_PREFIX = "delivery:slot:";

	private final StringRedisTemplate redis;
	private final DeliveryOpportunityRepository opportunityRepository;
	private final ObjectMapper objectMapper;
	private final Duration opportunityTtl;
	private final Duration slotTtl;

	public RedisDeliveryOpportunityCache(
			StringRedisTemplate redis,
			DeliveryOpportunityRepository opportunityRepository,
			ObjectMapper objectMapper,
			DeliveryOpportunityCacheProperties cacheProperties) {
		this(redis, opportunityRepository, objectMapper, cacheProperties.opportunityTtl(), cacheProperties.slotTtl());
	}

	RedisDeliveryOpportunityCache(
			StringRedisTemplate redis,
			DeliveryOpportunityRepository opportunityRepository,
			ObjectMapper objectMapper,
			Duration opportunityTtl,
			Duration slotTtl) {
		this.redis = redis;
		this.opportunityRepository = opportunityRepository;
		this.objectMapper = objectMapper;
		this.opportunityTtl = opportunityTtl;
		this.slotTtl = slotTtl;
	}

	@Override
	public Optional<DeliveryOpportunity> findOpportunity(UUID opportunityId) {
		String cacheKey = opportunityKey(opportunityId);
		Optional<DeliveryOpportunity> cached = readJson(cacheKey, DeliveryOpportunity.class);
		if (cached.isPresent()) {
			return cached;
		}

		Optional<DeliveryOpportunity> fromDb = opportunityRepository.findById(opportunityId);
		fromDb.ifPresent(opportunity -> redis.opsForValue().set(cacheKey, writeJson(opportunity), opportunityTtl));
		return fromDb;
	}

	@Override
	public void writeSlot(DeliveryOpportunitySlot slot) {
		writeSlotIfNewer(slotKey(slot.opportunityId(), slot.opportunitySlot()), slot);
	}

	@Override
	public Optional<DeliveryOpportunitySlot> findSlot(UUID opportunityId, int opportunitySlot) {
		String cacheKey = slotKey(opportunityId, opportunitySlot);
		Optional<DeliveryOpportunitySlot> cached = readSlotHash(cacheKey, opportunityId, opportunitySlot);
		if (cached.isPresent()) {
			return cached;
		}

		Optional<DeliveryOpportunitySlot> fromDb =
				opportunityRepository.findSlotByOpportunityIdAndSlot(opportunityId, opportunitySlot);
		fromDb.ifPresent(slot -> writeSlotIfNewer(cacheKey, slot));
		return fromDb;
	}

	/**
	 * Atomically writes the slot HASH only when {@code fromDb.version()} is newer than the cached version.
	 */
	void writeSlotIfNewer(String cacheKey, DeliveryOpportunitySlot fromDb) {
		Long written = redis.execute(
				SlotCacheRedisScript.setIfNewer(),
				Collections.singletonList(cacheKey),
				SlotCacheRedisScript.writeArgs(fromDb).toArray());
		if (written != null && written == 1L) {
			redis.expire(cacheKey, slotTtl);
		}
	}

	static String opportunityKey(UUID opportunityId) {
		return OPPORTUNITY_KEY_PREFIX + opportunityId;
	}

	static String slotKey(UUID opportunityId, int opportunitySlot) {
		return SLOT_KEY_PREFIX + opportunityId + "#" + opportunitySlot;
	}

	private Optional<DeliveryOpportunitySlot> readSlotHash(
			String key, UUID opportunityId, int opportunitySlot) {
		Map<String, String> entries = redis.<String, String>opsForHash().entries(key);
		if (entries.isEmpty() || !entries.containsKey(SlotCacheRedisScript.FIELD_VERSION)) {
			return Optional.empty();
		}
		return Optional.of(toSlot(entries, opportunityId, opportunitySlot));
	}

	static DeliveryOpportunitySlot toSlot(
			Map<String, String> entries, UUID opportunityId, int opportunitySlot) {
		UUID id = UUID.fromString(entries.get(SlotCacheRedisScript.FIELD_ID));
		String claimedByRaw = entries.get(SlotCacheRedisScript.FIELD_CLAIMED_BY);
		UUID claimedBy = claimedByRaw == null || claimedByRaw.isEmpty() ? null : UUID.fromString(claimedByRaw);
		String claimedAtRaw = entries.get(SlotCacheRedisScript.FIELD_CLAIMED_AT);
		Instant claimedAt = claimedAtRaw == null || claimedAtRaw.isEmpty() ? null : Instant.parse(claimedAtRaw);
		int version = Integer.parseInt(entries.get(SlotCacheRedisScript.FIELD_VERSION));
		return new DeliveryOpportunitySlot(id, opportunityId, opportunitySlot, claimedBy, claimedAt, version);
	}

	private <T> Optional<T> readJson(String key, Class<T> type) {
		String json = redis.opsForValue().get(key);
		if (json == null) {
			return Optional.empty();
		}
		try {
			return Optional.of(objectMapper.readValue(json, type));
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Failed to deserialize cache entry for key: " + key, ex);
		}
	}

	private String writeJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Failed to serialize cache entry", ex);
		}
	}
}
