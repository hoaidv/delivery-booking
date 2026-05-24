package com.delivery.dbservice.infra;

import com.delivery.dbcommon.dtos.ClaimMessageKeys;
import com.delivery.dbcommon.dtos.DeliveryOpportunityClaimMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Kafka producer for claim events. Message key format: {@code {op_id}#{op_slot}}.
 */
@Component
@Profile("!test")
public class KafkaClaimProducer implements AutoCloseable {

	public static final String EVENT_TYPE = "driver.claim.opportunity";
	public static final int EVENT_VERSION = 1;

	private final Producer<String, String> producer;
	private final ObjectMapper objectMapper;
	private final String topic;

	public KafkaClaimProducer(
			@Value("${kafka.bootstrap.servers}") String bootstrapServers,
			@Value("${kafka.topic.delivery-opportunity-claims}") String topic,
			ObjectMapper objectMapper) {
		this.topic = topic;
		this.objectMapper = objectMapper;
		this.producer = new KafkaProducer<>(producerProperties(bootstrapServers));
	}

	@Override
	public void close() {
		producer.close();
	}

	public void publish(DeliveryOpportunityClaimMessage message) {
		String key = claimKey(message.payload().opId(), message.payload().opSlot());
		try {
			producer.send(new ProducerRecord<>(topic, key, objectMapper.writeValueAsString(message)));
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Failed to serialize claim message", ex);
		}
	}

	public static String claimKey(UUID opportunityId, int opportunitySlot) {
		return ClaimMessageKeys.slotKey(opportunityId, opportunitySlot);
	}

	public static DeliveryOpportunityClaimMessage toMessage(
			UUID driverId, UUID opportunityId, int opportunitySlot, UUID requestedAt) {
		long requestedAtEpochSeconds = com.datastax.oss.driver.api.core.uuid.Uuids.unixTimestamp(requestedAt)
				/ 1000;
		return new DeliveryOpportunityClaimMessage(
				UUID.randomUUID().toString(),
				EVENT_TYPE,
				EVENT_VERSION,
				new DeliveryOpportunityClaimMessage.Payload(
						driverId, opportunityId, opportunitySlot, requestedAtEpochSeconds));
	}

	private static Properties producerProperties(String bootstrapServers) {
		Properties properties = new Properties();
		properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
		properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
		properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
		properties.put(ProducerConfig.ACKS_CONFIG, "all");
		properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
		return properties;
	}
}
