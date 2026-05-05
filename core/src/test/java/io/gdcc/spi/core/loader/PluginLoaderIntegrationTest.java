package io.gdcc.spi.core.loader;

import io.gdcc.spi.core.compiler.LoaderTestEnvironment;
import io.gdcc.spi.meta.plugin.Plugin;
import io.gdcc.spi.meta.processor.PluginContractProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

class PluginLoaderIntegrationTest {
    
    final String contractPackage = "test.spi";
    final String contractClass = "TestPlugin";
    
    final String pluginPackage = "test.plugins";
    
    final String baseContractClassFile = contractPackage.replace(".", "/") + "/" + contractClass + ".java";
    final String baseContractCode = """
                package %s;

                import io.gdcc.spi.meta.plugin.Plugin;
                import io.gdcc.spi.meta.annotations.PluginContract;

                @PluginContract(role = PluginContract.Role.BASE)
                public interface %s extends Plugin {
                    int API_LEVEL = %s;
                }
                """;
    
    final String pluginCodeTemplate = """
                package %s;

                import io.gdcc.spi.meta.annotations.DataversePlugin;
                import %s.%s;

                @DataversePlugin
                public class %s implements %s {
                    @Override
                    public String identity() {
                        return "test";
                    }
                }
                """;
    
    final String simplePluginClass = "SimplePlugin";
    final String simplePluginClassFile = pluginPackage.replace(".", "/") + "/" + simplePluginClass +  ".java";
    final String simplePluginCode = pluginCodeTemplate.formatted(pluginPackage, contractPackage, contractClass, simplePluginClass, contractClass);
    
    @ParameterizedTest(name = "API levels: core={0}, plugin={1}")
    @CsvSource({"1,2","2,1"})
    void rejectsPluginCompiledAgainstDifferentBaseApiLevel(int coreLevel, int pluginLevel) throws Exception {
        // Given
        LoaderTestEnvironment env = LoaderTestEnvironment.builder()
            .addCoreSource(
                baseContractClassFile,
                baseContractCode.formatted(contractPackage, contractClass, coreLevel)
            )
            .addPluginSource(
                baseContractClassFile,
                baseContractCode.formatted(contractPackage, contractClass, pluginLevel)
            )
            .addPluginSource(
                simplePluginClassFile,
                simplePluginCode
            )
            .addPluginProcessor(new PluginContractProcessor())
            .packagePluginAsJar(false)
            .build();

        Class<?> pluginContractClass = env.coreClassLoader().loadClass(contractPackage + "." + contractClass);
        @SuppressWarnings("unchecked")
        Class<Plugin> typedContract = (Class<Plugin>) pluginContractClass;

        PluginLoader<?> loader = new PluginLoader<>(typedContract, env.coreClassLoader());
        Path pluginLocation = Path.of(env.pluginArtifact().toString());

        // When + Then
        var ex = assertThrows(LoaderException.class, () -> loader.load(pluginLocation));
        assertEquals(1, ex.getProblems().size());
        assertInstanceOf(LoaderProblem.PluginClassApiLevelMismatch.class, ex.getProblems().get(0));
    }
    
    @ParameterizedTest(name = "Packaging as JAR: {0}")
    @ValueSource(booleans = {true, false})
    void acceptsPluginCompiledAgainstSameBaseApiLevel(boolean packageAsJar) throws Exception {
        // Given
        int apiLevel = 5;
        
        LoaderTestEnvironment env = LoaderTestEnvironment.builder()
            .addCoreSource(
                baseContractClassFile,
                baseContractCode.formatted(contractPackage, contractClass, apiLevel)
            )
            .addPluginSource(
                baseContractClassFile,
                baseContractCode.formatted(contractPackage, contractClass, apiLevel)
            )
            .addPluginSource(
                simplePluginClassFile,
                simplePluginCode
            )
            .addPluginProcessor(new PluginContractProcessor())
            .packagePluginAsJar(packageAsJar)
            .build();
        
        Class<?> pluginContractClass = env.coreClassLoader().loadClass(contractPackage + "." + contractClass);
        @SuppressWarnings("unchecked")
        Class<Plugin> typedContract = (Class<Plugin>) pluginContractClass;
        
        PluginLoader<?> loader = new PluginLoader<>(typedContract, env.coreClassLoader());
        Path pluginLocation = Path.of(env.pluginArtifact().toString());
        
        try {
            // When
            var plugins = loader.load(pluginLocation);
            
            // Then
            assertEquals(1, plugins.size());
            assertEquals(pluginPackage + "." + simplePluginClass, plugins.get(0).plugin().getClass().getName());
        } catch (LoaderException e) {
            fail("Loader problems detected:\n" + e.getProblems().stream().map(LoaderProblem::message).collect(Collectors.joining(",\n")), e);
        }
    }
}
