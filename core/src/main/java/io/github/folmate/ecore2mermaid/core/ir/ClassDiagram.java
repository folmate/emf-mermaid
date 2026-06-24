package io.github.folmate.ecore2mermaid.core.ir;

import java.util.List;
import java.util.Objects;

public final class ClassDiagram {
    private final String direction;
    private final String theme;
    private final List<Node> nodes;
    private final List<Edge> edges;

    public ClassDiagram(String direction, String theme, List<Node> nodes, List<Edge> edges) {
        this.direction = direction;
        this.theme = theme;
        this.nodes = nodes;
        this.edges = edges;
    }

    public String direction() { return direction; }
    public String theme() { return theme; }
    public List<Node> nodes() { return nodes; }
    public List<Edge> edges() { return edges; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClassDiagram)) return false;
        ClassDiagram that = (ClassDiagram) o;
        return Objects.equals(direction, that.direction)
                && Objects.equals(theme, that.theme)
                && Objects.equals(nodes, that.nodes)
                && Objects.equals(edges, that.edges);
    }

    @Override
    public int hashCode() {
        return Objects.hash(direction, theme, nodes, edges);
    }

    @Override
    public String toString() {
        return "ClassDiagram[direction=" + direction + ", theme=" + theme
                + ", nodes=" + nodes + ", edges=" + edges + "]";
    }
}
