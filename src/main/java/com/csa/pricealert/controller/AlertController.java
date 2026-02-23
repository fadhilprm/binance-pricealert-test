package com.csa.pricealert.controller;

import com.csa.pricealert.model.Alert;
import com.csa.pricealert.service.AlertEvaluatorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertEvaluatorService alertEvaluatorService;

    public AlertController(AlertEvaluatorService alertEvaluatorService) {
        this.alertEvaluatorService = alertEvaluatorService;
    }

    @PostMapping
    public ResponseEntity<Alert> createAlert(@RequestBody Map<String, Object> payload) {
        if (!payload.containsKey("symbol") || !payload.containsKey("targetPrice")
                || !payload.containsKey("condition")) {
            return ResponseEntity.badRequest().build();
        }

        String symbol = payload.get("symbol").toString();
        double targetPrice = Double.parseDouble(payload.get("targetPrice").toString());
        String condition = payload.get("condition").toString();

        Alert alert = alertEvaluatorService.addAlert(symbol, targetPrice, condition);
        return ResponseEntity.ok(alert);
    }

    @GetMapping
    public ResponseEntity<List<Alert>> getAlerts() {
        return ResponseEntity.ok(alertEvaluatorService.getAllAlerts());
    }
}
