package com.delivery.dbservice.infra;

import com.delivery.dbcommon.cache.DeliveryOpportunityCache;
import com.delivery.dbcommon.infra.postgres.JdbcDeliveryOpportunityRepository;
import com.delivery.dbcommon.infra.redis.DeliveryOpportunityCacheProperties;
import com.delivery.dbcommon.infra.redis.DirectDeliveryOpportunityCache;
import com.delivery.dbcommon.infra.redis.RedisDeliveryOpportunityCache;
import com.delivery.dbcommon.infra.scylla.ScyllaClaimRepository;
import com.delivery.dbcommon.repository.DeliveryOpportunityRepository;
import com.delivery.dbcommon.repository.DeliveryOpportunityClaimRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@EnableConfigurationProperties(DeliveryOpportunityCacheProperties.class)
public class DbcommonRepositoryConfig {

	@Bean
	DeliveryOpportunityRepository deliveryOpportunityRepository(
			org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
		return new JdbcDeliveryOpportunityRepository(jdbcTemplate);
	}

	@Bean
	DeliveryOpportunityClaimRepository deliveryOpportunityClaimRepository(
					com.datastax.oss.driver.api.core.CqlSession session) {
			return new ScyllaClaimRepository(session);
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
