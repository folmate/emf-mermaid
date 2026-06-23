# Specification: `ecore2mermaid`

**Audience:** an implementing coding agent.
**Goal:** A Java/JVM tool that converts an Eclipse EMF **Ecore metamodel** into a **Mermaid `classDiagram`**. Delivered as a library with a thin, loosely-coupled CLI on top. Architecture must leave room for a future *instance-model → object-diagram* path without building any of it now.

This document records decisions already made with the product owner. Implement to it. Where a value is marked **(placeholder)**, substitute a real one (e.g. your own Maven group / base package).

---

## 1. Scope

### In scope (MVP)
- Load an Ecore metamodel and emit a single Mermaid `classDiagram` as a **string**.
- Three input forms (library); one input form (CLI) — see §4.
- Mapping of EClasses, EAttributes, EReferences, EOperations, EEnums, inheritance, containment, cardinalities — see §6.
- CLI that writes the diagram to stdout or a file, with diagnostics routed per §8.

### Explicitly out of scope (for now, but do not architect against)
- Instance models (`.xmi` object graphs) → object diagrams. **Reserve the seam (§9); write no code, interfaces, enums, or branches for it yet.**
- Any diagram type other than `classDiagram`.
- Rendering to SVG/PNG/HTML (downstream concern — leave to mermaid-cli / Kroki).
- Filtering / scoping / focus-on-class (deferred; keep the transformer clean so it can be added).
- Generics fidelity, derived/transient features, attribute default values, member visibility modelling (see §6 for how these are handled — mostly omitted).

---

## 2. Tech stack & build

- **Build tool:** **Gradle** (Kotlin DSL preferred), **multi-module**.
- **Configurable Java + EMF versions (required):** expose both as Gradle properties (in `gradle.properties`, overridable with `-P`), with these defaults:
  - `javaRelease` = `17` — used for `release`/source/target compatibility of the produced artifacts.
  - `emfVersion` = `2.41.0` — applied to all `org.eclipse.emf:*` dependencies (§ below).
  - `buildJdkVersion` = `21` — Gradle Java toolchain (the JDK that compiles).

  Wire these through `build.gradle.kts` (e.g. `java.toolchain.languageVersion`, `tasks.withType<JavaCompile> { options.release = ... }`, and a `val emfVersion = ...` used in dependency declarations). Changing a property must require **no source edits**.
- **No Eclipse runtime / OSGi.** Use EMF *standalone* jars from Maven Central only.
- **EMF isolation:** the tool MUST NOT mutate any global EMF registry (`EPackage.Registry.INSTANCE`, `Resource.Factory.Registry.INSTANCE`, etc.) or global EMF configuration. All loading happens in a private, per-call `ResourceSet` with a local registry (§5).

### Modules
```
ecore2mermaid/
  core/        # library; NO CLI dependency, NO printing, NO System.out/err
  cli/         # thin CLI; depends on :core
  core/src/test/resources/   # sample .ecore + golden .mmd fixtures
```

### Dependencies (confirm latest compatible versions at build time)
- `core` (all at `$emfVersion`):
  - `org.eclipse.emf:org.eclipse.emf.ecore`
  - `org.eclipse.emf:org.eclipse.emf.common`
  - `org.eclipse.emf:org.eclipse.emf.ecore.xmi` (needed to load `.ecore` resources)
- `cli`:
  - `:core`
  - **JCommander** — `org.jcommander:jcommander` (2.x). *(If a blocker arises, picocli is an acceptable substitute, but JCommander is the preference.)*
- test (both): JUnit 5 (`org.junit.jupiter:junit-jupiter`).

> Note: EMF artifacts release roughly in lockstep, so the same `emfVersion` should resolve for all three on Maven Central (`2.41.0` is known-good at time of writing). If a chosen `emfVersion` requires a higher Java floor than `javaRelease`, the build should fail clearly; bump `javaRelease` to match.

---

## 3. Naming

- Root package: `io.ecore2mermaid` **(placeholder — set your own)**.
- Artifact id: `ecore2mermaid`.

---

## 4. Public API & entry points

The **core generator only knows `EPackage`s**. File loading is a *separate* concern that produces `EPackage`s and feeds the generator. This keeps responsibilities clean and makes the future instance path orthogonal.

