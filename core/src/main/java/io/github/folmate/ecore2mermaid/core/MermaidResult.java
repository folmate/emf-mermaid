package io.github.folmate.ecore2mermaid.core;

import java.util.List;
import java.util.Objects;

public final class MermaidResult {
    private final String diagram;
    private final List<Diagnostic> diagnostics;

    public MermaidResult(String diagram, List<Diagnostic> diagnostics) {
        this.diagram = diagram;
        this.diagnostics = diagnostics;
    }

    public String diagram() { return diagram; }
    public List<Diagnostic> diagnostics() { return diagnostics; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MermaidResult)) return false;
        MermaidResult that = (MermaidResult) o;
        return Objects.equals(diagram, that.diagram)
                && Objects.equals(diagnostics, that.diagnostics);
    }

    @Override
    public int hashCode() {
        return Objects.hash(diagram, diagnostics);
    }

    @Override
    public String toString() {
        return "MermaidResult[diagram=" + diagram + ", diagnostics=" + diagnostics + "]";
    }
}
