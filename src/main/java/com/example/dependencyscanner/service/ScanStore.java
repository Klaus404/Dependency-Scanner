package com.example.dependencyscanner.service;

import com.example.dependencyscanner.model.ScanReport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ScanStore {
    private final Map<String, Entry> scans = new ConcurrentHashMap<>();
    private final Duration ttl;

    public ScanStore(@Value("${scanner.job-ttl:30m}") Duration ttl) { this.ttl = ttl; }

    public String create(String filename) {
        String id = UUID.randomUUID().toString();
        scans.put(id, new Entry(ScanReport.queued(id, filename), Instant.now()));
        return id;
    }

    public ScanReport get(String id) {
        Entry entry = scans.get(id);
        if (entry == null || entry.created().plus(ttl).isBefore(Instant.now())) {
            scans.remove(id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Scan not found or expired");
        }
        return entry.report();
    }

    public void update(String id, ScanReport report) {
        scans.computeIfPresent(id, (key, current) -> new Entry(report, current.created()));
    }

    private record Entry(ScanReport report, Instant created) {}
}
