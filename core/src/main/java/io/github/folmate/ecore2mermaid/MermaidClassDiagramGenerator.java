package io.github.folmate.ecore2mermaid;

import io.github.folmate.ecore2mermaid.emit.MermaidClassDiagramEmitter;
import io.github.folmate.ecore2mermaid.ir.ClassDiagram;
import io.github.folmate.ecore2mermaid.transform.EcoreToClassDiagramTransformer;
import org.eclipse.emf.ecore.EPackage;

import java.util.Collection;
import java.util.List;

public final class MermaidClassDiagramGenerator {

    private final EcoreToClassDiagramTransformer transformer = new EcoreToClassDiagramTransformer();
    private final MermaidClassDiagramEmitter emitter = new MermaidClassDiagramEmitter();

    public MermaidResult generate(EPackage pkg, GeneratorOptions options) {
        return generate(List.of(pkg), options);
    }

    public MermaidResult generate(Collection<EPackage> packages, GeneratorOptions options) {
        EcoreToClassDiagramTransformer.Result result = transformer.transform(packages, options);
        String diagram = emitter.emit(result.diagram());
        return new MermaidResult(diagram, result.diagnostics());
    }
}
