package com.danzzan.infra.octomo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Component
@RequiredArgsConstructor
public class OctomoExistsApiClient implements OctomoMessageClient {

    private final OctomoProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public boolean existsRecentMessage(String mobileNum, String text) {
        WebClient webClient = WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create()))
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Octomo " + properties.getApiKey())
                .build();

        String rawResponse = webClient.post()
                .uri(properties.getMessageExistsPath())
                .bodyValue(new ExistsRequest(mobileNum, text))
                .retrieve()
                .bodyToMono(String.class)
                .block();

        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            boolean exists = root.path("exists").asBoolean(false);
            boolean verified = root.path("verified").asBoolean(false);
            return exists || verified;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse OCTOMO response", e);
        }
    }

    private record ExistsRequest(String mobileNum, String text) {
    }
}
