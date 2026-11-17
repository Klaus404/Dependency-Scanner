package com.example.dependencyscanner.controller;

import com.example.dependencyscanner.model.ScanReport;
import com.example.dependencyscanner.service.ScanService;
import com.example.dependencyscanner.service.ScanStore;
import com.example.dependencyscanner.service.ScanEventBroker;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/scans")
public class ScanController {
    private final ScanService service;
    private final ScanStore store;
    private final ScanEventBroker events;

    public ScanController(ScanService service, ScanStore store, ScanEventBroker events) {
        this.service = service;
        this.store = store;
        this.events = events;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> create(@RequestPart("file") MultipartFile file) {
        if (file.isEmpty() || file.getOriginalFilename() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A non-empty named file is required");
        try {
            return Map.of("scanId", service.submit(file.getOriginalFilename(), file.getBytes()), "status", "QUEUED");
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    @GetMapping("/{id}")
    public ScanReport get(@PathVariable String id) { return store.get(id); }

    @GetMapping(value = {"/{id}/events", "/{id}/event"}, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable String id) {
        return events.subscribe(id, store.get(id));
    }
}
