package io.ecore2mermaid;

import java.util.List;

public record MermaidResult(String diagram, List<Diagnostic> diagnostics) {}
