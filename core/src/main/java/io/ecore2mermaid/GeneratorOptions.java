package io.ecore2mermaid;

public final class GeneratorOptions {
    private final String direction;
    private final boolean showMultiplicity;
    private final boolean includeOperations;
    private final boolean collapseOpposites;
    private final boolean useFullyQualifiedNames;
    private final boolean realizeInterfaces;
    private final boolean includeExternalStubs;
    private final String theme;

    private GeneratorOptions(Builder b) {
        this.direction = b.direction;
        this.showMultiplicity = b.showMultiplicity;
        this.includeOperations = b.includeOperations;
        this.collapseOpposites = b.collapseOpposites;
        this.useFullyQualifiedNames = b.useFullyQualifiedNames;
        this.realizeInterfaces = b.realizeInterfaces;
        this.includeExternalStubs = b.includeExternalStubs;
        this.theme = b.theme;
    }

    public String direction()               { return direction; }
    public boolean showMultiplicity()        { return showMultiplicity; }
    public boolean includeOperations()       { return includeOperations; }
    public boolean collapseOpposites()       { return collapseOpposites; }
    public boolean useFullyQualifiedNames()  { return useFullyQualifiedNames; }
    public boolean realizeInterfaces()       { return realizeInterfaces; }
    public boolean includeExternalStubs()    { return includeExternalStubs; }
    public String theme()                    { return theme; }

    public static Builder builder() { return new Builder(); }
    public static GeneratorOptions defaults() { return builder().build(); }

    public static final class Builder {
        private String direction = "TB";
        private boolean showMultiplicity = true;
        private boolean includeOperations = true;
        private boolean collapseOpposites = false;
        private boolean useFullyQualifiedNames = false;
        private boolean realizeInterfaces = false;
        private boolean includeExternalStubs = true;
        private String theme = null;

        public Builder direction(String v)              { direction = v; return this; }
        public Builder showMultiplicity(boolean v)       { showMultiplicity = v; return this; }
        public Builder includeOperations(boolean v)      { includeOperations = v; return this; }
        public Builder collapseOpposites(boolean v)      { collapseOpposites = v; return this; }
        public Builder useFullyQualifiedNames(boolean v) { useFullyQualifiedNames = v; return this; }
        public Builder realizeInterfaces(boolean v)      { realizeInterfaces = v; return this; }
        public Builder includeExternalStubs(boolean v)   { includeExternalStubs = v; return this; }
        public Builder theme(String v)                   { theme = v; return this; }

        public GeneratorOptions build() { return new GeneratorOptions(this); }
    }
}
