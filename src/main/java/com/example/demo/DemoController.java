package com.example.demo;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

@RestController
public class DemoController {

    private static final Logger log = LoggerFactory.getLogger(DemoController.class);
    private final RestTemplate restTemplate;
    private final boolean isTracingEnabled;
    private final ServletWebServerApplicationContext webServerAppCtxt;

    public DemoController(RestTemplateBuilder restTemplateBuilder,
                          Environment environment,
                          ServletWebServerApplicationContext webServerAppCtxt) {
        this.restTemplate = restTemplateBuilder.build();
        isTracingEnabled = environment.getProperty("management.tracing.enabled", Boolean.class);
        this.webServerAppCtxt = webServerAppCtxt;
    }

    @GetMapping("/demo")
    ResponseEntity<Boolean> demo(HttpServletRequest httpRequest) {
        log.info("demo traceparent: {}", httpRequest.getHeader("traceparent"));
        int port = webServerAppCtxt.getWebServer().getPort();
        return restTemplate.getForEntity(URI.create("http://localhost:"+port+ "/checknotraceparent"), Boolean.class);
    }

    @GetMapping("/checknotraceparent")
    ResponseEntity<Boolean> checkNoTraceparent(HttpServletRequest httpRequest) {
        log.info("management.tracing.enabled={}", isTracingEnabled);
        log.info("checktraceparent traceparent: {}", httpRequest.getHeader("traceparent"));
        return ResponseEntity.ok(httpRequest.getHeader("traceparent") == null);
    }
}
