package com.antigravity.agents.analysis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Collections;
import java.util.Map;

@Service
public class LocalMarketApiClient {

    private static final Logger log = LoggerFactory.getLogger(LocalMarketApiClient.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${antigravity.api.localmarket.url}")
    private String localMarketBaseUrl;

    public LocalMarketApiClient(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    /**
     * Fetches the latest order-book and volume data for a given asset from the
     * configured LocalMarket API endpoint. Falls back to an empty map on failure
     * so the AnalysisAgent cycle is not interrupted.
     */
    public Map<String, Object> fetchLatestMarketData(String assetId) {
        log.info("[LocalMarketApiClient] Fetching market data for assetId={} from {}", assetId, localMarketBaseUrl);

        try {
            String response = webClient.get()
                    .uri(localMarketBaseUrl + "/stock/price/{assetId}", assetId)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (response == null || response.isBlank()) {
                log.warn("[LocalMarketApiClient] Empty response received for assetId={}. Returning empty data.",
                        assetId);
                return Collections.emptyMap();
            }

            Map<String, Object> root = objectMapper.readValue(response, new TypeReference<>() {
            });
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) root.getOrDefault("price_data", Collections.emptyMap());
            log.debug("[LocalMarketApiClient] Price data extracted for assetId={}: {}", assetId, data);
            return data;

        } catch (Exception e) {
            log.error("[LocalMarketApiClient] Failed to fetch market data for assetId={}: {}", assetId, e.getMessage());
            return Collections.emptyMap();
        }
    }
}
