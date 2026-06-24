package io.github.folmate.ecore2mermaid.transform;

import io.github.folmate.ecore2mermaid.Diagnostic;
import io.github.folmate.ecore2mermaid.GeneratorOptions;
import io.github.folmate.ecore2mermaid.Severity;
import io.github.folmate.ecore2mermaid.ir.*;
import org.eclipse.emf.ecore.*;

import java.util.*;

public final class EcoreToClassDiagramTransformer {

    public static final class Result {
        private final ClassDiagram diagram;
        private final List<Diagnostic> diagnostics;

        public Result(ClassDiagram diagram, List<Diagnostic> diagnostics) {
            this.diagram = diagram;
            this.diagnostics = diagnostics;
        }

        public ClassDiagram diagram() { return diagram; }
        public List<Diagnostic> diagnostics() { return diagnostics; }
    }

    public Result transform(Collection<EPackage> packages, GeneratorOptions opts) {
        List<Diagnostic> diagnostics = new ArrayList<>();

        // Collect all classifiers in deterministic order; sort unordered input by nsURI
        List<EPackage> orderedPkgs = new ArrayList<>(packages);
        orderedPkgs.sort(Comparator.comparing(EPackage::getNsURI));

        List<EClassifier> allClassifiers = new ArrayList<>();
        for (EPackage pkg : orderedPkgs) {
            collectClassifiers(pkg, allClassifiers);
        }

        // Build simple-name → classifiers map to detect collisions
        Map<String, List<EClassifier>> bySimpleName = new LinkedHashMap<>();
        for (EClassifier c : allClassifiers) {
            bySimpleName.computeIfAbsent(c.getName(), k -> new ArrayList<>()).add(c);
        }

        Set<EClassifier> needsQualified = new LinkedHashSet<>();
        for (Map.Entry<String, List<EClassifier>> entry : bySimpleName.entrySet()) {
            if (entry.getValue().size() > 1) {
                needsQualified.addAll(entry.getValue());
                diagnostics.add(new Diagnostic(Severity.WARN, "E2M_NAME_COLLISION",
                        "Name collision for '" + entry.getKey() + "'; using qualified ids"));
            }
        }

        // id resolution function
        Set<EClassifier> inScope = new LinkedHashSet<>(allClassifiers);

        // Build nodes
        List<Node> nodes = new ArrayList<>();
        List<Edge> edges = new ArrayList<>();

        // Track collapsed opposite pairs to avoid double-emission
        Set<String> collapsedPairs = new LinkedHashSet<>();

        for (EClassifier classifier : allClassifiers) {
            if (classifier instanceof EClass) {
                EClass eClass = (EClass) classifier;
                nodes.add(buildClassNode(eClass, opts, needsQualified, inScope, diagnostics));
            } else if (classifier instanceof EEnum) {
                EEnum eEnum = (EEnum) classifier;
                nodes.add(buildEnumNode(eEnum, needsQualified));
            }
            // EDataType (non-enum) has no node
        }

        // Build edges after all nodes declared
        for (EClassifier classifier : allClassifiers) {
            if (classifier instanceof EClass) {
                EClass eClass = (EClass) classifier;
                buildEdges(eClass, opts, needsQualified, inScope, nodes, edges, collapsedPairs, diagnostics);
            }
        }

        ClassDiagram diagram = new ClassDiagram(opts.direction(), opts.theme(), nodes, edges);
        return new Result(diagram, diagnostics);
    }

    private void collectClassifiers(EPackage pkg, List<EClassifier> out) {
        out.addAll(pkg.getEClassifiers());
        for (EPackage sub : pkg.getESubpackages()) {
            collectClassifiers(sub, out);
        }
    }

    private String nodeId(EClassifier c, boolean needsQualified, Set<EClassifier> needsQualifiedSet) {
        if (needsQualifiedSet.contains(c) || needsQualified) {
            return sanitize(qualifiedName(c));
        }
        return sanitize(c.getName());
    }

    private String nodeId(EClassifier c, Set<EClassifier> needsQualifiedSet, GeneratorOptions opts) {
        boolean useQual = opts.useFullyQualifiedNames() || needsQualifiedSet.contains(c);
        if (useQual) {
            return sanitize(qualifiedName(c));
        }
        return sanitize(c.getName());
    }

    private String nodeLabel(EClassifier c, Set<EClassifier> needsQualifiedSet, GeneratorOptions opts) {
        boolean useQual = opts.useFullyQualifiedNames() || needsQualifiedSet.contains(c);
        if (useQual) {
            return qualifiedName(c); // display label uses dots (unsanitized)
        }
        return c.getName();
    }

    private String qualifiedName(EClassifier c) {
        EPackage pkg = c.getEPackage();
        if (pkg == null) return c.getName();
        String pkgName = pkg.getName();
        if (pkgName == null || pkgName.isEmpty()) return c.getName();
        return pkgName + "." + c.getName();
    }

    private String sanitize(String name) {
        if (name == null) return "_";
        return name.replace('.', '_').replace('/', '_');
    }

