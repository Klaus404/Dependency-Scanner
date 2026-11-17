package com.example.dependencyscanner.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScanStoreTest {
    @Test
    void createsAndUpdatesScanReports() {
        ScanStore store = new ScanStore(Duration.ofMinutes(1));
        String id = store.create("pom.xml");

        assertThat(store.get(id).scanId()).isEqualTo(id);
        assertThat(store.get(id).filename()).isEqualTo("pom.xml");
    }

    @Test
    void expiresScans() {
        ScanStore store = new ScanStore(Duration.ZERO);
        String id = store.create("pom.xml");

        assertThatThrownBy(() -> store.get(id)).hasMessageContaining("Scan not found or expired");
    }
}
