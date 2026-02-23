package com.csa.pricealert.service;

import com.csa.pricealert.model.Alert;
import com.csa.pricealert.repository.AlertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AlertEvaluatorService {

    private static final Logger logger = LoggerFactory.getLogger(AlertEvaluatorService.class);

    private final ConcurrentHashMap<String, Alert> activeAlerts = new ConcurrentHashMap<>();
    private final SimpMessagingTemplate messagingTemplate;
    private final AlertRepository alertRepository;

    private com.espertech.esper.runtime.client.EPRuntime epRuntime;
    private com.espertech.esper.compiler.client.EPCompiler epCompiler;
    private com.espertech.esper.compiler.client.CompilerArguments compilerArgs;

    public AlertEvaluatorService(SimpMessagingTemplate messagingTemplate, AlertRepository alertRepository) {
        this.messagingTemplate = messagingTemplate;
        this.alertRepository = alertRepository;
    }

    @jakarta.annotation.PostConstruct
    public void setupEsper() {
        com.espertech.esper.common.client.configuration.Configuration config = new com.espertech.esper.common.client.configuration.Configuration();
        config.getCommon().addEventType("PriceUpdate", com.csa.pricealert.model.PriceUpdate.class);
        epRuntime = com.espertech.esper.runtime.client.EPRuntimeProvider.getDefaultRuntime(config);
        epCompiler = com.espertech.esper.compiler.client.EPCompilerProvider.getCompiler();
        compilerArgs = new com.espertech.esper.compiler.client.CompilerArguments(config);

        // Load existing active alerts from SQLite
        List<Alert> pendingAlerts = alertRepository.findByIsTriggeredFalse();
        for (Alert alert : pendingAlerts) {
            deployAlertStatement(alert);
            activeAlerts.put(alert.getId(), alert);
        }
        logger.info("Loaded {} active alerts from database.", pendingAlerts.size());
    }

    public Alert addAlert(String symbol, double targetPrice, String condition) {
        Alert alert = new Alert();
        alert.setId(UUID.randomUUID().toString());
        alert.setSymbol(symbol.toUpperCase());
        alert.setTargetPrice(targetPrice);
        alert.setCondition(condition);
        alert.setCreatedAt(LocalDateTime.now());
        alert.setTriggered(false);

        alert = alertRepository.save(alert);
        activeAlerts.put(alert.getId(), alert);
        deployAlertStatement(alert);

        logger.info("New CEP alert created: {} {} {}", alert.getSymbol(), alert.getCondition(), alert.getTargetPrice());
        return alert;
    }

    private void deployAlertStatement(Alert alert) {
        try {
            // Include @name in EPL so we can retrieve it reliably
            String statementName = "stmt_" + alert.getId().replace("-", "");
            String query = String.format("@name('%s') select * from PriceUpdate(symbol='%s', price %s %s)",
                    statementName, alert.getSymbol(), alert.getCondition(), String.valueOf(alert.getTargetPrice()));

            com.espertech.esper.common.client.EPCompiled compiled = epCompiler.compile(query, compilerArgs);
            com.espertech.esper.runtime.client.EPDeployment deployment = epRuntime.getDeploymentService()
                    .deploy(compiled);
            com.espertech.esper.runtime.client.EPStatement statement = epRuntime.getDeploymentService()
                    .getStatement(deployment.getDeploymentId(), statementName);

            statement.addListener((newData, oldData, stmt, runtime) -> {
                if (!alert.isTriggered()) {
                    alert.setTriggered(true);
                    alertRepository.save(alert); // Persist triggered state
                    logger.info("CEP ALERT TRIGGERED! {} crossed {}", alert.getSymbol(), alert.getTargetPrice());
                    messagingTemplate.convertAndSend("/topic/alerts", alert);
                    try {
                        epRuntime.getDeploymentService().undeploy(deployment.getDeploymentId());
                    } catch (Exception e) {
                        logger.error("Failed to undeploy", e);
                    }
                }
            });

        } catch (com.espertech.esper.compiler.client.EPCompileException
                | com.espertech.esper.runtime.client.EPDeployException e) {
            logger.error("Failed to create EPL for alert", e);
        }
    }

    public List<Alert> getAllAlerts() {
        return new ArrayList<>(activeAlerts.values());
    }

    public void evaluatePrice(String symbol, double currentPrice) {
        com.csa.pricealert.model.PriceUpdate update = new com.csa.pricealert.model.PriceUpdate(symbol, currentPrice);
        epRuntime.getEventService().sendEventBean(update, "PriceUpdate");

        // Also broadcast the price to the frontend if anyone is actively listening for
        // it
        // We only broadcast for symbols that have at least one untriggered alert to
        // save bandwidth
        boolean hasActiveAlertForSymbol = activeAlerts.values().stream()
                .anyMatch(a -> !a.isTriggered() && a.getSymbol().equalsIgnoreCase(symbol));

        if (hasActiveAlertForSymbol) {
            messagingTemplate.convertAndSend("/topic/prices", update);
        }
    }
}
