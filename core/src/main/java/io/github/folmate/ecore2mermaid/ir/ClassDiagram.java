package io.github.folmate.ecore2mermaid.ir;

import java.util.List;

public record ClassDiagram(String direction, String theme, List<Node> nodes, List<Edge> edges) {}
