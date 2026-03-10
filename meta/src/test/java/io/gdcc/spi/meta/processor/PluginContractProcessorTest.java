package io.gdcc.spi.meta.processor;

import io.gdcc.spi.meta.annotations.PluginContract;
import io.gdcc.spi.meta.annotations.DataversePlugin;
import io.gdcc.spi.meta.annotations.RequiredProvider;
import io.gdcc.spi.meta.plugin.CoreProvider;
import io.gdcc.spi.meta.plugin.Plugin;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.tools.Diagnostic;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PluginContractProcessorTest {
    
    private final ProcessorTestCompiler compiler = new ProcessorTestCompiler();
    
    @Nested
    class Basics {
        @Test
        void generatesDescriptorAndServiceFileForValidPlugin() throws IOException {
            ProcessorTestCompiler.CompilationResult result = compiler.compile(List.of(
                source(
                    "test/TestProvider.java",
                    """
                        package test;
                    
                        import %s;
                    
                        public interface TestProvider extends CoreProvider {
                            int API_LEVEL = 7;
                        }
                        """.formatted(CoreProvider.class.getCanonicalName())
                ),
                source(
                    "test/TestPlugin.java",
                    """
                        package test;
                    
                        import %s;
                        import %s;
                        import %s;
                        import %s;
                    
                        @PluginContract(
                            kind = PluginContract.Kind.BASE,
                            providers = { @RequiredProvider(TestProvider.class) }
                        )
                        public interface TestPlugin extends Plugin {
                            int API_LEVEL = 3;
                        }
                        """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName(),
                        RequiredProvider.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                ),
                source(
                    "test/GoodPlugin.java",
                    """
                        package test;
                    
                        import %s;
                    
                        @DataversePlugin
                        public class GoodPlugin implements TestPlugin {
                            @Override
                            public String identity() {
                                return "good";
                            }
                        }
                        """.formatted(DataversePlugin.class.getCanonicalName())
                )
            ));
            
            assertTrue(result.success(), result.diagnosticsAsText());
            
            String descriptorPath = "META-INF/dataverse/plugins/test_GoodPlugin.properties";
            String servicePath = "META-INF/services/test.TestPlugin";
            
            assertTrue(Files.exists(result.generatedFile(descriptorPath)), "Descriptor should be generated");
            assertTrue(Files.exists(result.generatedFile(servicePath)), "Service file should be generated");
            
            String descriptor = Files.readString(result.generatedFile(descriptorPath));
            assertTrue(descriptor.contains("plugin.class=test.GoodPlugin"));
            assertTrue(descriptor.contains("plugin.kind=test.TestPlugin"));
            assertTrue(descriptor.contains("plugin.test.TestPlugin.level=3"));
            assertTrue(descriptor.contains("plugin.requires.test.TestProvider.level=7"));
            
            String serviceFile = Files.readString(result.generatedFile(servicePath));
            assertEquals("test.GoodPlugin", serviceFile.trim());
        }
        
        @Test
        void failsWhenMultipleBaseContractsAreImplemented() throws IOException {
            ProcessorTestCompiler.CompilationResult result = compiler.compile(List.of(
                source(
                    "test/BasePluginA.java",
                    """
                        package test;
                    
                        import %s;
                        import %s;
                    
                        @PluginContract(kind = PluginContract.Kind.BASE)
                        public interface BasePluginA extends Plugin {
                            int API_LEVEL = 1;
                        }
                        """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                ),
                source(
                    "test/BasePluginB.java",
                    """
                        package test;
                    
                        import %s;
                        import %s;
                    
                        @PluginContract(kind = PluginContract.Kind.BASE)
                        public interface BasePluginB extends Plugin {
                            int API_LEVEL = 1;
                        }
                        """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                ),
                source(
                    "test/BadPlugin.java",
                    """
                        package test;
                    
                        import %s;
                    
                        @DataversePlugin
                        public class BadPlugin implements BasePluginA, BasePluginB {
                            @Override
                            public String identity() {
                                return "bad";
                            }
                        }
                        """.formatted(DataversePlugin.class.getCanonicalName())
                )
            ));
            
            assertFalse(result.success(), "Compilation should fail");
            assertDiagnosticContains(result, Diagnostic.Kind.ERROR, "multiple base plugin contracts");
        }
        
        @Test
        void failsWhenRequiredContractIsMissing() throws IOException {
            ProcessorTestCompiler.CompilationResult result = compiler.compile(List.of(
                source(
                    "test/BasePlugin.java",
                    """
                        package test;
                    
                        import %s;
                        import %s;
                    
                        @PluginContract(kind = PluginContract.Kind.BASE)
                        public interface BasePlugin extends Plugin {
                            int API_LEVEL = 1;
                        }
                        """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                ),
                source(
                    "test/CapabilityPlugin.java",
                    """
                        package test;
                    
                        import %s;
                        import %s;
                        import %s;
                    
                        @PluginContract(
                            kind = PluginContract.Kind.CAPABILITY,
                            requires = { BasePlugin.class }
                        )
                        public interface CapabilityPlugin extends Plugin {
                            int API_LEVEL = 1;
                        }
                        """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                ),
                source(
                    "test/MissingBasePluginImpl.java",
                    """
                        package test;
                    
                        import %s;
                    
                        @DataversePlugin
                        public class MissingBasePluginImpl implements CapabilityPlugin {
                            @Override
                            public String identity() {
                                return "missing-base";
                            }
                        }
                        """.formatted(DataversePlugin.class.getCanonicalName())
                )
            ));
            
            assertFalse(result.success(), "Compilation should fail");
            assertDiagnosticContains(result, Diagnostic.Kind.ERROR, "also requires contract test.BasePlugin");
        }
        
        @Test
        void suppressesGeneratedServiceFileWhenAutoServiceIsPresent() throws IOException {
            ProcessorTestCompiler.CompilationResult result = compiler.compile(List.of(
                source(
                    "com/google/auto/service/AutoService.java",
                    """
                        package com.google.auto.service;
                    
                        import java.lang.annotation.ElementType;
                        import java.lang.annotation.Retention;
                        import java.lang.annotation.RetentionPolicy;
                        import java.lang.annotation.Target;
                    
                        @Retention(RetentionPolicy.RUNTIME)
                        @Target(ElementType.TYPE)
                        public @interface AutoService {
                            Class<?>[] value();
                        }
                        """
                ),
                source(
                    "test/TestPlugin.java",
                    """
                        package test;
                    
                        import %s;
                        import %s;
                    
                        @PluginContract(kind = PluginContract.Kind.BASE)
                        public interface TestPlugin extends Plugin {
                            int API_LEVEL = 2;
                        }
                        """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                ),
                source(
                    "test/AutoServicePlugin.java",
                    """
                        package test;
                    
                        import com.google.auto.service.AutoService;
                        import %s;
                    
                        @DataversePlugin
                        @AutoService(TestPlugin.class)
                        public class AutoServicePlugin implements TestPlugin {
                            @Override
                            public String identity() {
                                return "auto";
                            }
                        }
                        """.formatted(DataversePlugin.class.getCanonicalName())
                )
            ));
            
            assertTrue(result.success(), result.diagnosticsAsText());
            
            String descriptorPath = "META-INF/dataverse/plugins/test_AutoServicePlugin.properties";
            String servicePath = "META-INF/services/test.TestPlugin";
            
            assertTrue(Files.exists(result.generatedFile(descriptorPath)), "Descriptor should still be generated");
            assertFalse(Files.exists(result.generatedFile(servicePath)), "Service file should be suppressed");
            
            assertDiagnosticContains(result, Diagnostic.Kind.WARNING, "@AutoService detected");
        }
        
        @Test
        void failsWhenImplementationIsNotPublic() throws IOException {
            ProcessorTestCompiler.CompilationResult result = compiler.compile(List.of(
                source(
                    "test/TestPlugin.java",
                    """
                        package test;
                    
                        import %s;
                        import %s;
                    
                        @PluginContract(kind = PluginContract.Kind.BASE)
                        public interface TestPlugin extends Plugin {
                            int API_LEVEL = 1;
                        }
                        """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                ),
                source(
                    "test/HiddenPlugin.java",
                    """
                        package test;
                    
                        import %s;
                    
                        @DataversePlugin
                        class HiddenPlugin implements TestPlugin {
                            @Override
                            public String identity() {
                                return "hidden";
                            }
                        }
                        """.formatted(DataversePlugin.class.getCanonicalName())
                )
            ));
            
            assertFalse(result.success(), "Compilation should fail");
            assertDiagnosticContains(result, Diagnostic.Kind.ERROR, "must be public");
        }
        
        @Test
        void failsWhenImplementationIsAbstract() throws IOException {
            ProcessorTestCompiler.CompilationResult result = compiler.compile(List.of(
                source(
                    "test/TestPlugin.java",
                    """
                        package test;
                    
                        import %s;
                        import %s;
                    
                        @PluginContract(kind = PluginContract.Kind.BASE)
                        public interface TestPlugin extends Plugin {
                            int API_LEVEL = 1;
                        }
                        """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                ),
                source(
                    "test/AbstractPluginImpl.java",
                    """
                        package test;
                    
                        import %s;
                    
                        @DataversePlugin
                        public abstract class AbstractPluginImpl implements TestPlugin {
                            @Override
                            public String identity() {
                                return "abstract";
                            }
                        }
                        """.formatted(DataversePlugin.class.getCanonicalName())
                )
            ));
            
            assertFalse(result.success(), "Compilation should fail");
            assertDiagnosticContains(result, Diagnostic.Kind.ERROR, "must not be abstract");
        }
        
        @Test
        void failsWhenNoBaseContractIsImplemented() throws IOException {
            ProcessorTestCompiler.CompilationResult result = compiler.compile(List.of(
                source(
                    "test/CapabilityPlugin.java",
                    """
                        package test;
                    
                        import %s;
                        import %s;
                    
                        @PluginContract(kind = PluginContract.Kind.CAPABILITY)
                        public interface CapabilityPlugin extends Plugin {
                            int API_LEVEL = 1;
                        }
                        """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                ),
                source(
                    "test/CapabilityOnlyImpl.java",
                    """
                        package test;
                    
                        import %s;
                    
                        @DataversePlugin
                        public class CapabilityOnlyImpl implements CapabilityPlugin {
                            @Override
                            public String identity() {
                                return "capability-only";
                            }
                        }
                        """.formatted(DataversePlugin.class.getCanonicalName())
                )
            ));
            
            assertFalse(result.success(), "Compilation should fail");
            assertDiagnosticContains(result, Diagnostic.Kind.ERROR, "exactly one base plugin contract");
        }
    }
    
    @Nested
    class EdgeCases {
        @Test
        void failsWhenContractApiLevelIsMissing() throws IOException {
            ProcessorTestCompiler.CompilationResult result = compiler.compile(List.of(
                source(
                    "test/MissingApiLevelPlugin.java",
                    """
                    package test;

                    import %s;
                    import %s;

                    @PluginContract(kind = PluginContract.Kind.BASE)
                    public interface MissingApiLevelPlugin extends Plugin {
                    }
                    """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                ),
                source(
                    "test/MissingApiLevelImpl.java",
                    """
                    package test;

                    import %s;

                    @DataversePlugin
                    public class MissingApiLevelImpl implements MissingApiLevelPlugin {
                        @Override
                        public String identity() {
                            return "missing-api-level";
                        }
                    }
                    """.formatted(DataversePlugin.class.getCanonicalName())
                )
            ));
            
            assertFalse(result.success(), "Compilation should fail");
            assertDiagnosticContains(result, Diagnostic.Kind.ERROR, "must declare int API_LEVEL");
        }
        
        @Test
        void failsWhenContractApiLevelIsNotCompileTimeConstant() throws IOException {
            ProcessorTestCompiler.CompilationResult result = compiler.compile(List.of(
                source(
                    "test/NonConstantApiLevelPlugin.java",
                    """
                    package test;

                    import %s;
                    import %s;

                    @PluginContract(kind = PluginContract.Kind.BASE)
                    public interface NonConstantApiLevelPlugin extends Plugin {
                        Integer API_LEVEL = Integer.valueOf(2);
                    }
                    """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                ),
                source(
                    "test/NonConstantApiLevelImpl.java",
                    """
                    package test;

                    import %s;

                    @DataversePlugin
                    public class NonConstantApiLevelImpl implements NonConstantApiLevelPlugin {
                        @Override
                        public String identity() {
                            return "non-constant-api-level";
                        }
                    }
                    """.formatted(DataversePlugin.class.getCanonicalName())
                )
            ));
            
            assertFalse(result.success(), "Compilation should fail");
            assertDiagnosticContains(result, Diagnostic.Kind.ERROR, "must be a compile-time int constant");
        }
        
        @Test
        void failsWhenRequiredProviderIsNotACoreProvider() throws IOException {
            ProcessorTestCompiler.CompilationResult result = compiler.compile(List.of(
                source(
                    "test/NotAProvider.java",
                    """
                    package test;

                    public interface NotAProvider {
                        int API_LEVEL = 1;
                    }
                    """
                ),
                source(
                    "test/InvalidProviderPlugin.java",
                    """
                    package test;

                    import %s;
                    import %s;
                    import %s;

                    @PluginContract(
                        kind = PluginContract.Kind.BASE,
                        providers = { @RequiredProvider(NotAProvider.class) }
                    )
                    public interface InvalidProviderPlugin extends Plugin {
                        int API_LEVEL = 1;
                    }
                    """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName(),
                        RequiredProvider.class.getCanonicalName()
                    )
                ),
                source(
                    "test/InvalidProviderImpl.java",
                    """
                    package test;

                    import %s;

                    @DataversePlugin
                    public class InvalidProviderImpl implements InvalidProviderPlugin {
                        @Override
                        public String identity() {
                            return "invalid-provider";
                        }
                    }
                    """.formatted(DataversePlugin.class.getCanonicalName())
                )
            ));
            
            assertFalse(result.success(), "Compilation should fail");
            assertDiagnosticContains(result, Diagnostic.Kind.ERROR, "cannot be converted");
        }
        
        @Test
        void failsWhenProviderApiLevelIsMissing() throws IOException {
            ProcessorTestCompiler.CompilationResult result = compiler.compile(List.of(
                source(
                    "test/MissingProviderApiLevel.java",
                    """
                    package test;

                    import %s;

                    public interface MissingProviderApiLevel extends CoreProvider {
                    }
                    """.formatted(CoreProvider.class.getCanonicalName())
                ),
                source(
                    "test/TestPlugin.java",
                    """
                    package test;

                    import %s;
                    import %s;
                    import %s;

                    @PluginContract(
                        kind = PluginContract.Kind.BASE,
                        providers = { @RequiredProvider(MissingProviderApiLevel.class) }
                    )
                    public interface TestPlugin extends Plugin {
                        int API_LEVEL = 1;
                    }
                    """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName(),
                        RequiredProvider.class.getCanonicalName()
                    )
                ),
                source(
                    "test/MissingProviderApiLevelImpl.java",
                    """
                    package test;

                    import %s;

                    @DataversePlugin
                    public class MissingProviderApiLevelImpl implements TestPlugin {
                        @Override
                        public String identity() {
                            return "missing-provider-api-level";
                        }
                    }
                    """.formatted(DataversePlugin.class.getCanonicalName())
                )
            ));
            
            assertFalse(result.success(), "Compilation should fail");
            assertDiagnosticContains(result, Diagnostic.Kind.ERROR, "must declare int API_LEVEL");
        }
        
        @Test
        void failsWhenProviderApiLevelIsNotCompileTimeConstant() throws IOException {
            ProcessorTestCompiler.CompilationResult result = compiler.compile(List.of(
                source(
                    "test/NonConstantProviderApiLevel.java",
                    """
                    package test;

                    import %s;

                    public interface NonConstantProviderApiLevel extends CoreProvider {
                        Integer API_LEVEL = Integer.valueOf(9);
                    }
                    """.formatted(CoreProvider.class.getCanonicalName())
                ),
                source(
                    "test/TestPlugin.java",
                    """
                    package test;

                    import %s;
                    import %s;
                    import %s;

                    @PluginContract(
                        kind = PluginContract.Kind.BASE,
                        providers = { @RequiredProvider(NonConstantProviderApiLevel.class) }
                    )
                    public interface TestPlugin extends Plugin {
                        int API_LEVEL = 1;
                    }
                    """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName(),
                        RequiredProvider.class.getCanonicalName()
                    )
                ),
                source(
                    "test/NonConstantProviderApiLevelImpl.java",
                    """
                    package test;

                    import %s;

                    @DataversePlugin
                    public class NonConstantProviderApiLevelImpl implements TestPlugin {
                        @Override
                        public String identity() {
                            return "non-constant-provider-api-level";
                        }
                    }
                    """.formatted(DataversePlugin.class.getCanonicalName())
                )
            ));
            
            assertFalse(result.success(), "Compilation should fail");
            assertDiagnosticContains(result, Diagnostic.Kind.ERROR, "must be a compile-time int constant");
        }
        
        @Test
        void discoversInheritedContractsTransitively() throws IOException {
            ProcessorTestCompiler.CompilationResult result = compiler.compile(List.of(
                source(
                    "test/TestProvider.java",
                    """
                    package test;

                    import %s;

                    public interface TestProvider extends CoreProvider {
                        int API_LEVEL = 11;
                    }
                    """.formatted(CoreProvider.class.getCanonicalName())
                ),
                source(
                    "test/BasePlugin.java",
                    """
                    package test;

                    import %s;
                    import %s;
                    import %s;

                    @PluginContract(
                        kind = PluginContract.Kind.BASE,
                        providers = { @RequiredProvider(TestProvider.class) }
                    )
                    public interface BasePlugin extends Plugin {
                        int API_LEVEL = 3;
                    }
                    """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName(),
                        RequiredProvider.class.getCanonicalName()
                    )
                ),
                source(
                    "test/IntermediateCapability.java",
                    """
                    package test;

                    import %s;
                    import %s;

                    @PluginContract(
                        kind = PluginContract.Kind.CAPABILITY,
                        requires = { BasePlugin.class }
                    )
                    public interface IntermediateCapability extends BasePlugin {
                        int API_LEVEL = 4;
                    }
                    """.formatted(
                        PluginContract.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                ),
                source(
                    "test/LeafCapability.java",
                    """
                    package test;

                    import %s;
                    import %s;

                    @PluginContract(
                        kind = PluginContract.Kind.CAPABILITY,
                        requires = { BasePlugin.class, IntermediateCapability.class }
                    )
                    public interface LeafCapability extends IntermediateCapability {
                        int API_LEVEL = 5;
                    }
                    """.formatted(
                        PluginContract.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                ),
                source(
                    "test/TransitiveImpl.java",
                    """
                    package test;

                    import %s;

                    @DataversePlugin
                    public class TransitiveImpl implements LeafCapability {
                        @Override
                        public String identity() {
                            return "transitive";
                        }
                    }
                    """.formatted(DataversePlugin.class.getCanonicalName())
                )
            ));
            
            assertTrue(result.success(), result.diagnosticsAsText());
            
            String descriptorPath = "META-INF/dataverse/plugins/test_TransitiveImpl.properties";
            assertTrue(Files.exists(result.generatedFile(descriptorPath)), "Descriptor should be generated");
            
            String descriptor = Files.readString(result.generatedFile(descriptorPath));
            assertTrue(descriptor.contains("plugin.class=test.TransitiveImpl"));
            assertTrue(descriptor.contains("plugin.kind=test.BasePlugin"));
            assertTrue(descriptor.contains("plugin.test.BasePlugin.level=3"));
            assertTrue(descriptor.contains("plugin.test.IntermediateCapability.level=4"));
            assertTrue(descriptor.contains("plugin.test.LeafCapability.level=5"));
            assertTrue(descriptor.contains("plugin.requires.test.TestProvider.level=11"));
        }
        
        @Test
        void suppressesGeneratedServiceFileForWholeContractWhenAutoServiceIsMixedWithNormalImplementations() throws IOException {
            ProcessorTestCompiler.CompilationResult result = compiler.compile(List.of(
                source(
                    "com/google/auto/service/AutoService.java",
                    """
                    package com.google.auto.service;

                    import java.lang.annotation.ElementType;
                    import java.lang.annotation.Retention;
                    import java.lang.annotation.RetentionPolicy;
                    import java.lang.annotation.Target;

                    @Retention(RetentionPolicy.RUNTIME)
                    @Target(ElementType.TYPE)
                    public @interface AutoService {
                        Class<?>[] value();
                    }
                    """
                ),
                source(
                    "test/TestPlugin.java",
                    """
                    package test;

                    import %s;
                    import %s;

                    @PluginContract(kind = PluginContract.Kind.BASE)
                    public interface TestPlugin extends Plugin {
                        int API_LEVEL = 2;
                    }
                    """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                ),
                source(
                    "test/AutoServiceImpl.java",
                    """
                    package test;

                    import com.google.auto.service.AutoService;
                    import %s;

                    @DataversePlugin
                    @AutoService(TestPlugin.class)
                    public class AutoServiceImpl implements TestPlugin {
                        @Override
                        public String identity() {
                            return "auto";
                        }
                    }
                    """.formatted(DataversePlugin.class.getCanonicalName())
                ),
                source(
                    "test/NormalImpl.java",
                    """
                    package test;

                    import %s;

                    @DataversePlugin
                    public class NormalImpl implements TestPlugin {
                        @Override
                        public String identity() {
                            return "normal";
                        }
                    }
                    """.formatted(DataversePlugin.class.getCanonicalName())
                )
            ));
            
            assertTrue(result.success(), result.diagnosticsAsText());
            
            String autoDescriptorPath = "META-INF/dataverse/plugins/test_AutoServiceImpl.properties";
            String normalDescriptorPath = "META-INF/dataverse/plugins/test_NormalImpl.properties";
            String servicePath = "META-INF/services/test.TestPlugin";
            
            assertTrue(Files.exists(result.generatedFile(autoDescriptorPath)), "AutoService descriptor should be generated");
            assertTrue(Files.exists(result.generatedFile(normalDescriptorPath)), "Normal descriptor should be generated");
            assertFalse(Files.exists(result.generatedFile(servicePath)), "Service file should be suppressed for the whole contract");
            
            assertDiagnosticContains(result, Diagnostic.Kind.WARNING, "@AutoService detected");
        }
        
        @Test
        void mergesDuplicateProviderRequirementsWhenLevelsMatch() throws IOException {
            ProcessorTestCompiler.CompilationResult result = compiler.compile(List.of(
                source(
                    "test/SharedProvider.java",
                    """
                    package test;

                    import %s;

                    public interface SharedProvider extends CoreProvider {
                        int API_LEVEL = 8;
                    }
                    """.formatted(CoreProvider.class.getCanonicalName())
                ),
                source(
                    "test/BasePlugin.java",
                    """
                    package test;

                    import %s;
                    import %s;

                    @PluginContract(kind = PluginContract.Kind.BASE)
                    public interface BasePlugin extends Plugin {
                        int API_LEVEL = 1;
                    }
                    """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                ),
                source(
                    "test/CapabilityOne.java",
                    """
                    package test;

                    import %s;
                    import %s;
                    import %s;

                    @PluginContract(
                        kind = PluginContract.Kind.CAPABILITY,
                        requires = { BasePlugin.class },
                        providers = { @RequiredProvider(SharedProvider.class) }
                    )
                    public interface CapabilityOne extends Plugin {
                        int API_LEVEL = 1;
                    }
                    """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName(),
                        RequiredProvider.class.getCanonicalName()
                    )
                ),
                source(
                    "test/CapabilityTwo.java",
                    """
                    package test;

                    import %s;
                    import %s;
                    import %s;

                    @PluginContract(
                        kind = PluginContract.Kind.CAPABILITY,
                        requires = { BasePlugin.class },
                        providers = { @RequiredProvider(SharedProvider.class) }
                    )
                    public interface CapabilityTwo extends Plugin {
                        int API_LEVEL = 2;
                    }
                    """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName(),
                        RequiredProvider.class.getCanonicalName()
                    )
                ),
                source(
                    "test/MergedProviderImpl.java",
                    """
                    package test;

                    import %s;

                    @DataversePlugin
                    public class MergedProviderImpl implements BasePlugin, CapabilityOne, CapabilityTwo {
                        @Override
                        public String identity() {
                            return "merged-provider";
                        }
                    }
                    """.formatted(DataversePlugin.class.getCanonicalName())
                )
            ));
            
            assertTrue(result.success(), result.diagnosticsAsText());
            
            String descriptorPath = "META-INF/dataverse/plugins/test_MergedProviderImpl.properties";
            String descriptor = Files.readString(result.generatedFile(descriptorPath));
            
            assertTrue(descriptor.contains("plugin.requires.test.SharedProvider.level=8"));
        }
    }
    
    private static ProcessorTestCompiler.SourceFile source(String relativePath, String content) {
        return new ProcessorTestCompiler.SourceFile(relativePath, content);
    }
    
    private static void assertDiagnosticContains(
        ProcessorTestCompiler.CompilationResult result,
        Diagnostic.Kind kind,
        String fragment
    ) {
        boolean found = result.diagnostics().stream()
            .filter(diagnostic -> diagnostic.getKind() == kind)
            .map(diagnostic -> diagnostic.getMessage(null))
            .anyMatch(message -> message.contains(fragment));
        
        assertTrue(
            found,
            () -> "Expected diagnostic containing '%s' but got:%n%s".formatted(fragment, result.diagnosticsAsText())
        );
    }
}