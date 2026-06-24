package io.github.folmate.ecore2mermaid.core.ir;

import java.util.List;
import java.util.Objects;

public final class Node {
    private final String id;
    private final String label;
    private final String stereotype;
    private final List<String> members;
    private final boolean external;

    public Node(String id, String label, String stereotype, List<String> members, boolean external) {
        this.id = id;
        this.label = label;
        this.stereotype = stereotype;
        this.members = members;
        this.external = external;
    }

    public String id() { return id; }
    public String label() { return label; }
    public String stereotype() { return stereotype; }
    public List<String> members() { return members; }
    public boolean external() { return external; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Node)) return false;
        Node that = (Node) o;
        return external == that.external
                && Objects.equals(id, that.id)
                && Objects.equals(label, that.label)
                && Objects.equals(stereotype, that.stereotype)
                && Objects.equals(members, that.members);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, label, stereotype, members, external);
    }

    @Override
    public String toString() {
        return "Node[id=" + id + ", label=" + label + ", stereotype=" + stereotype
                + ", members=" + members + ", external=" + external + "]";
    }
}
