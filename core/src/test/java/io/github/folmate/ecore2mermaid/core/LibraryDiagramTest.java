package io.github.folmate.ecore2mermaid.core;

import org.eclipse.emf.ecore.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.github.folmate.ecore2mermaid.core.Ecore2Mermaid;
import io.github.folmate.ecore2mermaid.core.EcoreLoader;
import io.github.folmate.ecore2mermaid.core.GeneratorOptions;
import io.github.folmate.ecore2mermaid.core.MermaidClassDiagramGenerator;
import io.github.folmate.ecore2mermaid.core.MermaidResult;
import io.github.folmate.ecore2mermaid.core.Severity;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LibraryDiagramTest {

    private static Path ecorePath;

    @BeforeAll
    static void setup() throws URISyntaxException {
        URL url = LibraryDiagramTest.class.getClassLoader().getResource("library.ecore");
        assertNotNull(url, "library.ecore not found on classpath");
        ecorePath = Paths.get(url.toURI());
    }

    private static String golden(String name) throws IOException, URISyntaxException {
        URL url = LibraryDiagramTest.class.getClassLoader().getResource(name);
        assertNotNull(url, name + " not found on classpath");
        return new String(Files.readAllBytes(Paths.get(url.toURI())), StandardCharsets.UTF_8);
    }

    @Test
    void defaultOptionsMatchesGolden() throws Exception {
        MermaidResult result = Ecore2Mermaid.fromPath(ecorePath, GeneratorOptions.defaults());
        assertTrue(result.diagnostics().isEmpty(),
                "Expected no diagnostics: " + result.diagnostics());
        assertEquals(golden("library.default.mmd"), result.diagram());
    }

    @Test
    void collapseOppositesMatchesGolden() throws Exception {
        GeneratorOptions opts = GeneratorOptions.builder().collapseOpposites(true).build();
        MermaidResult result = Ecore2Mermaid.fromPath(ecorePath, opts);
        assertTrue(result.diagnostics().isEmpty(),
                "Expected no diagnostics: " + result.diagnostics());
        assertEquals(golden("library.collapsed.mmd"), result.diagram());
        // Verify only one edge for the Book/Author pair
        long authorBookEdgeCount = Arrays.stream(result.diagram().split("\n", -1))
                .filter(l -> l.contains("books / authors") || l.contains("authors / books"))
                .count();
        assertEquals(1, authorBookEdgeCount, "Collapsed opposite pair should appear exactly once");
    }

    @Test
    void noOperationsMatchesGolden() throws Exception {
        GeneratorOptions opts = GeneratorOptions.builder().includeOperations(false).build();
        MermaidResult result = Ecore2Mermaid.fromPath(ecorePath, opts);
        assertTrue(result.diagnostics().isEmpty(),
                "Expected no diagnostics: " + result.diagnostics());
        assertEquals(golden("library.noops.mmd"), result.diagram());
        assertFalse(result.diagram().contains("matches"), "Operations should be absent");
    }

    @Test
    void ePackageEntryPointProducesSameOutput() throws Exception {
        // Load via EcoreLoader, then call generate(EPackage, opts) directly
        EcoreLoader loader = new EcoreLoader();
        List<EPackage> packages = loader.load(ecorePath);
        assertEquals(1, packages.size());

        MermaidClassDiagramGenerator generator = new MermaidClassDiagramGenerator();
        MermaidResult fromPkg = generator.generate(packages.get(0), GeneratorOptions.defaults());
        MermaidResult fromPath = Ecore2Mermaid.fromPath(ecorePath, GeneratorOptions.defaults());

        assertEquals(fromPath.diagram(), fromPkg.diagram(),
                "generate(EPackage) must produce identical output to fromPath()");
    }

    @Test
    void externalReferenceEmitsStubAndWarn() {
        // Build a minimal dynamic EPackage with a reference to an out-of-scope type
        EPackage pkg = EcoreFactory.eINSTANCE.createEPackage();
        pkg.setName("test");
        pkg.setNsURI("http://test/external");
        pkg.setNsPrefix("test");

        EClass externalClass = EcoreFactory.eINSTANCE.createEClass();
        externalClass.setName("External");
        // externalClass is intentionally NOT added to pkg

        EClass ownerClass = EcoreFactory.eINSTANCE.createEClass();
        ownerClass.setName("Owner");
        pkg.getEClassifiers().add(ownerClass);

        EReference ref = EcoreFactory.eINSTANCE.createEReference();
        ref.setName("target");
        ref.setEType(externalClass); // points to a class not in pkg
        ref.setLowerBound(0);
        ref.setUpperBound(1);
        ownerClass.getEStructuralFeatures().add(ref);

        MermaidClassDiagramGenerator generator = new MermaidClassDiagramGenerator();
        MermaidResult result = generator.generate(pkg, GeneratorOptions.defaults());

        // Should not throw; should contain an external stub
        assertDoesNotThrow(() -> generator.generate(pkg, GeneratorOptions.defaults()));
        assertTrue(result.diagram().contains("<<external>>"),
                "Expected an <<external>> stub node in diagram:\n" + result.diagram());
        assertTrue(result.diagnostics().stream()
                        .anyMatch(d -> d.severity() == Severity.WARN && d.code().equals("E2M_EXTERNAL_REF")),
                "Expected a WARN diagnostic for external reference");
    }
}
