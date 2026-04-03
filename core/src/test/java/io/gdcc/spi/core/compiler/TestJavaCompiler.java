package io.gdcc.spi.core.compiler;

import javax.annotation.processing.Processor;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A utility class for testing Java compilation using the system Java compiler.
 * Provides functionality to configure custom compilation settings
 * and compile a set of source files.
 * The compiled files are stored in temporary directories during execution.
 * This class is immutable and supports configuration through its builder.
 */
public final class TestJavaCompiler {

    private final String release;
    private final List<Path> classpathEntries;
    private final List<Processor> processors;
    private final boolean inheritRuntimeClasspath;

    private TestJavaCompiler(Builder builder) {
        this.release = builder.release;
        this.classpathEntries = List.copyOf(builder.classpathEntries);
        this.processors = List.copyOf(builder.processors);
        this.inheritRuntimeClasspath = builder.inheritRuntimeClasspath;
    }

    public static Builder builder() {
        return new Builder();
    }

    public TestCompilation compile(List<SourceFile> sources) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException(
                "No system Java compiler available. Are tests running on a JRE instead of a JDK?"
            );
        }

        Path tempDir = Files.createTempDirectory("test-java-compiler");
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

        try (StandardJavaFileManager fileManager =
                 compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {

            Iterable<? extends JavaFileObject> compilationUnits =
                fileManager.getJavaFileObjectsFromPaths(sourcePaths);

            List<String> options = new ArrayList<>();
            options.add("--release");
            options.add(release);
            options.add("-d");
            options.add(classOutputDir.toString());

            String classpath = buildClasspath();
            if (!classpath.isBlank()) {
                options.add("-classpath");
                options.add(classpath);
            }

            JavaCompiler.CompilationTask task = compiler.getTask(
                null,
                fileManager,
                diagnostics,
                options,
                null,
                compilationUnits
            );

            if (!processors.isEmpty()) {
                task.setProcessors(processors);
            }

            boolean success = Boolean.TRUE.equals(task.call());

            return new TestCompilation(
                success,
                tempDir,
                sourceDir,
                classOutputDir,
                List.copyOf(diagnostics.getDiagnostics())
            );
        }
    }

    private String buildClasspath() {
        List<String> entries = new ArrayList<>();

        if (inheritRuntimeClasspath) {
            String runtimeClasspath = System.getProperty("java.class.path", "");
            if (!runtimeClasspath.isBlank()) {
                entries.add(runtimeClasspath);
            }
        }

        for (Path classpathEntry : classpathEntries) {
            entries.add(classpathEntry.toString());
        }

        return String.join(File.pathSeparator, entries);
    }

    public static final class Builder {
        private String release = "17";
        private final List<Path> classpathEntries = new ArrayList<>();
        private final List<Processor> processors = new ArrayList<>();
        private boolean inheritRuntimeClasspath = true;

        private Builder() {
        }

        public Builder withRelease(String release) {
            this.release = release;
            return this;
        }

        public Builder withClasspathEntry(Path path) {
            this.classpathEntries.add(path);
            return this;
        }

        public Builder withClasspathEntries(List<Path> paths) {
            this.classpathEntries.addAll(paths);
            return this;
        }

        public Builder withProcessor(Processor processor) {
            this.processors.add(processor);
            return this;
        }

        public Builder withProcessors(List<? extends Processor> processors) {
            this.processors.addAll(processors);
            return this;
        }

        public Builder withInheritRuntimeClasspath(boolean inheritRuntimeClasspath) {
            this.inheritRuntimeClasspath = inheritRuntimeClasspath;
            return this;
        }

        public TestJavaCompiler build() {
            return new TestJavaCompiler(this);
        }
    }

    public record SourceFile(String relativePath, String content) {
        public static SourceFile of(String relativePath, String content) {
            return new SourceFile(relativePath, content);
        }
    }
}
