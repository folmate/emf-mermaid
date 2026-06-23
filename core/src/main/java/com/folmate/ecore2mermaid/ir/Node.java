package com.folmate.ecore2mermaid.ir;

import java.util.List;

public record Node(String id, String label, String stereotype, List<String> members, boolean external) {}