### 4.1 Core generator (library — primary form)
```java
public final class MermaidClassDiagramGenerator {
    public MermaidResult generate(EPackage pkg, GeneratorOptions options);
    public MermaidResult generate(Collection<EPackage> packages, GeneratorOptions options);
}
```
- Accepts both **generated** packages (e.g. `MyPackage.eINSTANCE`) and **dynamic / reflective** `EPackage`s — no distinction needed; both are just `EPackage`.
- **Never prints.** **Never touches global registries.** Pure: same input + options ⇒ identical output (§7).

### 4.2 Result object
```java
public record MermaidResult(String diagram, List<Diagnostic> diagnostics) {}

public record Diagnostic(Severity severity, String code, String message) {}
public enum Severity { INFO, WARN, ERROR }
```
- `diagram` is the complete Mermaid source (no theme/init line unless options request it — §6.7).
- `diagnostics` carries recoverable issues (unresolved external types, name-collision disambiguation, etc.). Hard failures throw (§8).

### 4.3 File loader (library — convenience)
```java
public final class EcoreLoader {
    // Loads an .ecore file into an isolated ResourceSet and returns all
    // EPackages found (root packages; subpackages are reachable via getESubpackages()).
    public List<EPackage> load(Path ecoreFile) throws EcoreLoadException;
}
```

### 4.4 Façade (library — path → diagram in one call)
```java
public final class Ecore2Mermaid {
    public static MermaidResult fromPath(Path ecoreFile, GeneratorOptions options); // load + generate
    public static MermaidResult fromPackages(Collection<EPackage> pkgs, GeneratorOptions options);
}
```
- `fromPath` = `EcoreLoader.load(...)` then `MermaidClassDiagramGenerator.generate(...)`. Loader diagnostics and generator diagnostics are merged into the returned result.

### 4.5 CLI input
- CLI accepts exactly **one `.ecore` file path** per invocation and calls `Ecore2Mermaid.fromPath`. (Multiple-file/combined input is a future extension — do not build it; one diagram per call.)

---

## 5. Loading semantics (EcoreLoader)

- Create a fresh `ResourceSetImpl` per `load` call.
- Register factories **locally** on that resource set, not globally:
  - `resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("ecore", new EcoreResourceFactoryImpl())`
  - `...put("*", new XMIResourceFactoryImpl())` (fallback)
  - Seed the resource set's **local** package registry with `EcorePackage.eINSTANCE` so built-in Ecore types resolve.
- Load the resource for the given path, then return all root `EPackage`s in the resource (`resource.getContents()` filtered to `EPackage`). Subpackages are reached later by the transformer via `getESubpackages()` (recursively).
- **Graceful cross-references (decision A3):** attempt to resolve referenced types from other resources/registered packages. If resolution fails, **do not fail generation** — record a `WARN` diagnostic and let the transformer emit a stub node (§6.5). `--no-external` (§6.6 option) disables stub emission.
- Throw `EcoreLoadException` (→ non-zero CLI exit) only for unrecoverable load failures: file missing/unreadable, malformed XML that yields no usable `EPackage`.

---

## 6. Mapping rules (Ecore → Mermaid `classDiagram`)

Mermaid reference: classes with `<<stereotype>>` annotations; relationship arrows `<|--` (inheritance), `<|..` (realization), `*--` (composition), `-->` (association); quoted cardinality strings on relationship ends; member lines `+name : Type`.

All members are rendered **public (`+`)** — Ecore has no visibility. Attribute **default values are never shown**. **Generics are flattened** to the raw classifier name (type arguments ignored). **Derived, transient, and volatile** structural features are **omitted entirely** (no option; just skip).

### 6.1 EClass → class node
- `class <Id>` where `<Id>` is the node identifier (§6.8).
- Stereotype:
  - `eClass.isInterface()` ⇒ `<<interface>>` (takes precedence; interfaces are abstract in Ecore).
  - else `eClass.isAbstract()` ⇒ `<<abstract>>`.
  - else no stereotype.

### 6.2 EAttribute → member
- Line: `+<name> : <TypeRef><multiplicity>`
- `<TypeRef>` resolution by attribute's `eType`:
  - **Built-in Ecore datatype** (belongs to `EcorePackage`, e.g. EString, EInt, EBoolean): simple name — `EString`, `EInt`, …
  - **Custom `EDataType`** (not in `EcorePackage`, not an `EEnum`): `<Name>@<instanceClassName>` — e.g. `Money@java.math.BigDecimal`. If `instanceClassName` is null/blank, use `<Name>@datatype`.
  - **`EEnum`**: simple enum name (the enum is rendered as its own `<<enumeration>>` node — §6.4).
