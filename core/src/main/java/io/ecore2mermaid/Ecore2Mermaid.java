package io.ecore2mermaid;

import org.eclipse.emf.ecore.EPackage;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class Ecore2Mermaid {

    private Ecore2Mermaid() {}

    public static MermaidResult fromPath(Path ecoreFile, GeneratorOptions options) throws EcoreLoadException {
        EcoreLoader loader = new EcoreLoader();
        List<EPackage> packages = loader.load(ecoreFile);
        MermaidClassDiagramGenerator generator = new MermaidClassDiagramGenerator();
        return generator.generate(packages, options);
    }

    public static MermaidResult fromPackages(Collection<EPackage> pkgs, GeneratorOptions options) {
        MermaidClassDiagramGenerator generator = new MermaidClassDiagramGenerator();
        return generator.generate(pkgs, options);
    }
}
