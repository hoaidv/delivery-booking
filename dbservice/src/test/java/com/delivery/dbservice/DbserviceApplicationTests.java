package com.delivery.dbservice;

import com.delivery.dbservice.infra.KafkaClaimProducer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class DbserviceApplicationTests {

	@MockitoBean
	private KafkaClaimProducer claimProducer;

	@Test
	void contextLoads() {
	}
}