- `<multiplicity>`: see §6.6. Attribute multiplicities use **bracket** form appended to the type, e.g. `+tags : EString [0..*]`. Suppressed only for `[0..1]`.

### 6.3 EOperation → member (optional)
- Included by default; suppressed when `includeOperations = false`.
- Line: `+<name>(<p1> : <T1>, <p2> : <T2>) : <ReturnType>`
  - Parameter types use the same type-ref rules as §6.2 (no parameter multiplicity for MVP).
  - Return type from `eType`; if none, use `void`.
  - No operation/return multiplicity brackets for MVP (keep noise down).

### 6.4 EEnum → enumeration node
- Separate `class <EnumId>` with `<<enumeration>>`.
- Each `EEnumLiteral` becomes a member line: just the literal **name** (no value, no `+`/type). Mermaid renders enum literals as plain member lines.

### 6.5 eSuperTypes → inheritance / realization
- Default: for each super type `S` of `C`, emit `S <|-- C` (triangle points at the base `S`; reads "C is-a S").
- If `realizeInterfaces = true` **and** `S.isInterface()`: emit `S <|.. C` (dashed realization, base on the left for consistency).
- One edge per super type (Ecore allows multiple supertypes).

### 6.6 EReference → composition / association
- `isContainment()` ⇒ **composition**: `<Owner> "<srcCard>" *-- "<tgtCard>" <Target> : <refName>`
- non-containment ⇒ **association**: `<Owner> "<srcCard>" --> "<tgtCard>" <Target> : <refName>`
- `<tgtCard>` from the reference's own `lowerBound`/`upperBound`. `<srcCard>` from the `eOpposite` bounds if an opposite exists, else omit the source cardinality (and its quotes).
- **Cardinality string format** (relationship ends use **quoted** form): `"l..u"`, with `upperBound == -1` rendered as `*` (so `"0..*"`, `"1..*"`, `"1..1"`, `"2..5"`). Suppress a cardinality only when it equals `0..1`. Controlled by `showMultiplicity` (when false, omit all cardinality strings everywhere — relationships and attributes).
- **Target outside input scope** (resolved from another resource, or unresolvable proxy):
  - If allowed (`includeExternalStubs = true`, default): emit a stub node `class <Target>` annotated `<<external>>`, draw the edge, add a `WARN`/`INFO` diagnostic. Name the stub by the type's name if available, else by URI fragment.
  - If `includeExternalStubs = false`: skip the edge, add a `WARN` diagnostic. **Never throw.**

### 6.6.1 eOpposite (bidirectional) handling
- **Default (`collapseOpposites = false`):** render each reference as its **own** edge (so a bidirectional pair produces two lines).
- **`collapseOpposites = true`:** render the opposite pair as a **single** edge. Deterministically pick the "left" end (e.g. the containment side if exactly one side is containment; otherwise the side whose owning classifier sorts first by §6.8 id). Put both end cardinalities on, and a combined label `<refA> / <oppositeRefB>`. Emit the pair exactly once (dedupe).

### 6.7 Direction & theme
- Prepend `direction <TB|LR>` inside the diagram (default `TB`).
- If `theme` option set, prepend a minimal init line: `%%{init: {'theme': '<theme>'}}%%` before `classDiagram`. Otherwise emit nothing extra.

### 6.8 Node identifiers, labels, name collisions
- Mermaid class ids may contain only `[A-Za-z0-9_-]`. Sanitize by replacing `.` and `/` with `_`.
- Default: use the classifier **simple name** as both id and display.
- `useFullyQualifiedNames = true`: id = sanitized fully-qualified name (e.g. `library_Book`); display label via `class library_Book["library.Book"]`.
- **Collision safety (overrides the flag when needed):** if two classifiers share a simple name, the generator MUST disambiguate *those* classifiers using qualified ids (with simple display labels) and emit a `WARN` diagnostic — even if `useFullyQualifiedNames` is false — so the output is always valid Mermaid.

---

## 6.9 GeneratorOptions

Immutable, with a builder. Defaults in brackets.

