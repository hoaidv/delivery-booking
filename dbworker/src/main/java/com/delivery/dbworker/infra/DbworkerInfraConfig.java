package com.delivery.dbworker.infra;

import com.delivery.dbcommon.cache.DeliveryOpportunityCache;
import com.delivery.dbcommon.infra.postgres.JdbcDeliveryOpportunityRepository;
import com.delivery.dbcommon.infra.redis.DeliveryOpportunityCacheProperties;
import com.delivery.dbcommon.infra.redis.DirectDeliveryOpportunityCache;
import com.delivery.dbcommon.infra.redis.RedisDeliveryOpportunityCache;
import com.delivery.dbcommon.repository.DeliveryOpportunityRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@EnableConfigurationProperties({DeliveryOpportunityCacheProperties.class, ClaimConsumerProperties.class})
public class DbworkerInfraConfig {

	@Bean
	DeliveryOpportunityRepository deliveryOpportunityRepository(JdbcTemplate jdbcTemplate) {
		return new JdbcDeliveryOpportunityRepository(jdbcTemplate);
	}

	@Bean
	ObjectMapper objectMapper() {
		return new ObjectMapper()
				.registerModule(new JavaTimeModule())
				.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
	}

	@Bean
	@Profile("!test")
	DeliveryOpportunityCache deliveryOpportunityCache(
			StringRedisTemplate redis,
			DeliveryOpportunityRepository deliveryOpportunityRepository,
			ObjectMapper objectMapper,
			DeliveryOpportunityCacheProperties cacheProperties) {
		return new RedisDeliveryOpportunityCache(redis, deliveryOpportunityRepository, objectMapper, cacheProperties);
	}

	@Bean
	@Profile("test")
	DeliveryOpportunityCache directDeliveryOpportunityCache(
			DeliveryOpportunityRepository deliveryOpportunityRepository) {
		return new DirectDeliveryOpportunityCache(deliveryOpportunityRepository);
	}
}
