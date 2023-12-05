package com.example.demo;

import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.tracing.annotation.*;
import io.micrometer.tracing.otel.bridge.ArrayListSpanProcessor;
import io.micrometer.tracing.otel.bridge.OtelFinishedSpan;
import io.micrometer.tracing.test.simple.SpansAssert;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = AopTracingTest.TestConfiguration.class,
        properties = {"spring.main.web-application-type=SERVLET", "management.tracing.sampling.probability=1.0", "debug=true"}
)
@AutoConfigureObservability(metrics = false) // Tracing is disabled by default within a Spring Boot test, and this enables it
class AopTracingTest {

    @LocalServerPort
    private int port;

    @Autowired
    private RestTemplateBuilder restTemplateBuilder;

    @Autowired
    ArrayListSpanProcessor arrayListSpanProcessor;

    @Test
    void tracing_annotations_should_work() {
        // Trigger the endpoint end our bean with tracing annotations
        ResponseEntity<String> response = restTemplateBuilder.build()
                .exchange("http://localhost:{port}/test", HttpMethod.GET, null, String.class, port);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Wait for expected parent span to be exported, the other spans should be child spans and also there once this one is
        await().alias("Wait for spans to be exported")
                .until(() -> !arrayListSpanProcessor.spans().isEmpty());

        SpansAssert.assertThat(
                        arrayListSpanProcessor.spans()
                                .stream()
                                .map(OtelFinishedSpan::fromOtel)
                                .toList()
                ).haveSameTraceId()
                .hasNumberOfSpansEqualTo(4)
                .hasASpanWithName("http get /test")//The client call
                .thenASpanWithNameEqualTo("http get /test")//The call being handled by the controller
                .hasTag("annotated.class", "TestBean")
                .hasTag(
                        "annotated.method",
                        "testMethod4"
                )//This is the last one and overwrites the value from testMethod3
                .hasTag("testTag3", "testParam3") //These tags are missing!
                .hasTag("testTag4", "testParam4") //These tags are missing!
                .hasEventWithNameEqualTo("customEvent.before")//Added by testMethod4
                .hasEventWithNameEqualTo("customEvent.after")//Added by testMethod4
                .backToSpans()
                //Span created by testMethod1
                .thenASpanWithNameEqualTo("test-method1")
                .hasTag("annotated.class", "TestBean")
                .hasTag("annotated.method", "testMethod1")
                .backToSpans()
                //Span create by testMethod2
                .thenASpanWithNameEqualTo("custom-name-on-test-method2")
                .hasTag("annotated.class", "TestBean")
                .hasTag("annotated.method", "testMethod2");
    }

    @Configuration
    @EnableAutoConfiguration
    @Import({TestBean.class, TestRestController.class})
    static class TestConfiguration {
        @Bean
        public TestObservationRegistry testObservationRegistry() {
            return TestObservationRegistry.create();
        }

        @Bean
        public ArrayListSpanProcessor testArrayListSpanProcessor() {
            return new ArrayListSpanProcessor();
        }
    }

    @RestController
    static class TestRestController {
        private final TestInterface testInterface;

        TestRestController(TestInterface testInterface) {
            this.testInterface = testInterface;
        }

        @GetMapping("/test")
        public void test() {
            testInterface.testMethod1();
            testInterface.testMethod2();
            testInterface.testMethod3("testParam3");
            testInterface.testMethod4("testParam4");
        }
    }

    interface TestInterface {
        void testMethod1();

        void testMethod2();

        void testMethod3(String testParam3);

        void testMethod4(String testParam4);
    }

    static class TestBean implements TestInterface {
        @NewSpan
        @Override
        public void testMethod1() {
        }

        @NewSpan("customNameOnTestMethod2")
        @Override
        public void testMethod2() {
        }

        @ContinueSpan
        @Override
        public void testMethod3(@SpanTag("testTag3") String testParam3) {
        }

        //@ContinueSpan(log= "customEvent")//Adds an event
        @ContinueSpan(log = "customEvent")
        @Override
        public void testMethod4(@SpanTag("testTag4") String testParam4) {
        }
    }
}
