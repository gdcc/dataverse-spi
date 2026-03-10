package io.gdcc.spi.meta.processor;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A utility class for compiling Java source files during tests, leveraging an in-memory
 * approach to simulate compilation and validation of Java code. This is typically used
 * in scenarios where processor-based code validation is needed.
 *
 * This class uses the Java Compiler API to compile source files provided as input and
 * returns a result encapsulating success state, diagnostics information, and the path
 * to generated class files.
 */
final class ProcessorTestCompiler {

    CompilationResult compile(List<SourceFile> sources) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("No system Java compiler available. Are tests running on a JRE instead of a JDK?");
        }

        Path tempDir = Files.createTempDirectory("plugin-contract-processor-test");
        Path sourceDir = tempDir.resolve("src");
        Path classOutputDir = tempDir.resolve("classes");
        Files.createDirectories(sourceDir);
        Files.createDirectories(classOutputDir);

        List<Path> sourcePaths = new ArrayList<>();
        for (SourceFile source : sources) {
            Path file = sourceDir.resolve(source.relativePath());
            Files.createDirectories(file.getParent());
            Files.writeString(file, source.content(), StandardCharsets.UTF_8);
            sourcePaths.add(file);
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> compilationUnits =
                fileManager.getJavaFileObjectsFromPaths(sourcePaths);

            List<String> options = List.of(
                "--release", "17",
                "-classpath", System.getProperty("java.class.path"),
                "-d", classOutputDir.toString()
            );

            JavaCompiler.CompilationTask task = compiler.getTask(
                null,
                fileManager,
                diagnostics,
                options,
                null,
                compilationUnits
            );

            task.setProcessors(List.of(new PluginContractProcessor()));

            boolean success = task.call();
            return new CompilationResult(success, List.copyOf(diagnostics.getDiagnostics()), classOutputDir);
        }
    }

    record SourceFile(String relativePath, String content) {
    }

    record CompilationResult(
        boolean success,
        List<Diagnostic<? extends JavaFileObject>> diagnostics,
        Path classOutputDir
    ) {
        String diagnosticsAsText() {
            return diagnostics.stream()
                .map(diagnostic -> diagnostic.getKind() + ": " + diagnostic.getMessage(null))
                .reduce("", (left, right) -> left + right + System.lineSeparator());
        }

        Path generatedFile(String relativePath) {
            return classOutputDir.resolve(relativePath);
        }
    }
}
