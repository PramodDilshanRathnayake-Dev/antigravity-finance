package com.antigravity.agents.trade;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.Map;

@Service
public class SandboxBrokerClient {

    private static final Logger log = LoggerFactory.getLogger(SandboxBrokerClient.class);
    private final WebClient webClient;

    @Value("${antigravity.api.sandbox.broker.url}")
    private String sandboxBrokerUrl;

    public SandboxBrokerClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public record OrderRequest(
            String symbol,
            @JsonProperty("order_type") String orderType,
            int quantity,
            BigDecimal price,
            String condition) {
    }

    public record OrderResponse(
            boolean success,
            Map<String, Object> order,
            String message) {
    }

    public OrderResponse placeOrder(String symbol, String action, int quantity, BigDecimal price) {
        log.info("[SandboxBrokerClient] Placing {} order for {} units of {} at {} in sandbox",
                action, quantity, symbol, price);

        OrderRequest request = new OrderRequest(
                symbol,
                action.toLowerCase(), // buy or sell
                quantity,
                price,
                "market" // Default to market for now as per v1 logic
        );

        try {
            return webClient.post()
                    .uri(sandboxBrokerUrl + "/orders")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(OrderResponse.class)
                    .block();
        } catch (Exception e) {
            log.error("[SandboxBrokerClient] Failed to place order in sandbox: {}", e.getMessage());
            return new OrderResponse(false, null, e.getMessage());
        }
    }

    public BigDecimal getAccountBalance(String userId) {
        // userId is ignored for now as sandbox uses account_id from context/queries
        // But we want to sync the CDS balance with our system.
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.get()
                    .uri(sandboxBrokerUrl + "/account/balance")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null && response.containsKey("balance")) {
                return new BigDecimal(response.get("balance").toString());
            }
        } catch (Exception e) {
            log.error("[SandboxBrokerClient] Failed to fetch account balance: {}", e.getMessage());
        }
        return BigDecimal.ZERO;
    }
}
