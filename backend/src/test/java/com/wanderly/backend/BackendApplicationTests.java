package com.wanderly.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// The real secret lives in secrets.properties, which CI and fresh checkouts do not have.
// A dev database may already have db/seed applied (dev profile), which this profile does not load; ignore it.
@SpringBootTest(properties = {
		"jwt.secret=test-secret-key-that-is-long-enough-for-hs256-0123456789",
		"spring.flyway.ignore-migration-patterns=*:missing"
})
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
