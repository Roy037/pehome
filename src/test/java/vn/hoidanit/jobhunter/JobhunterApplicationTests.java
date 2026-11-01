package vn.hoidanit.jobhunter;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// The real secrets live in .env, which CI and fresh clones do not have; the context only needs *a* signing key.
@SpringBootTest(properties = "hoidanit.jwt.base64-secret=dGVzdC1vbmx5LXNlY3JldC1ub3QtZm9yLXByb2R1Y3Rpb24tdGVzdC1vbmx5LXNlY3JldC1ub3QtZm9yLXByb2R1Y3Rpb24hIQ==")
class JobhunterApplicationTests {

	@Test
	void contextLoads() {
	}

}
