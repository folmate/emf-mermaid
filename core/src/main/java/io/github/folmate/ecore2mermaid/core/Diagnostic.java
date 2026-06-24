package io.github.folmate.ecore2mermaid.core;

import java.util.Objects;

public final class Diagnostic {
    private final Severity severity;
    private final String code;
    private final String message;

    public Diagnostic(Severity severity, String code, String message) {
        this.severity = severity;
        this.code = code;
        this.message = message;
    }

    public Severity severity() { return severity; }
    public String code() { return code; }
    public String message() { return message; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Diagnostic)) return false;
        Diagnostic that = (Diagnostic) o;
        return Objects.equals(severity, that.severity)
                && Objects.equals(code, that.code)
                && Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(severity, code, message);
    }

    @Override
    public String toString() {
        return "Diagnostic[severity=" + severity + ", code=" + code + ", message=" + message + "]";
    }
}
