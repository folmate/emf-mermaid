package com.folmate.ecore2mermaid.ir;

public record Edge(EdgeKind kind, String fromId, String toId, String fromCard, String toCard, String label) {}
