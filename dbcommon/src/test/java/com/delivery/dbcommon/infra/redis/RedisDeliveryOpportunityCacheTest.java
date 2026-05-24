package com.delivery.dbcommon.infra.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.delivery.dbcommon.models.DeliveryOpportunitySlot;
import com.delivery.dbcommon.repository.DeliveryOpportunityRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisDeliveryOpportunityCacheTest {

	@Mock
	private StringRedisTemplate redis;

	@Mock
	private ValueOperations<String, String> valueOperations;

	@Mock
	private HashOperations<String, String, String> hashOperations;

	@Mock
	private DeliveryOpportunityRepository opportunityRepository;

	private RedisDeliveryOpportunityCache cache;

	@BeforeEach
	void setUp() {
		when(redis.opsForValue()).thenReturn(valueOperations);
		when(redis.<String, String>opsForHash()).thenReturn(hashOperations);
		ObjectMapper objectMapper = new ObjectMapper()
				.registerModule(new JavaTimeModule())
				.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		cache = new RedisDeliveryOpportunityCache(
				redis,
				opportunityRepository,
				objectMapper,
				Duration.ofSeconds(60),
				Duration.ofSeconds(15));
	}

	@Test
	void writeSlotIfNewer_invokesLuaScriptWithDatabaseFields() {
		UUID opportunityId = UUID.randomUUID();
		int slotNumber = 3;
		String key = RedisDeliveryOpportunityCache.slotKey(opportunityId, slotNumber);
		UUID slotId = UUID.randomUUID();
		UUID claimedBy = UUID.randomUUID();
		Instant claimedAt = Instant.parse("2026-05-24T10:00:00Z");
		DeliveryOpportunitySlot dbSlot =
				new DeliveryOpportunitySlot(slotId, opportunityId, slotNumber, claimedBy, claimedAt, 4);

		when(redis.execute(any(RedisScript.class), any(List.class), any(Object[].class))).thenReturn(1L);

		cache.writeSlotIfNewer(key, dbSlot);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
		verify(redis).execute(any(RedisScript.class), any(List.class), argsCaptor.capture());
		Object[] args = argsCaptor.getValue();
		assertEquals("4", args[0]);
		assertEquals(claimedBy.toString(), args[1]);
		assertEquals(claimedAt.toString(), args[2]);
		assertEquals(slotId.toString(), args[3]);
		verify(redis).expire(key, Duration.ofSeconds(15));
	}

	@Test
	void writeSlotIfNewer_skipsExpireWhenScriptDoesNotWrite() {
		UUID opportunityId = UUID.randomUUID();
		String key = RedisDeliveryOpportunityCache.slotKey(opportunityId, 1);
		DeliveryOpportunitySlot dbSlot = new DeliveryOpportunitySlot(
				UUID.randomUUID(), opportunityId, 1, null, null, 2);

		when(redis.execute(any(RedisScript.class), any(List.class), any(Object[].class))).thenReturn(0L);

		cache.writeSlotIfNewer(key, dbSlot);

		verify(redis, never()).expire(eq(key), any(Duration.class));
	}

	@Test
	void findSlot_returnsCachedHashWithoutHittingDatabase() {
		UUID opportunityId = UUID.randomUUID();
		int slotNumber = 2;
		UUID slotId = UUID.randomUUID();
		UUID claimedBy = UUID.randomUUID();
		Instant claimedAt = Instant.parse("2026-05-24T12:00:00Z");
		String key = RedisDeliveryOpportunityCache.slotKey(opportunityId, slotNumber);

		when(hashOperations.entries(key))
				.thenReturn(Map.of(
						SlotCacheRedisScript.FIELD_ID, slotId.toString(),
						SlotCacheRedisScript.FIELD_VERSION, "3",
						SlotCacheRedisScript.FIELD_CLAIMED_BY, claimedBy.toString(),
						SlotCacheRedisScript.FIELD_CLAIMED_AT, claimedAt.toString()));

		Optional<DeliveryOpportunitySlot> result = cache.findSlot(opportunityId, slotNumber);

		assertTrue(result.isPresent());
		DeliveryOpportunitySlot slot = result.get();
		assertEquals(slotId, slot.id());
		assertEquals(opportunityId, slot.opportunityId());
		assertEquals(slotNumber, slot.opportunitySlot());
		assertEquals(claimedBy, slot.claimedBy());
		assertEquals(claimedAt, slot.claimedAt());
		assertEquals(3, slot.version());
	}

	@Test
	void toSlot_treatsEmptyClaimedFieldsAsNull() {
		UUID opportunityId = UUID.randomUUID();
		int slotNumber = 1;
		UUID slotId = UUID.randomUUID();

		DeliveryOpportunitySlot slot = RedisDeliveryOpportunityCache.toSlot(
				Map.of(
						SlotCacheRedisScript.FIELD_ID, slotId.toString(),
						SlotCacheRedisScript.FIELD_VERSION, "1",
						SlotCacheRedisScript.FIELD_CLAIMED_BY, "",
						SlotCacheRedisScript.FIELD_CLAIMED_AT, ""),
				opportunityId,
				slotNumber);

		assertEquals(slotId, slot.id());
		assertEquals(null, slot.claimedBy());
		assertEquals(null, slot.claimedAt());
		assertEquals(1, slot.version());
	}
}
