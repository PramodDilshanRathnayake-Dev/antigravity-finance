package com.antigravity;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.properties")
class TradingEngineApplicationTests {

	@Test
	void contextLoads() {
		// Validates the entire Spring ApplicationContext boots correctly with test
		// profile.
	}
}
