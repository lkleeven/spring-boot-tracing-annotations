package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DemoApplicationTests {

	@LocalServerPort
	int port;

	@Test
	void shouldNotPropagateTracingHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.set("traceparent", "00-51903f4961cad5d726fd2ea39677c5d4-4b6646499ab88ecf-00");

		HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
		RestTemplate restTemplate = new RestTemplate();
		Boolean tracingHeaderWasPropagated = restTemplate.exchange(
				URI.create("http://localhost:" + port + "/demo"),
				HttpMethod.GET,
				requestEntity,
				Boolean.class).getBody();
        assertEquals(Boolean.TRUE, tracingHeaderWasPropagated, "Tracing header was propagated");
	}

}
