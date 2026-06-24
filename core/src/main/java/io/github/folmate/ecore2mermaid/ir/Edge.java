package io.github.folmate.ecore2mermaid.ir;

import java.util.Objects;

public final class Edge {
    private final EdgeKind kind;
    private final String fromId;
    private final String toId;
    private final String fromCard;
    private final String toCard;
    private final String label;

    public Edge(EdgeKind kind, String fromId, String toId, String fromCard, String toCard, String label) {
        this.kind = kind;
        this.fromId = fromId;
        this.toId = toId;
        this.fromCard = fromCard;
        this.toCard = toCard;
        this.label = label;
    }

    public EdgeKind kind() { return kind; }
    public String fromId() { return fromId; }
    public String toId() { return toId; }
    public String fromCard() { return fromCard; }
    public String toCard() { return toCard; }
    public String label() { return label; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Edge)) return false;
        Edge that = (Edge) o;
        return Objects.equals(kind, that.kind)
                && Objects.equals(fromId, that.fromId)
                && Objects.equals(toId, that.toId)
                && Objects.equals(fromCard, that.fromCard)
                && Objects.equals(toCard, that.toCard)
                && Objects.equals(label, that.label);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, fromId, toId, fromCard, toCard, label);
    }

    @Override
    public String toString() {
        return "Edge[kind=" + kind + ", fromId=" + fromId + ", toId=" + toId
                + ", fromCard=" + fromCard + ", toCard=" + toCard + ", label=" + label + "]";
    }
}
