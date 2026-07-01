package com.vdt.scheduler_service;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

@SpringBootTest
@AutoConfigureMockMvc
class SchedulerServiceApplicationTests {

	@Autowired
	MockMvc mockMvc;
	@Autowired
	RestTemplate restTemplate;
	MockRestServiceServer mockServer;

	@BeforeEach
	void setup() {
		mockServer = MockRestServiceServer.createServer(restTemplate);
	}

	@Test
	void trigger_forwardsToNotificationService() throws Exception {
		mockServer.expect(requestTo("http://localhost:8083/internal/trigger"))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withSuccess("{\"status\":\"ok\",\"enqueued\":3}",
						MediaType.APPLICATION_JSON));

		mockMvc.perform(post("/internal/trigger"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("forwarded"))
				.andExpect(jsonPath("$.notification.enqueued").value(3));

		mockServer.verify();
	}

}