    private Node buildClassNode(EClass eClass, GeneratorOptions opts,
                                 Set<EClassifier> needsQualifiedSet, Set<EClassifier> inScope,
                                 List<Diagnostic> diagnostics) {
        String id = nodeId(eClass, needsQualifiedSet, opts);
        String label = nodeLabel(eClass, needsQualifiedSet, opts);
        String displayLabel = id.equals(label) ? null : label;

        String stereotype;
        if (eClass.isInterface()) stereotype = "interface";
        else if (eClass.isAbstract()) stereotype = "abstract";
        else stereotype = null;

        List<String> members = new ArrayList<>();

        // Attributes first (in declared order), skip derived/transient/volatile
        for (EAttribute attr : eClass.getEAttributes()) {
            if (attr.isDerived() || attr.isTransient() || attr.isVolatile()) continue;
            members.add(formatAttribute(attr, opts));
        }

        // References (in declared order), skip derived/transient/volatile
        // (members only — edges handled separately)

        // Operations
        if (opts.includeOperations()) {
            for (EOperation op : eClass.getEOperations()) {
                members.add(formatOperation(op));
            }
        }

        return new Node(id, displayLabel, stereotype, members, false);
    }

    private Node buildEnumNode(EEnum eEnum, Set<EClassifier> needsQualifiedSet) {
        // Use simple name for display; qualify only if collision
        String id = needsQualifiedSet.contains(eEnum)
                ? sanitize(qualifiedName(eEnum)) : sanitize(eEnum.getName());

        List<String> members = new ArrayList<>();
        for (EEnumLiteral lit : eEnum.getELiterals()) {
            members.add(lit.getName());
        }
        return new Node(id, null, "enumeration", members, false);
    }

    private String formatAttribute(EAttribute attr, GeneratorOptions opts) {
        String typeRef = typeRef(attr.getEType());
        String mult = opts.showMultiplicity()
                ? multiplicityBracket(attr.getLowerBound(), attr.getUpperBound()) : "";
        return "+" + attr.getName() + " : " + typeRef + mult;
    }

    private String multiplicityBracket(int lower, int upper) {
        // suppress 0..1
        if (lower == 0 && upper == 1) return "";
        String u = upper == -1 ? "*" : String.valueOf(upper);
        return " [" + lower + ".." + u + "]";
    }

    private String cardinalityQuoted(int lower, int upper) {
        // suppress 0..1 (return null = omit)
        if (lower == 0 && upper == 1) return null;
        String u = upper == -1 ? "*" : String.valueOf(upper);
        return "\"" + lower + ".." + u + "\"";
    }

    private String typeRef(EClassifier type) {
        if (type == null) return "void";
        if (type instanceof EEnum) return type.getName();
        if (type instanceof EDataType) {
            EDataType dt = (EDataType) type;
            if (dt.getEPackage() == EcorePackage.eINSTANCE) {
                // built-in Ecore datatype
                return type.getName();
            }
            // Custom EDataType
            String ic = dt.getInstanceClassName();
            if (ic == null || ic.trim().isEmpty()) return type.getName() + "@datatype";
            return type.getName() + "@" + ic;
        }
        if (type instanceof EClass) return type.getName();
        return type.getName();
    }

