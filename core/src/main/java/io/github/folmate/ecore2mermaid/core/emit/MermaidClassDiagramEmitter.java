package io.github.folmate.ecore2mermaid.core.emit;

import io.github.folmate.ecore2mermaid.core.ir.*;

public final class MermaidClassDiagramEmitter {

    public String emit(ClassDiagram diagram) {
        StringBuilder sb = new StringBuilder();

        if (diagram.theme() != null && !diagram.theme().trim().isEmpty()) {
            sb.append("%%{init: {'theme': '").append(diagram.theme()).append("'}}%%\n");
        }

        sb.append("classDiagram\n");
        sb.append("direction ").append(diagram.direction()).append("\n");

        for (Node node : diagram.nodes()) {
            emitNode(sb, node);
        }

        for (Edge edge : diagram.edges()) {
            emitEdge(sb, edge);
        }

        return sb.toString();
    }

    private void emitNode(StringBuilder sb, Node node) {
        if (node.label() != null) {
            sb.append("class ").append(node.id())
              .append("[\"").append(node.label()).append("\"]").append(" {\n");
        } else {
            sb.append("class ").append(node.id()).append(" {\n");
        }
        if (node.stereotype() != null) {
            sb.append("    <<").append(node.stereotype()).append(">>\n");
        }
        for (String member : node.members()) {
            sb.append("    ").append(member).append("\n");
        }
        sb.append("}\n");
    }

    private void emitEdge(StringBuilder sb, Edge edge) {
        switch (edge.kind()) {
            case INHERITANCE:  emitRelationship(sb, edge, "<|--"); break;
            case REALIZATION:  emitRelationship(sb, edge, "<|.."); break;
            case COMPOSITION:  emitRelationship(sb, edge, "*--");  break;
            case ASSOCIATION:  emitRelationship(sb, edge, "-->");  break;
        }
    }

    private void emitRelationship(StringBuilder sb, Edge edge, String arrow) {
        sb.append(edge.fromId());
        if (edge.fromCard() != null) {
            sb.append(" ").append(edge.fromCard());
        }
        sb.append(" ").append(arrow).append(" ");
        if (edge.toCard() != null) {
            sb.append(edge.toCard()).append(" ");
        }
        sb.append(edge.toId());
        if (edge.label() != null) {
            sb.append(" : ").append(edge.label());
        }
        sb.append("\n");
    }
}
