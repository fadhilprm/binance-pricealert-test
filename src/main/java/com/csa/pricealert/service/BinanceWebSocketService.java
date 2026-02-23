package com.csa.pricealert.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

@Service
public class BinanceWebSocketService {

    private static final Logger logger = LoggerFactory.getLogger(BinanceWebSocketService.class);
    private final AlertEvaluatorService alertEvaluatorService;
    private final ObjectMapper objectMapper;
    private BinanceClient client;

    @Value("${binance.websocket.url}")
    private String binanceWsUrl;

    public BinanceWebSocketService(AlertEvaluatorService alertEvaluatorService, ObjectMapper objectMapper) {
        this.alertEvaluatorService = alertEvaluatorService;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void start() {
        connectToBinance();
    }

    @PreDestroy
    public void stop() {
        if (client != null && !client.isClosed()) {
            client.close();
        }
    }

    private void connectToBinance() {
        try {
            logger.info("Connecting to Binance WSS: {}", binanceWsUrl);
            client = new BinanceClient(new URI(binanceWsUrl));
            client.connect();
        } catch (URISyntaxException e) {
            logger.error("Invalid Binance WSS URI", e);
        }
    }

    private class BinanceClient extends WebSocketClient {

        public BinanceClient(URI serverUri) {
            super(serverUri);
        }

        @Override
        public void onOpen(ServerHandshake handshakedata) {
            logger.info("Connected to Binance WebSocket!");
        }

        @Override
        public void onMessage(String message) {
            try {
                // !ticker@arr returns an array of ticker objects
                List<Map<String, Object>> tickers = objectMapper.readValue(message,
                        new TypeReference<List<Map<String, Object>>>() {
                        });

                for (Map<String, Object> ticker : tickers) {
                    if (ticker.containsKey("s") && ticker.containsKey("c")) {
                        String symbol = (String) ticker.get("s");
                        double price = Double.parseDouble((String) ticker.get("c"));

                        // Let AlertEvaluator check if any active alerts meet the condition
                        alertEvaluatorService.evaluatePrice(symbol, price);
                    }
                }
            } catch (Exception e) {
                logger.error("Error parsing Binance message", e);
            }
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            logger.warn("Binance WebSocket closed: {} {}. Reconnecting...", code, reason);
            // Attempt simple auto-reconnect using a new thread after a short delay
            new Thread(() -> {
                try {
                    Thread.sleep(5000);
                    connectToBinance();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        }

        @Override
        public void onError(Exception ex) {
            logger.error("Binance WebSocket Error", ex);
        }
    }
}
