package com.wanderly.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// The real secret lives in secrets.properties, which CI and fresh checkouts do not have.
@SpringBootTest(properties = "jwt.secret=test-secret-key-that-is-long-enough-for-hs256-0123456789")
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
