package com.example.dependencyscanner.service;

import com.example.dependencyscanner.model.ScanReport;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class ScanEventBroker {
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String scanId, ScanReport current) {
        SseEmitter emitter = new SseEmitter(0L);
        if (current.status().name().equals("COMPLETED") || current.status().name().equals("FAILED")) {
            sendAndComplete(emitter, current);
            return emitter;
        }
        subscribers.computeIfAbsent(scanId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(scanId, emitter));
        emitter.onTimeout(() -> remove(scanId, emitter));
        return emitter;
    }

    public void publish(String scanId, ScanReport report) {
        List<SseEmitter> emitters = subscribers.remove(scanId);
        if (emitters == null) return;
        emitters.forEach(emitter -> sendAndComplete(emitter, report));
    }

    private void sendAndComplete(SseEmitter emitter, ScanReport report) {
        try {
            emitter.send(SseEmitter.event().name("scan-complete").data(report));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    private void remove(String scanId, SseEmitter emitter) {
        var entries = subscribers.get(scanId);
        if (entries != null) entries.remove(emitter);
    }
}
