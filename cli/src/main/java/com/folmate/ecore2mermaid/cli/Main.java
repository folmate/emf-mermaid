package com.folmate.ecore2mermaid.cli;

import com.beust.jcommander.ParameterException;
import com.folmate.ecore2mermaid.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Main {

    static final String DIAG_PREFIX = "%% [ecore2mermaid] ";
    private static final String VERSION = "0.0.1";

    public static void main(String[] args) {
        CliArgs cli;
        try {
            cli = CliArgs.parse(args);
        } catch (ParameterException e) {
            System.err.println("ERROR: " + e.getMessage());
            System.exit(1);
            return;
        }

        if (cli.isHelp()) {
            cli.printUsage();
            System.out.println();
            System.out.println("Diagnostic lines are written to stdout prefixed with:");
            System.out.println("  " + DIAG_PREFIX + "<SEVERITY> [<CODE>] <message>");
            System.out.println("Strip them with: grep -v '^" + DIAG_PREFIX + "'");
            return;
        }
        if (cli.isVersion()) {
            System.out.println("ecore2mermaid " + VERSION);
            return;
        }

        System.exit(run(cli));
    }

    private static int run(CliArgs cli) {
        if (cli.inputPath() == null) {
            System.err.println("ERROR: No input file specified. Use -i <path> or pass as positional argument.");
            return 1;
        }

        MermaidResult result;
        try {
            result = Ecore2Mermaid.fromPath(Paths.get(cli.inputPath()), cli.toGeneratorOptions());
        } catch (EcoreLoadException e) {
            System.err.println("ERROR: " + e.getMessage());
            return 1;
        }

        List<String> diagLines = result.diagnostics().stream()
                .map(d -> DIAG_PREFIX + d.severity() + " [" + d.code() + "] " + d.message())
                .toList();

        if (cli.outputPath() != null) {
            try {
                Files.writeString(Path.of(cli.outputPath()), result.diagram(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                System.err.println("ERROR: Cannot write to output file: " + e.getMessage());
                return 1;
            }
            diagLines.forEach(System.out::println);
        } else {
            System.out.print(result.diagram());
            diagLines.forEach(System.out::println);
        }

        return 0;
    }
}
