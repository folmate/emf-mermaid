package io.github.folmate.ecore2mermaid.core;

import io.github.folmate.ecore2mermaid.core.emit.MermaidClassDiagramEmitter;
import io.github.folmate.ecore2mermaid.core.transform.EcoreToClassDiagramTransformer;
import org.eclipse.emf.ecore.EPackage;

import java.util.Collection;
import java.util.Collections;

public final class MermaidClassDiagramGenerator {

    private final EcoreToClassDiagramTransformer transformer = new EcoreToClassDiagramTransformer();
    private final MermaidClassDiagramEmitter emitter = new MermaidClassDiagramEmitter();

    public MermaidResult generate(EPackage pkg, GeneratorOptions options) {
        return generate(Collections.singletonList(pkg), options);
    }

    public MermaidResult generate(Collection<EPackage> packages, GeneratorOptions options) {
        EcoreToClassDiagramTransformer.Result result = transformer.transform(packages, options);
        String diagram = emitter.emit(result.diagram());
        return new MermaidResult(diagram, result.diagnostics());
    }
}
