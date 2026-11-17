package com.example.dependencyscanner.model;

public record ScanJob(String scanId, String filename, byte[] content) {
}
