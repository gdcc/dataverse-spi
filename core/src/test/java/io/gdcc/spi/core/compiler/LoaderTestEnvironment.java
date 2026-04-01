package io.gdcc.spi.core.compiler;

import javax.annotation.processing.Processor;
import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a test environment for managing the compilation and class loading
 * of core and plugin components. This environment facilitates scenarios where
 * Java code must be compiled, loaded, and tested dynamically.
 *
 * The `LoaderTestEnvironment` class is immutable and provides access to the core
 * and plugin compilations, their respective outputs, and class loaders. It is
 * built using the companion `Builder` class.
 */
public final class LoaderTestEnvironment {

    private final TestCompilation coreCompilation;
    private final TestCompilation pluginCompilation;
    private final Path pluginArtifact;
    private final URLClassLoader coreClassLoader;

    private LoaderTestEnvironment(
        TestCompilation coreCompilation,
        TestCompilation pluginCompilation,
        Path pluginArtifact,
        URLClassLoader coreClassLoader
    ) {
        this.coreCompilation = coreCompilation;
        this.pluginCompilation = pluginCompilation;
        this.pluginArtifact = pluginArtifact;
        this.coreClassLoader = coreClassLoader;
    }

    public static Builder builder() {
        return new Builder();
    }

    public TestCompilation coreCompilation() {
        return coreCompilation;
    }

    public TestCompilation pluginCompilation() {
        return pluginCompilation;
    }

    public Path pluginArtifact() {
        return pluginArtifact;
    }

    public URLClassLoader coreClassLoader() {
        return coreClassLoader;
    }

    public Path pluginClassesDirectory() {
        return pluginCompilation.classOutputDir();
    }

    public static final class Builder {
        private final List<TestJavaCompiler.SourceFile> coreSources = new ArrayList<>();
        private final List<TestJavaCompiler.SourceFile> pluginSources = new ArrayList<>();
        private final List<Processor> pluginProcessors = new ArrayList<>();

        private String release = "17";
        private boolean packagePluginAsJar = false;
        private String pluginJarName = "plugin-under-test.jar";

        private Builder() {
        }

        public Builder withRelease(String release) {
            this.release = release;
            return this;
        }

        public Builder addCoreSource(String relativePath, String content) {
            this.coreSources.add(TestJavaCompiler.SourceFile.of(relativePath, content));
            return this;
        }

        public Builder addPluginSource(String relativePath, String content) {
            this.pluginSources.add(TestJavaCompiler.SourceFile.of(relativePath, content));
            return this;
        }

        public Builder addPluginProcessor(Processor processor) {
            this.pluginProcessors.add(processor);
            return this;
        }

        public Builder packagePluginAsJar(boolean packagePluginAsJar) {
            this.packagePluginAsJar = packagePluginAsJar;
            return this;
        }

        public Builder withPluginJarName(String pluginJarName) {
            this.pluginJarName = pluginJarName;
            return this;
        }

        public LoaderTestEnvironment build() throws IOException {
            TestCompilation coreCompilation = TestJavaCompiler.builder()
                .withRelease(release)
                .build()
                .compile(coreSources);

            coreCompilation.assertSuccess();

            URLClassLoader coreClassLoader =
                coreCompilation.newClassLoader(Thread.currentThread().getContextClassLoader());

            TestJavaCompiler.Builder pluginCompilerBuilder = TestJavaCompiler.builder()
                .withRelease(release)
                .withClasspathEntry(coreCompilation.classOutputDir());

            if (!pluginProcessors.isEmpty()) {
                pluginCompilerBuilder.withProcessors(pluginProcessors);
            }

            TestCompilation pluginCompilation = pluginCompilerBuilder
                .build()
                .compile(pluginSources);

            pluginCompilation.assertSuccess();

            Path pluginArtifact = packagePluginAsJar
                ? pluginCompilation.createJar(pluginJarName)
                : pluginCompilation.classOutputDir();

            return new LoaderTestEnvironment(
                coreCompilation,
                pluginCompilation,
                pluginArtifact,
                coreClassLoader
            );
        }
    }
}
