package com.antigravity.agents.simulation;

import com.antigravity.agents.analysis.AnalysisAgent;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/simulation")
@CrossOrigin(origins = "*")
public class SimulationController {

    private final AnalysisAgent analysisAgent;

    public SimulationController(AnalysisAgent analysisAgent) {
        this.analysisAgent = analysisAgent;
    }

    @PostMapping("/inject")
    public ResponseEntity<Map<String, String>> injectMarketData(@RequestBody String rawPayload) {
        analysisAgent.processMarketData(rawPayload);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Market data block injected into Analysis Agent."));
    }
}
