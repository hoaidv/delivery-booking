package com.delivery.dbworker.infra;

import com.delivery.dbcommon.cache.DeliveryOpportunityCache;
import com.delivery.dbcommon.repository.DeliveryOpportunityRepository;
import com.delivery.dbworker.appcore.ClaimProcessingService;
import com.delivery.dbworker.appcore.LocalSlotCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class ClaimConsumerManager {

	private static final Logger log = LoggerFactory.getLogger(ClaimConsumerManager.class);

	private final String bootstrapServers;
	private final String topic;
	private final ClaimConsumerProperties consumerProperties;
	private final DeliveryOpportunityRepository opportunityRepository;
	private final DeliveryOpportunityCache sharedCache;
	private final ObjectMapper objectMapper;

	private ExecutorService executor;
	private final List<ClaimKafkaConsumerLoop> loops = new ArrayList<>();

	public ClaimConsumerManager(
			@Value("${kafka.bootstrap.servers}") String bootstrapServers,
			@Value("${kafka.topic.delivery-opportunity-claims}") String topic,
			ClaimConsumerProperties consumerProperties,
			DeliveryOpportunityRepository opportunityRepository,
			DeliveryOpportunityCache sharedCache,
			ObjectMapper objectMapper) {
		this.bootstrapServers = bootstrapServers;
		this.topic = topic;
		this.consumerProperties = consumerProperties;
		this.opportunityRepository = opportunityRepository;
		this.sharedCache = sharedCache;
		this.objectMapper = objectMapper;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void startConsumers() {
		int instances = Math.max(1, consumerProperties.instances());
		executor = Executors.newFixedThreadPool(instances, runnable -> {
			Thread thread = new Thread(runnable);
			thread.setName("claim-consumer-" + thread.threadId());
			thread.setDaemon(false);
			return thread;
		});

		for (int index = 0; index < instances; index++) {
			String clientId = consumerProperties.groupId() + "-" + index;
			LocalSlotCache slotCache = new LocalSlotCache(opportunityRepository);
			ClaimProcessingService processingService =
					new ClaimProcessingService(slotCache, opportunityRepository, sharedCache);
			ClaimKafkaConsumerLoop loop = new ClaimKafkaConsumerLoop(
					bootstrapServers,
					topic,
					clientId,
					consumerProperties,
					processingService,
					slotCache,
					objectMapper);
			loops.add(loop);
			executor.submit(loop);
		}
		log.info("Started {} claim Kafka consumer(s) for topic {}", instances, topic);
	}

	@PreDestroy
	public void stopConsumers() {
		for (ClaimKafkaConsumerLoop loop : loops) {
			loop.close();
		}
		if (executor != null) {
			executor.shutdown();
			try {
				if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
					executor.shutdownNow();
				}
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				executor.shutdownNow();
			}
		}
		log.info("Stopped claim Kafka consumers");
	}
}
