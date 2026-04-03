package io.gdcc.spi.core.compiler;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

/**
 * An immutable class that encapsulates the result of a Java source code compilation process.
 * Provides access to details such as success status, output directories, diagnostics, and utility
 * methods for handling compilation results.
 */
public final class TestCompilation {

    private final boolean success;
    private final Path rootDir;
    private final Path sourceDir;
    private final Path classOutputDir;
    private final List<Diagnostic<? extends JavaFileObject>> diagnostics;

    TestCompilation(
        boolean success,
        Path rootDir,
        Path sourceDir,
        Path classOutputDir,
        List<Diagnostic<? extends JavaFileObject>> diagnostics
    ) {
        this.success = success;
        this.rootDir = rootDir;
        this.sourceDir = sourceDir;
        this.classOutputDir = classOutputDir;
        this.diagnostics = diagnostics;
    }

    public boolean success() {
        return success;
    }

    public Path rootDir() {
        return rootDir;
    }

    public Path sourceDir() {
        return sourceDir;
    }

    public Path classOutputDir() {
        return classOutputDir;
    }

    public List<Diagnostic<? extends JavaFileObject>> diagnostics() {
        return diagnostics;
    }

    public String diagnosticsAsText() {
        return diagnostics.stream()
            .map(diagnostic -> diagnostic.getKind() + ": " + diagnostic.getMessage(null))
            .reduce("", (left, right) -> left + right + System.lineSeparator());
    }

    public Path generatedFile(String relativePath) {
        return classOutputDir.resolve(relativePath);
    }

    public URL[] classpathUrls() {
        try {
            return new URL[]{classOutputDir.toUri().toURL()};
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public URLClassLoader newClassLoader(ClassLoader parent) {
        return URLClassLoader.newInstance(classpathUrls(), parent);
    }

    public Path createJar(String fileName) throws IOException {
        Path jarPath = rootDir.resolve(fileName);

        try (JarOutputStream jarOut = new JarOutputStream(Files.newOutputStream(jarPath));
             Stream<Path> stream = Files.walk(classOutputDir)) {

            for (Path path : stream.filter(Files::isRegularFile).toList()) {
                String entryName = classOutputDir.relativize(path).toString().replace('\\', '/');
                jarOut.putNextEntry(new JarEntry(entryName));
                Files.copy(path, jarOut);
                jarOut.closeEntry();
            }
        }

        return jarPath;
    }

    public void assertSuccess() {
        if (!success) {
            throw new IllegalStateException("Compilation failed:\n" + diagnosticsAsText());
        }
    }
}
