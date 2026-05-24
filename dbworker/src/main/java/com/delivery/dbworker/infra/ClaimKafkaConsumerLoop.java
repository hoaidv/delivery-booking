package com.delivery.dbworker.infra;

import com.delivery.dbcommon.dtos.DeliveryOpportunityClaimMessage;
import com.delivery.dbworker.appcore.ClaimProcessingResult;
import com.delivery.dbworker.appcore.ClaimProcessingService;
import com.delivery.dbworker.appcore.LocalSlotCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.CooperativeStickyAssignor;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClaimKafkaConsumerLoop implements Runnable, AutoCloseable {

	private static final Logger log = LoggerFactory.getLogger(ClaimKafkaConsumerLoop.class);

	private final String bootstrapServers;
	private final String topic;
	private final String clientId;
	private final ClaimConsumerProperties consumerProperties;
	private final ClaimProcessingService claimProcessingService;
	private final LocalSlotCache slotCache;
	private final ObjectMapper objectMapper;
	private final AtomicBoolean running = new AtomicBoolean(true);

	private KafkaConsumer<String, String> consumer;
	private final Map<TopicPartition, OffsetAndMetadata> pendingOffsets = new HashMap<>();
	private int recordsSinceLastCommit;

	public ClaimKafkaConsumerLoop(
			String bootstrapServers,
			String topic,
			String clientId,
			ClaimConsumerProperties consumerProperties,
			ClaimProcessingService claimProcessingService,
			LocalSlotCache slotCache,
			ObjectMapper objectMapper) {
		this.bootstrapServers = bootstrapServers;
		this.topic = topic;
		this.clientId = clientId;
		this.consumerProperties = consumerProperties;
		this.claimProcessingService = claimProcessingService;
		this.slotCache = slotCache;
		this.objectMapper = objectMapper;
	}

	@Override
	public void run() {
		consumer = new KafkaConsumer<>(consumerConfig());
		consumer.subscribe(java.util.List.of(topic), rebalanceListener());
		log.info("Claim consumer {} subscribed to {}", clientId, topic);

		try {
			while (running.get()) {
				ConsumerRecords<String, String> records = consumer.poll(consumerProperties.pollTimeout());
				for (ConsumerRecord<String, String> record : records) {
					processRecord(record);
				}
				commitIfNeeded(false);
			}
		} finally {
			commitIfNeeded(false);
			consumer.close();
			log.info("Claim consumer {} stopped", clientId);
		}
	}

	@Override
	public void close() {
		running.set(false);
	}

	private ConsumerRebalanceListener rebalanceListener() {
		return new ConsumerRebalanceListener() {
			@Override
			public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
				commitIfNeeded(true);
				slotCache.clear();
				log.info("Consumer {} revoked {} partition(s); local slot cache cleared", clientId, partitions.size());
			}

			@Override
			public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
				log.info("Consumer {} assigned {} partition(s)", clientId, partitions.size());
			}
		};
	}

	private void processRecord(ConsumerRecord<String, String> record) {
		TopicPartition partition = new TopicPartition(record.topic(), record.partition());
		try {
			DeliveryOpportunityClaimMessage message =
					objectMapper.readValue(record.value(), DeliveryOpportunityClaimMessage.class);
			ClaimProcessingResult result = claimProcessingService.process(message);
			trackOffset(partition, record.offset());
			recordsSinceLastCommit++;
			if (shouldCommit(result)) {
				commitIfNeeded(true);
			}
		} catch (Exception ex) {
			log.error(
					"Failed to process claim at {}-{} offset {}; skipping",
					record.topic(),
					record.partition(),
					record.offset(),
					ex);
			trackOffset(partition, record.offset());
			recordsSinceLastCommit++;
			if (recordsSinceLastCommit >= consumerProperties.maxPollRecords()) {
				commitIfNeeded(true);
			}
		}
	}

	private boolean shouldCommit(ClaimProcessingResult result) {
		if (consumerProperties.commitOnSuccessfulClaim() && result == ClaimProcessingResult.CLAIMED) {
			return true;
		}
		return recordsSinceLastCommit >= consumerProperties.maxPollRecords();
	}

	private void trackOffset(TopicPartition partition, long offset) {
		pendingOffsets.put(partition, new OffsetAndMetadata(offset + 1));
	}

	private void commitIfNeeded(boolean force) {
		if (pendingOffsets.isEmpty()) {
			return;
		}
		if (!force && recordsSinceLastCommit == 0) {
			return;
		}
		consumer.commitSync(Map.copyOf(pendingOffsets));
		pendingOffsets.clear();
		recordsSinceLastCommit = 0;
	}

	private Properties consumerConfig() {
		Properties properties = new Properties();
		properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
		properties.put(ConsumerConfig.GROUP_ID_CONFIG, consumerProperties.groupId());
		properties.put(ConsumerConfig.CLIENT_ID_CONFIG, clientId);
		properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
		properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
		properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
		properties.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, Integer.toString(consumerProperties.maxPollRecords()));
		properties.put(
				ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG, CooperativeStickyAssignor.class.getName());
		return properties;
	}
}
