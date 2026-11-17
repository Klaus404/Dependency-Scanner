package com.example.dependencyscanner.service;

import com.example.dependencyscanner.model.Dependency;
import com.example.dependencyscanner.model.DependencyReport;
import com.example.dependencyscanner.model.ScanReport;
import com.example.dependencyscanner.model.ScanJob;
import com.example.dependencyscanner.model.ScanResultEvent;
import com.example.dependencyscanner.model.ScanStatus;
import com.example.dependencyscanner.parser.DependencyParser;
import com.example.dependencyscanner.parser.ManifestParsers;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.io.IOException;
import java.util.List;

@Service
public class ScanService {
    private final ScanStore store;
    private final ManifestParsers manifests;
    private final OsvVulnerabilityProvider osv;
    private final MavenVersionResolver versionResolver;
    private final MavenDependencyGraphResolver graphResolver;
    private final RabbitTemplate rabbitTemplate;
    private final ScanEventBroker events;
    private final String scanQueue;
    private final String resultExchange;
    private final int maxDependencies;

    public ScanService(ScanStore store, ManifestParsers manifests, OsvVulnerabilityProvider osv,
                       MavenVersionResolver versionResolver, MavenDependencyGraphResolver graphResolver,
                       RabbitTemplate rabbitTemplate, ScanEventBroker events,
                       @Value("${scanner.scan-queue}") String scanQueue,
                       @Value("${scanner.result-exchange}") String resultExchange,
                       @Value("${scanner.max-dependencies:500}") int maxDependencies) {
        this.store = store;
        this.manifests = manifests;
        this.osv = osv;
        this.versionResolver = versionResolver;
        this.graphResolver = graphResolver;
        this.rabbitTemplate = rabbitTemplate;
        this.events = events;
        this.scanQueue = scanQueue;
        this.resultExchange = resultExchange;
        this.maxDependencies = maxDependencies;
    }

    public String submit(String filename, byte[] content) throws IOException {
        DependencyParser parser = manifests.parsers().stream().filter(p -> p.supports(filename)).findFirst()
                .orElseThrow(() -> new IOException("Unsupported dependency file: " + filename));
        String id = store.create(filename);
        rabbitTemplate.convertAndSend(scanQueue, new ScanJob(id, filename, content));
        return id;
    }

    @RabbitListener(queues = "${scanner.scan-queue}")
    public void consume(ScanJob job) {
        DependencyParser parser;
        try {
            parser = manifests.parsers().stream().filter(p -> p.supports(job.filename())).findFirst()
                    .orElseThrow(() -> new IOException("Unsupported dependency file: " + job.filename()));
        } catch (IOException e) {
            fail(job.scanId(), e);
            return;
        }
        scan(job.scanId(), parser, job.content());
    }

    private void scan(String id, DependencyParser parser, byte[] content) {
        store.update(id, store.get(id).withStatus(ScanStatus.RUNNING));
        try {
            List<Dependency> dependencies = parser.parse(content);
            dependencies = versionResolver.resolve(dependencies, content);
            dependencies = graphResolver.resolve(dependencies);
            if (dependencies.size() > maxDependencies)
                throw new IOException("Manifest contains more than " + maxDependencies + " dependencies");
            List<DependencyReport> reports = dependencies.stream()
                    .map(dependency -> new DependencyReport(dependency, osv.find(dependency))).toList();
            int critical = (int) reports.stream().flatMap(r -> r.vulnerabilities().stream()).filter(v -> v.severity().name().equals("CRITICAL")).count();
            int high = (int) reports.stream().flatMap(r -> r.vulnerabilities().stream()).filter(v -> v.severity().name().equals("HIGH")).count();
            ScanReport previous = store.get(id);
            ScanReport report = new ScanReport(id, previous.filename(), ScanStatus.COMPLETED, null, critical, high, reports);
            store.update(id, report);
            publish(report);
        } catch (Exception e) {
            fail(id, e);
        }
    }

    private void fail(String id, Exception error) {
        ScanReport previous = store.get(id);
        ScanReport report = new ScanReport(id, previous.filename(), ScanStatus.FAILED,
                error.getMessage(), 0, 0, List.of());
        store.update(id, report);
        publish(report);
    }

    private void publish(ScanReport report) {
        events.publish(report.scanId(), report);
        rabbitTemplate.convertAndSend(resultExchange, "scan." + report.scanId(),
                new ScanResultEvent(report.scanId(), report));
    }
}
