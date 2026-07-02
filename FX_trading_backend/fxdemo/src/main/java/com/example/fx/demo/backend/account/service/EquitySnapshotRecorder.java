package com.example.fx.demo.backend.account.service;

import com.example.fx.demo.backend.account.config.EquityHistoryProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EquitySnapshotRecorder {

    private final EquityHistoryProperties properties;
    private final EquitySnapshotService equitySnapshotService;

    public EquitySnapshotRecorder(
            EquityHistoryProperties properties,
            EquitySnapshotService equitySnapshotService
    ) {
        this.properties = properties;
        this.equitySnapshotService = equitySnapshotService;
    }

    @Scheduled(fixedRateString = "#{@equityHistoryProperties.intervalMillis()}")
    public void recordDefaultAccountSnapshot() {
        if (!properties.isEnabled()) {
            return;
        }
        equitySnapshotService.recordDefaultAccountSnapshot();
    }
}
