package io.gdcc.spi.core.loader;

import io.gdcc.spi.core.compiler.LoaderTestEnvironment;
import io.gdcc.spi.meta.processor.PluginContractProcessor;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PluginLoaderIntegrationTest {

    @Test
    void rejectsPluginCompiledAgainstNewerBaseApiLevel() throws Exception {
        // Given
        
        String baseContract = """
                package test.spi;

                import io.gdcc.spi.meta.plugin.Plugin;
                import io.gdcc.spi.meta.annotations.PluginContract;

                @PluginContract(role = PluginContract.Role.BASE)
                public interface TestPlugin extends Plugin {
                    int API_LEVEL = %s;
                }
                """;
        
        LoaderTestEnvironment env = LoaderTestEnvironment.builder()
            .addCoreSource(
                "test/spi/TestPlugin.java",
                baseContract.formatted(1)
            )
            .addPluginSource(
                "test/spi/TestPlugin.java",
                baseContract.formatted(2)
            )
            .addPluginSource(
                "test/plugins/NewerPlugin.java",
                """
                package test.plugins;

                import io.gdcc.spi.meta.annotations.DataversePlugin;
                import test.spi.TestPlugin;

                @DataversePlugin
                public class NewerPlugin implements TestPlugin {
                    @Override
                    public String identity() {
                        return "newer";
                    }
                }
                """
            )
            .addPluginProcessor(new PluginContractProcessor())
            .packagePluginAsJar(false)
            .build();

        Class<?> pluginContractClass = env.coreClassLoader().loadClass("test.spi.TestPlugin");

        @SuppressWarnings("unchecked")
        Class<io.gdcc.spi.meta.plugin.Plugin> typedContract =
            (Class<io.gdcc.spi.meta.plugin.Plugin>) pluginContractClass;

        PluginLoader<?> loader = new PluginLoader<>(typedContract, env.coreClassLoader());
        Path pluginLocation = Path.of(env.pluginArtifact().toString());

        // When + Then
        var ex = assertThrows(LoaderException.class, () -> loader.load(pluginLocation));
        assertEquals(1, ex.getProblems().size());
        assertInstanceOf(LoaderProblem.PluginClassApiLevelMismatch.class, ex.getProblems().get(0));
    }
}