    private String formatOperation(EOperation op) {
        StringBuilder sb = new StringBuilder("+").append(op.getName()).append("(");
        List<EParameter> params = op.getEParameters();
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) sb.append(", ");
            EParameter p = params.get(i);
            sb.append(p.getName()).append(" : ").append(typeRef(p.getEType()));
        }
        sb.append(") : ");
        sb.append(op.getEType() != null ? typeRef(op.getEType()) : "void");
        return sb.toString();
    }

    private void buildEdges(EClass eClass, GeneratorOptions opts,
                             Set<EClassifier> needsQualifiedSet, Set<EClassifier> inScope,
                             List<Node> nodes, List<Edge> edges,
                             Set<String> collapsedPairs, List<Diagnostic> diagnostics) {
        String fromId = nodeId(eClass, needsQualifiedSet, opts);

        // Inheritance / realization edges
        for (EClass superType : eClass.getESuperTypes()) {
            String toId = nodeId(superType, needsQualifiedSet, opts);
            EdgeKind kind;
            if (opts.realizeInterfaces() && superType.isInterface()) {
                kind = EdgeKind.REALIZATION;
            } else {
                kind = EdgeKind.INHERITANCE;
            }
            edges.add(new Edge(kind, toId, fromId, null, null, null));
        }

        // Reference edges
        for (EReference ref : eClass.getEReferences()) {
            if (ref.isDerived() || ref.isTransient() || ref.isVolatile()) continue;

            EReference opposite = ref.getEOpposite();

            if (opts.collapseOpposites() && opposite != null) {
                // Build a canonical pair key to dedupe
                String pairKey = canonicalPairKey(ref, opposite);
                if (collapsedPairs.contains(pairKey)) continue;
                collapsedPairs.add(pairKey);

                // Determine which side is "left"
                EReference leftRef, rightRef;
                if (ref.isContainment() && !opposite.isContainment()) {
                    leftRef = ref; rightRef = opposite;
                } else if (!ref.isContainment() && opposite.isContainment()) {
                    leftRef = opposite; rightRef = ref;
                } else {
                    // sort by owning classifier id
                    String ownId = nodeId(eClass, needsQualifiedSet, opts);
                    String oppId = nodeId(opposite.getEContainingClass(), needsQualifiedSet, opts);
                    if (ownId.compareTo(oppId) <= 0) {
                        leftRef = ref; rightRef = opposite;
                    } else {
                        leftRef = opposite; rightRef = ref;
                    }
                }

                EClass leftOwner = leftRef.getEContainingClass();
                EClass rightOwner = rightRef.getEContainingClass();
                String leftId = nodeId(leftOwner, needsQualifiedSet, opts);
                String rightId = nodeId(rightOwner, needsQualifiedSet, opts);

                // srcCard = rightRef bounds (how many left-owner instances from right's perspective)
                String srcCard = opts.showMultiplicity()
                        ? cardinalityQuoted(rightRef.getLowerBound(), rightRef.getUpperBound()) : null;
                // tgtCard = leftRef bounds
                String tgtCard = opts.showMultiplicity()
                        ? cardinalityQuoted(leftRef.getLowerBound(), leftRef.getUpperBound()) : null;

                EdgeKind kind = leftRef.isContainment() ? EdgeKind.COMPOSITION : EdgeKind.ASSOCIATION;
                String label = leftRef.getName() + " / " + rightRef.getName();

                String toId = resolveTarget(leftRef, needsQualifiedSet, inScope, opts, nodes, diagnostics);
                if (toId == null) continue;

                edges.add(new Edge(kind, leftId, toId, srcCard, tgtCard, label));

            } else {
                // Each reference as its own edge
                String toId = resolveTarget(ref, needsQualifiedSet, inScope, opts, nodes, diagnostics);
                if (toId == null) continue;

                String srcCard = null;
                if (opts.showMultiplicity() && opposite != null) {
                    srcCard = cardinalityQuoted(opposite.getLowerBound(), opposite.getUpperBound());
                }
                String tgtCard = opts.showMultiplicity()
                        ? cardinalityQuoted(ref.getLowerBound(), ref.getUpperBound()) : null;

                EdgeKind kind = ref.isContainment() ? EdgeKind.COMPOSITION : EdgeKind.ASSOCIATION;
                edges.add(new Edge(kind, fromId, toId, srcCard, tgtCard, ref.getName()));
            }
        }
    }

    private String resolveTarget(EReference ref, Set<EClassifier> needsQualifiedSet,
                                  Set<EClassifier> inScope, GeneratorOptions opts,
                                  List<Node> nodes, List<Diagnostic> diagnostics) {
        EClassifier targetType = ref.getEType();
        if (targetType == null || targetType.eIsProxy()) {
            if (!opts.includeExternalStubs()) {
                diagnostics.add(new Diagnostic(Severity.WARN, "E2M_EXTERNAL_REF",
                        "Unresolved target for '" + ref.getName() + "'; skipping edge"));
                return null;
            }
            String stubId = "UnresolvedType";
            ensureExternalStub(stubId, nodes);
            diagnostics.add(new Diagnostic(Severity.WARN, "E2M_EXTERNAL_REF",
                    "Unresolved target for reference '" + ref.getName() + "'; emitted as external stub"));
            return stubId;
        }
        if (inScope.contains(targetType)) {
            return nodeId(targetType, needsQualifiedSet, opts);
        }
        // Out-of-scope target
        if (!opts.includeExternalStubs()) {
            diagnostics.add(new Diagnostic(Severity.WARN, "E2M_EXTERNAL_REF",
                    "Type '" + targetType.getName() + "' resolved outside input scope; skipping edge"));
            return null;
        }
        String stubId = sanitize(targetType.getName() != null ? targetType.getName() : "ExternalType");
        ensureExternalStub(stubId, nodes);
        diagnostics.add(new Diagnostic(Severity.WARN, "E2M_EXTERNAL_REF",
                "Type '" + targetType.getName() + "' resolved outside input scope; emitted as external stub"));
        return stubId;
    }

    private void ensureExternalStub(String id, List<Node> nodes) {
        for (Node n : nodes) {
            if (n.id().equals(id)) return;
        }
        nodes.add(new Node(id, null, "external", Collections.<String>emptyList(), true));
    }

    private String canonicalPairKey(EReference a, EReference b) {
        // use deterministic ordering based on containing class name + ref name
        String nameA = a.getEContainingClass().getName() + "." + a.getName();
        String nameB = b.getEContainingClass().getName() + "." + b.getName();
        if (nameA.compareTo(nameB) <= 0) return nameA + "|" + nameB;
        return nameB + "|" + nameA;
    }
}
