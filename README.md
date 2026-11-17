# Dependency Scanner

Asynchronous Spring Boot API for scanning dependency manifests for Critical and High vulnerabilities from OSV.dev.

RabbitMQ is used for scan jobs. Start a local broker before starting the application:

```bash
./scripts/start.sh
```

This starts the API and RabbitMQ with Docker Compose. RabbitMQ management is available at `http://localhost:15672` using `scanner` / `scanner`.

## Run

```bash
mvn spring-boot:run
```

## API

Submit a supported manifest:

```bash
curl -F file=@pom.xml http://localhost:8080/api/v1/scans
```

Poll the returned scan ID:

```bash
curl http://localhost:8080/api/v1/scans/{scanId}
```

To wait for completion without polling, subscribe with Server-Sent Events:

```bash
curl -N http://localhost:8080/api/v1/scans/{scanId}/events
```

The singular `/event` path is also accepted.

Completion events are also published to the `dependency-scan-results` topic exchange with routing key `scan.{scanId}`. External RabbitMQ consumers can bind a queue to that exchange and routing key.

Supported files currently include `pom.xml`, `build.gradle`, `build.gradle.kts`, `package.json`, and `requirements.txt`.

Jobs are stored in memory for 30 minutes and are lost when the application restarts. Build files are parsed as text and are never executed.