| Option | Default | Effect |
|---|---|---|
| `direction` | `TB` | `direction TB`/`LR` line |
| `showMultiplicity` | `true` | show cardinalities (suppressing `0..1`); false hides all |
| `includeOperations` | `true` | include EOperations |
| `collapseOpposites` | `false` | false = two edges per opposite pair; true = one |
| `useFullyQualifiedNames` | `false` | false = simple names; true = qualified ids + labels |
| `realizeInterfaces` | `false` | false = `<|--` for all supertypes; true = `<|..` when super is interface |
| `includeExternalStubs` | `true` | emit `<<external>>` stub nodes for out-of-scope/unresolved targets |
| `theme` | `null` | optional Mermaid theme via init line |

(Derived/transient/volatile = always hidden; default values = always hidden; visibility = always `+`; generics = always flattened. These are **not** options for MVP.)

---

## 7. Determinism

Same input + options ⇒ byte-identical output (required for golden-file tests).
- **Packages:** iterate the provided collection in order; if the collection is unordered, sort by `nsURI`. Recurse into `getESubpackages()` in declared order.
- **Classifiers within a package:** declared order (`getEClassifiers()`).
- **Members within a classifier:** declared order (attributes, then references, then operations — each in declared order). *(Pick this fixed section order and keep it.)*
- **Relationships:** emitted after all class declarations, in the order their source classifier/feature is visited.
- No use of `HashMap`/`HashSet` iteration for anything that reaches output; use `LinkedHashMap`/sorted structures.

---

## 8. CLI behaviour, I/O routing, exit codes

The **library never prints**. The **CLI owns all I/O.** This is the single source of truth for "who prints what."

### Flags (JCommander)
| Flag | Meaning |
|---|---|
| `-i, --input <path>` (or positional) | input `.ecore` file (required) |
| `-o, --output <file>` | write diagram to file; **default = stdout** |
| `--direction <TB\|LR>` | default `TB` |
| `--hide-multiplicity` | `showMultiplicity = false` |
| `--no-operations` | `includeOperations = false` |
| `--full-names` | `useFullyQualifiedNames = true` |
| `--collapse-opposites` | `collapseOpposites = true` |
| `--realize-interfaces` | `realizeInterfaces = true` |
| `--no-external` | `includeExternalStubs = false` |
| `--theme <name>` | set theme |
| `-h, --help` / `--version` | usage / version |

### Output routing — the diagram
- `--output <file>` given ⇒ write the diagram (only, no diagnostic lines) to that file.
- otherwise ⇒ write the diagram to **stdout**.

### Output routing — diagnostics/messages
Diagnostics always go to **stdout**, formatted so a single regex strips them and so the raw stdout is *still valid Mermaid* if it isn't stripped. Each diagnostic is one line, prefixed with a fixed marker that is also a Mermaid comment (`%%`):

```
%% [ecore2mermaid] <SEVERITY> [<CODE>] <message>
```

e.g. `%% [ecore2mermaid] WARN [E2M_EXTERNAL_REF] Type 'Foo' resolved outside input scope; emitted as external stub`

- **diagram → stdout (default):** write the diagram, then append the diagnostic comment lines after it. The combined stream parses as valid Mermaid (the `%%` lines are comments). Consumers who want the bare diagram strip them with e.g. `grep -v '^%% \[ecore2mermaid\] '` or `sed '/^%% \[ecore2mermaid\] /d'`.
- **diagram → file (`-o`):** the file contains only the diagram; the diagnostic comment lines go to stdout on their own.

The marker prefix MUST be fixed and documented in `--help` so the strip regex is stable. Do **not** write diagnostics to stderr, and do **not** interleave them into the middle of the diagram (append after it).

### Exit codes
- `0` — success, including when only `WARN`/`INFO` diagnostics were produced.
- non-zero — hard failure: input not found/unreadable, load produced no usable `EPackage`, I/O error writing output, bad arguments. Print an `ERROR` line (to stderr) and exit non-zero.

---

## 9. Internal architecture & extensibility seam

Build a clean transform→emit pipeline around a small intermediate representation (IR). The IR boundary is the seam that lets a future instance-model path slot in **without** touching existing code. **Do not** add interfaces, enums, abstract bases, or branches for instance models now — no placeholders, no dead code.

```
Path ──EcoreLoader──▶ List<EPackage>
                         │
        EcoreToClassDiagramTransformer  (EPackage… → ClassDiagram IR + diagnostics)
                         │
        MermaidClassDiagramEmitter      (ClassDiagram IR → String)
                         │
                    MermaidResult
```

