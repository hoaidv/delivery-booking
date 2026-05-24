package com.delivery.dbcommon.infra.redis;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "cache.delivery")
public record DeliveryOpportunityCacheProperties(
		@DefaultValue("60s") Duration opportunityTtl, @DefaultValue("15s") Duration slotTtl) {}
