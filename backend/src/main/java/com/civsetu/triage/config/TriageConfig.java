package com.civsetu.triage.config;

import java.net.http.HttpClient;
import java.time.Duration;

import tools.jackson.databind.json.JsonMapper;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.civsetu.triage.service.GeminiTriageAdvisor;
import com.civsetu.triage.service.TriageAdvisor;

/**
 * Creates the Gemini advisor only when CIVSETU_GEMINI_API_KEY is set. Without it no
 * {@link TriageAdvisor} exists and the rule engine decides alone.
 */
@Configuration(proxyBeanMethods = false)
class TriageConfig {

    @Bean
    @ConditionalOnExpression("'${civsetu.triage.gemini.api-key:}' != ''")
    TriageAdvisor geminiTriageAdvisor(TriageProperties properties, JsonMapper json) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(http);
        requests.setReadTimeout(properties.timeout());
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.gemini().baseUrl())
                .defaultHeader("x-goog-api-key", properties.gemini().apiKey())
                .requestFactory(requests);
        return new GeminiTriageAdvisor(builder, json, properties.gemini().model());
    }
}
