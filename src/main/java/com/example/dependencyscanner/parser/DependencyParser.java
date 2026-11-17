package com.example.dependencyscanner.parser;

import com.example.dependencyscanner.model.Dependency;

import java.io.IOException;
import java.util.List;

public interface DependencyParser {
    boolean supports(String filename);
    List<Dependency> parse(byte[] content) throws IOException;
}
