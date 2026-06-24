package io.github.folmate.ecore2mermaid;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class EcoreLoader {

    public List<EPackage> load(Path ecoreFile) throws EcoreLoadException {
        if (!Files.exists(ecoreFile)) {
            throw new EcoreLoadException("File not found: " + ecoreFile);
        }
        if (!Files.isReadable(ecoreFile)) {
            throw new EcoreLoadException("File not readable: " + ecoreFile);
        }

        ResourceSet resourceSet = new ResourceSetImpl();

        // Register factories locally — never touch global registries
        resourceSet.getResourceFactoryRegistry()
                .getExtensionToFactoryMap()
                .put("ecore", new EcoreResourceFactoryImpl());
        resourceSet.getResourceFactoryRegistry()
                .getExtensionToFactoryMap()
                .put("*", new XMIResourceFactoryImpl());

        // Seed local package registry with EcorePackage so built-in types resolve
        resourceSet.getPackageRegistry().put(EcorePackage.eNS_URI, EcorePackage.eINSTANCE);

        URI uri = URI.createFileURI(ecoreFile.toAbsolutePath().toString());
        Resource resource;
        try {
            resource = resourceSet.getResource(uri, true);
        } catch (Exception e) {
            throw new EcoreLoadException("Failed to load resource: " + ecoreFile, e);
        }

        List<EPackage> packages = new ArrayList<>();
        for (var obj : resource.getContents()) {
            if (obj instanceof EPackage pkg) {
                packages.add(pkg);
            }
        }

        if (packages.isEmpty()) {
            throw new EcoreLoadException("No EPackage found in: " + ecoreFile);
        }

        return packages;
    }
}
