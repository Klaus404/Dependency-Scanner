package com.example.dependencyscanner.model;

import java.util.List;

public record ScanReport(
        String scanId,
        String filename,
        ScanStatus status,
        String error,
        int critical,
        int high,
        List<DependencyReport> dependencies) {
    public static ScanReport queued(String id, String filename) {
        return new ScanReport(id, filename, ScanStatus.QUEUED, null, 0, 0, List.of());
    }

    public ScanReport withStatus(ScanStatus newStatus) {
        return new ScanReport(scanId, filename, newStatus, error, critical, high, dependencies);
    }
}