Concrete classes to implement (single responsibility each):
- `EcoreLoader` — files → `EPackage`s (isolated). No printing.
- `EcoreToClassDiagramTransformer` — `Collection<EPackage>` + options → IR + diagnostics. No printing, no Mermaid string building.
- `MermaidClassDiagramEmitter` — IR → Mermaid `String`. No EMF knowledge.
- `MermaidClassDiagramGenerator` — orchestrates transformer + emitter, returns `MermaidResult`.
- `Ecore2Mermaid` — façade (§4.4).
- CLI `Main` (in `:cli`) — arg parsing + I/O routing only.

Illustrative IR (shape is up to you; keep it diagram-type-neutral and EMF-free):
```java
record ClassDiagram(String direction, String theme, List<Node> nodes, List<Edge> edges) {}
record Node(String id, String label, String stereotype, List<String> members, boolean external) {}
record Edge(EdgeKind kind, String fromId, String toId, String fromCard, String toCard, String label) {}
enum EdgeKind { INHERITANCE, REALIZATION, COMPOSITION, ASSOCIATION }
```
*(`EdgeKind`/`Node` here serve only the class-diagram MVP. Adding instance support later means a new transformer producing a possibly-extended IR and a new emitter — not editing these.)*

**Responsibility rule:** only the CLI calls `System.out`/`System.err`. Loader/transformer/emitter/generator return data and `Diagnostic`s; they never print.

---

## 10. Sample model & tests (keep it to a few)

Bundle one small sample metamodel under `core/src/test/resources/library.ecore` exercising the interesting cases:
- package `library` (nsURI `http://example.com/library`)
- abstract `Item` (abstract)
- interface `Identifiable` (interface) with attribute `id : EString`
- `Book` extends `Item`, implements `Identifiable`; attributes `title : EString`, `tags : EString [0..*]`; `category : BookCategory`
- `Author` with `name : EString`
- enum `BookCategory { FICTION, NONFICTION, REFERENCE }`
- `Library` with **containment** ref `books : Book [0..*]` and containment `authors : Author [0..*]`
- `Book.authors : Author [1..*]` (non-containment) with **eOpposite** `Author.books : Book [0..*]`
- one custom `EDataType` `ISBN` (instanceClassName `java.lang.String`) used by `Book.isbn`
- an operation `Book.matches(query : EString) : EBoolean`

### Tests (JUnit 5, golden `.mmd` fixtures)
1. **Default options, path input** → matches `library.default.mmd`.
2. **`collapseOpposites = true`** → the `Book`/`Author` pair appears once → `library.collapsed.mmd`.
3. **`includeOperations = false`** → no `matches(...)` line → `library.noops.mmd`.
4. **EPackage input path** — build a tiny dynamic `EPackage` in-test (or use the loaded one) and call `generate(EPackage, opts)` directly, proving the non-file entry point works and produces the same output as the file path for the same model.
5. **External/unresolved reference** — feed a reference whose target is out of input scope; assert a stub `<<external>>` node + a `WARN` diagnostic and **no exception**, exit/normal completion.

Generate goldens once, eyeball them in mermaid.live, then check them in. Tests compare exact strings (determinism from §7 makes this safe).

---

## 11. Acceptance checklist
- [ ] `:core` has zero CLI/printing dependencies; never mutates global EMF state.
- [ ] Library accepts `EPackage`, `Collection<EPackage>`, and `Path`; CLI accepts one `.ecore` path.
- [ ] Mapping per §6 (stereotypes, members, enums, inheritance/realization, composition/association, cardinality format, external stubs, opposite handling).
- [ ] Options per §6.9 with stated defaults; all CLI flags per §8 wired to them.
- [ ] Diagnostics routing per §8: library returns diagnostics; CLI prints them to stdout as `%%`-prefixed Mermaid comment lines a single regex can strip; the `-o` file stays clean.
- [ ] Deterministic output per §7.
- [ ] Java release and EMF version are Gradle properties (defaults 17 / 2.41.0, toolchain JDK 21); changing them needs no source edits; standalone EMF from Maven Central; runs without Eclipse.
- [ ] Sample model + the five tests pass.
- [ ] No instance-model code, interfaces, or placeholders; transform/emit/IR seam in place.
