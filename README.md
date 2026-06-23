# ecore2mermaid

Converts an [Eclipse EMF](https://eclipse.dev/modeling/emf/) Ecore metamodel into a [Mermaid](https://mermaid.js.org/) `classDiagram`. Runs standalone — no Eclipse installation or OSGi runtime required.

## Requirements

- Java 17 or later (to run)
- JDK 21 (to build) — set via `buildJdkVersion` in `gradle.properties`

## Building

```sh
./gradlew :core:jar          # library jar
./gradlew :cli:shadowJar     # runnable fat jar → cli/build/libs/ecore2mermaid-*.jar
./gradlew test               # run all tests
```

Gradle properties with their defaults — override with `-P<key>=<value>`:

| Property | Default | Effect |
|---|---|---|
| `javaRelease` | `17` | Source/target compatibility of produced artifacts |
| `emfVersion` | `2.40.0` | Version applied to all `org.eclipse.emf:*` dependencies |
| `buildJdkVersion` | `21` | JDK toolchain used during compilation |

## CLI usage

```
java -jar ecore2mermaid-<version>.jar [options] <input.ecore>
```

| Flag | Default | Description |
|---|---|---|
| `-i, --input <path>` | — | Input `.ecore` file (required; also accepted as a positional argument) |
| `-o, --output <file>` | stdout | Write diagram to a file instead of stdout |
| `--direction <TB\|LR>` | `TB` | Diagram layout direction |
| `--hide-multiplicity` | off | Hide all cardinality annotations |
| `--no-operations` | off | Exclude EOperations from the diagram |
| `--full-names` | off | Use fully-qualified class names as node identifiers |
| `--collapse-opposites` | off | Render bidirectional reference pairs as a single edge |
| `--realize-interfaces` | off | Use dashed realization arrows (`<|..`) for interface supertypes |
| `--no-external` | off | Skip edges to types outside the input scope instead of emitting stub nodes |
| `--theme <name>` | — | Prepend a Mermaid `%%{init}%%` theme line |
| `-h, --help` | — | Show usage |
| `--version` | — | Show version |

### Output routing

- **Diagram** — written to stdout by default; to a file when `-o` is given.
- **Diagnostics** — always written to stdout, appended after the diagram as Mermaid comment lines:
  ```
  %% [ecore2mermaid] WARN [E2M_EXTERNAL_REF] Type 'Foo' resolved outside input scope; emitted as external stub
  ```
  Strip them when needed:
  ```sh
  java -jar ecore2mermaid.jar model.ecore | grep -v '^%% \[ecore2mermaid\] '
  ```
  When `-o` is given the file contains only the diagram; diagnostics still go to stdout.

### Example

Given the bundled `library.ecore` sample model:

```sh
java -jar ecore2mermaid.jar library.ecore
```

```
classDiagram
direction TB
class BookCategory {
    <<enumeration>>
    FICTION
    NONFICTION
    REFERENCE
}
class Identifiable {
    <<interface>>
    +id : EString
}
class Item {
    <<abstract>>
}
class Book {
    +title : EString
    +isbn : ISBN@java.lang.String
    +tags : EString [0..*]
    +category : BookCategory
    +matches(query : EString) : EBoolean
}
class Author {
    +name : EString
}
class Library {
}
Item <|-- Book
Identifiable <|-- Book
Book "0..*" --> "1..*" Author : authors
Author "1..*" --> "0..*" Book : books
Library *-- "0..*" Book : books
Library *-- "0..*" Author : authors
```

Paste the output into [mermaid.live](https://mermaid.live) to render it.

## Library usage

Add `core.jar` to your classpath alongside the EMF standalone jars.

### One-call façade

```java
import com.folmate.ecore2mermaid.*;

MermaidResult result = Ecore2Mermaid.fromPath(
    Path.of("model.ecore"),
    GeneratorOptions.defaults()
);
System.out.println(result.diagram());
result.diagnostics().forEach(System.err::println);
```

### From an in-memory EPackage

```java
MermaidResult result = Ecore2Mermaid.fromPackages(
    List.of(MyPackage.eINSTANCE),
    GeneratorOptions.builder()
        .collapseOpposites(true)
        .includeOperations(false)
        .build()
);
```

### Generator options

```java
GeneratorOptions opts = GeneratorOptions.builder()
    .direction("LR")             // "TB" (default) or "LR"
    .showMultiplicity(true)      // default true
    .includeOperations(true)     // default true
    .collapseOpposites(false)    // default false
    .useFullyQualifiedNames(false)
    .realizeInterfaces(false)
    .includeExternalStubs(true)
    .theme(null)                 // e.g. "forest"
    .build();
```

### Result type

```java
record MermaidResult(String diagram, List<Diagnostic> diagnostics) {}
record Diagnostic(Severity severity, String code, String message) {}
enum Severity { INFO, WARN, ERROR }
```

`diagram` is the complete Mermaid source string. Hard failures (file not found, unreadable, no EPackage found) throw `EcoreLoadException`; recoverable issues (unresolved cross-references, name collisions) are returned as diagnostics.

## Mapping rules

| Ecore construct | Mermaid output |
|---|---|
| `EClass` | `class` node; `<<abstract>>` or `<<interface>>` stereotype when applicable |
| `EAttribute` | `+name : Type` member; `[l..u]` multiplicity appended (except `[0..1]`) |
| `EOperation` | `+name(p : T) : ReturnType` member |
| `EEnum` | `class` node with `<<enumeration>>`; each literal as a plain member line |
| `eSuperTypes` | `Super <|-- Sub` (inheritance) or `Super <|.. Sub` (realization, with `--realize-interfaces`) |
| Containment `EReference` | `Owner *-- Target : refName` |
| Non-containment `EReference` | `Owner --> Target : refName` |
| Out-of-scope target type | Stub node annotated `<<external>>` + `WARN` diagnostic |

Derived, transient, and volatile structural features are always omitted. Attribute default values and member visibility are not rendered.

## Architecture

```
Path ──EcoreLoader──▶ List<EPackage>
                         │
        EcoreToClassDiagramTransformer  (EPackage… → ClassDiagram IR)
                         │
        MermaidClassDiagramEmitter      (ClassDiagram IR → String)
                         │
                    MermaidResult
```

The core library never touches `EPackage.Registry.INSTANCE` or any other global EMF state. Each `EcoreLoader.load()` call creates a private `ResourceSet`.

## License

ecore2mermaid is released under the [Apache License 2.0](LICENSE).

This product bundles third-party software; see `META-INF/NOTICE` inside the distribution jar for details.
