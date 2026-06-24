package io.github.folmate.ecore2mermaid.cli;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import io.github.folmate.ecore2mermaid.core.GeneratorOptions;

import java.util.List;

public final class CliArgs {

    @Parameter(names = {"-i", "--input"}, description = "Input .ecore file")
    private String input;

    @Parameter(description = "Input .ecore file (positional)")
    private List<String> positional;

    @Parameter(names = {"-o", "--output"}, description = "Write diagram to file (default: stdout)")
    private String output;

    @Parameter(names = {"--direction"}, description = "Diagram direction: TB or LR")
    private String direction = "TB";

    @Parameter(names = {"--hide-multiplicity"}, description = "Hide cardinality annotations")
    private boolean hideMultiplicity;

    @Parameter(names = {"--no-operations"}, description = "Exclude EOperations")
    private boolean noOperations;

    @Parameter(names = {"--full-names"}, description = "Use fully-qualified class names")
    private boolean fullNames;

    @Parameter(names = {"--collapse-opposites"}, description = "Collapse opposite reference pairs into one edge")
    private boolean collapseOpposites;

    @Parameter(names = {"--realize-interfaces"}, description = "Use realization arrows for interface supertypes")
    private boolean realizeInterfaces;

    @Parameter(names = {"--no-external"}, description = "Skip edges to out-of-scope types instead of emitting stubs")
    private boolean noExternal;

    @Parameter(names = {"--theme"}, description = "Mermaid theme for init line")
    private String theme;

    @Parameter(names = {"-h", "--help"}, help = true, description = "Show usage")
    private boolean help;

    @Parameter(names = {"--version"}, description = "Show version")
    private boolean version;

    private CliArgs() {}

    /**
     * Parse {@code args} and return a configured instance.
     *
     * @throws ParameterException if unrecognised or invalid arguments are supplied
     */
    public static CliArgs parse(String[] args) throws ParameterException {
        CliArgs cli = new CliArgs();
        jcommander(cli).parse(args);
        return cli;
    }

    public void printUsage() { jcommander(this).usage(); }

    private static JCommander jcommander(CliArgs target) {
        return JCommander.newBuilder()
                .addObject(target)
                .programName("ecore2mermaid")
                .build();
    }

    public boolean isHelp()    { return help; }
    public boolean isVersion() { return version; }

    public String inputPath() {
        if (input != null) return input;
        if (positional != null && !positional.isEmpty()) return positional.get(0);
        return null;
    }

    public String outputPath() { return output; }

    public GeneratorOptions toGeneratorOptions() {
        return GeneratorOptions.builder()
                .direction(direction)
                .showMultiplicity(!hideMultiplicity)
                .includeOperations(!noOperations)
                .collapseOpposites(collapseOpposites)
                .useFullyQualifiedNames(fullNames)
                .realizeInterfaces(realizeInterfaces)
                .includeExternalStubs(!noExternal)
                .theme(theme)
                .build();
    }
}
