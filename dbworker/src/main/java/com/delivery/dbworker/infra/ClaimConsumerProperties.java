package com.delivery.dbworker.infra;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kafka.consumer")
public record ClaimConsumerProperties(
		String groupId,
		int instances,
		int maxPollRecords,
		Duration pollTimeout,
		boolean commitOnSuccessfulClaim) {

	public ClaimConsumerProperties {
		if (pollTimeout == null) {
			pollTimeout = Duration.ofMillis(100);
		}
	}
}
