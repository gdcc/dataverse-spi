package io.gdcc.spi.meta.processor;

import io.gdcc.spi.meta.annotations.DataversePlugin;
import io.gdcc.spi.meta.annotations.PluginContract;
import io.gdcc.spi.meta.annotations.RequiredProvider;
import io.gdcc.spi.meta.descriptor.Descriptor;
import io.gdcc.spi.meta.descriptor.DescriptorFormat;
import io.gdcc.spi.meta.plugin.CoreProvider;
import io.gdcc.spi.meta.plugin.Plugin;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.tools.Diagnostic;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                            role = PluginContract.Role.BASE,
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
            
            String descriptorPath = DescriptorFormat.toPath("test.GoodPlugin");
            String servicePath = "META-INF/services/test.TestPlugin";
            
            assertTrue(Files.exists(result.generatedFile(descriptorPath)), "Descriptor should be generated");
            assertTrue(Files.exists(result.generatedFile(servicePath)), "Service file should be generated");
            
            Descriptor descriptor = DescriptorFormat.read(Files.readString(result.generatedFile(descriptorPath)));
            assertEquals("test.GoodPlugin", descriptor.klass());
            assertEquals("test.TestPlugin", descriptor.kind());
            assertEquals(3, descriptor.contractLevel("test.TestPlugin"));
            assertEquals(7, descriptor.requiredProviderLevel("test.TestProvider"));
            
            String serviceFile = Files.readString(result.generatedFile(servicePath));
            assertEquals("test.GoodPlugin", serviceFile.trim());
        }
        
        @Test
        void failsWhenPluginContractIsPlacedOnImplementationClass() throws IOException {
            ProcessorTestCompiler.CompilationResult result = compiler.compile(List.of(
                source(
                    "test/TestPlugin.java",
                    """
                        package test;
                    
                        import %s;
                        import %s;
                    
                        @PluginContract(role = PluginContract.Role.BASE)
                        public interface TestPlugin extends Plugin {
                            int API_LEVEL = 1;
                        }
                        """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                ),
                source(
                    "test/InvalidImplementation.java",
                    """
                        package test;
                    
                        import %s;
                        import %s;
                    
                        @DataversePlugin
                        @PluginContract(role = PluginContract.Role.BASE)
                        public class InvalidImplementation implements TestPlugin {
                            @Override
                            public String identity() {
                                return "invalid";
                            }
                        }
                        """.formatted(
                        DataversePlugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                )
            ));
            
            assertFalse(result.success(), "Compilation should fail");
            assertDiagnosticContains(result, Diagnostic.Kind.ERROR, "@PluginContract may only be declared on interfaces");
        }
        
        @Test
        void warnsWhenPluginImplementationOmitsDataversePluginAnnotation() throws IOException {
            ProcessorTestCompiler.CompilationResult result = compiler.compile(List.of(
                source(
                    "test/TestPlugin.java",
                    """
                        package test;
                    
                        import %s;
                        import %s;
                    
                        @PluginContract(role = PluginContract.Role.BASE)
                        public interface TestPlugin extends Plugin {
                            int API_LEVEL = 2;
                        }
                        """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                ),
                source(
                    "test/ImplicitPlugin.java",
                    """
                        package test;
                    
                        public class ImplicitPlugin implements TestPlugin {
                            @Override
                            public String identity() {
                                return "implicit";
                            }
                        }
                        """
                )
            ));
            
            assertTrue(result.success(), result.diagnosticsAsText());
            assertDiagnosticContains(result, Diagnostic.Kind.WARNING, "@DataversePlugin");
        }
        
        @Test
        void createsDescriptorEvenWhenPluginImplementationOmitsDataversePluginAnnotation() throws IOException {
            ProcessorTestCompiler.CompilationResult result = compiler.compile(List.of(
                source(
                    "test/TestPlugin.java",
                    """
                        package test;
                    
                        import %s;
                        import %s;
                    
                        @PluginContract(role = PluginContract.Role.BASE)
                        public interface TestPlugin extends Plugin {
                            int API_LEVEL = 4;
                        }
                        """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                ),
                source(
                    "test/ImplicitPlugin.java",
                    """
                        package test;
                    
                        public class ImplicitPlugin implements TestPlugin {
                            @Override
                            public String identity() {
                                return "implicit";
                            }
                        }
                        """
                )
            ));
            
            assertTrue(result.success(), result.diagnosticsAsText());
            
            String descriptorPath = DescriptorFormat.toPath("test.ImplicitPlugin");
            assertTrue(Files.exists(result.generatedFile(descriptorPath)), "Descriptor should still be generated");
            
            Descriptor descriptor = DescriptorFormat.read(Files.readString(result.generatedFile(descriptorPath)));
            assertEquals("test.ImplicitPlugin", descriptor.klass());
            assertEquals("test.TestPlugin", descriptor.kind());
            assertEquals(4, descriptor.contractLevel("test.TestPlugin"));
        }
        
        @Test
        void createsServiceFileEvenWhenPluginImplementationOmitsDataversePluginAnnotation() throws IOException {
            ProcessorTestCompiler.CompilationResult result = compiler.compile(List.of(
                source(
                    "test/TestPlugin.java",
                    """
                        package test;
                    
                        import %s;
                        import %s;
                    
                        @PluginContract(role = PluginContract.Role.BASE)
                        public interface TestPlugin extends Plugin {
                            int API_LEVEL = 5;
                        }
                        """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                ),
                source(
                    "test/ImplicitPlugin.java",
                    """
                        package test;
                    
                        public class ImplicitPlugin implements TestPlugin {
                            @Override
                            public String identity() {
                                return "implicit";
                            }
                        }
                        """
                )
            ));
            
            assertTrue(result.success(), result.diagnosticsAsText());
            
            String servicePath = "META-INF/services/test.TestPlugin";
            assertTrue(Files.exists(result.generatedFile(servicePath)), "Service file should still be generated");
            
            String serviceFile = Files.readString(result.generatedFile(servicePath));
            assertEquals("test.ImplicitPlugin", serviceFile.trim());
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
                    
                        @PluginContract(role = PluginContract.Role.BASE)
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
                    
                        @PluginContract(role = PluginContract.Role.BASE)
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
                    
                        @PluginContract(role = PluginContract.Role.BASE)
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
                            role = PluginContract.Role.CAPABILITY,
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
        void failsWhenPluginInterfaceLacksPluginContractAnnotation() throws IOException {
            ProcessorTestCompiler.CompilationResult result = compiler.compile(List.of(
                source(
                    "test/UndeclaredPluginContract.java",
                    """
                        package test;
                    
                        import %s;
                    
                        public interface UndeclaredPluginContract extends Plugin {
                            int API_LEVEL = 1;
                        }
                        """.formatted(Plugin.class.getCanonicalName())
                ),
                source(
                    "test/UndeclaredPluginImpl.java",
                    """
                        package test;
                    
                        import %s;
                    
                        @DataversePlugin
                        public class UndeclaredPluginImpl implements UndeclaredPluginContract {
                            @Override
                            public String identity() {
                                return "undeclared-contract";
                            }
                        }
                        """.formatted(DataversePlugin.class.getCanonicalName())
                )
            ));
            
            assertFalse(result.success(), "Compilation should fail");
            assertDiagnosticContains(result, Diagnostic.Kind.ERROR, "Plugin interfaces must declare @PluginContract");
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
                    
                        @PluginContract(role = PluginContract.Role.BASE)
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
            
            String descriptorPath = DescriptorFormat.toPath("test.AutoServicePlugin");
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
                    
                        @PluginContract(role = PluginContract.Role.BASE)
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
                    
                        @PluginContract(role = PluginContract.Role.BASE)
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
                    
                        @PluginContract(role = PluginContract.Role.CAPABILITY)
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
            assertDiagnosticContains(result, Diagnostic.Kind.ERROR, "exactly one Role.BASE @PluginContract");
        }
        
        @Test
        void failsWhenDataversePluginAnnotatedClassIsNotAPluginImplementation() throws IOException {
            ProcessorTestCompiler.CompilationResult result = compiler.compile(List.of(
                source(
                    "test/NotAPlugin.java",
                    """
                        package test;
                    
                        import %s;
                    
                        @DataversePlugin
                        public class NotAPlugin {
                            public String identity() {
                                return "not-a-plugin";
                            }
                        }
                        """.formatted(DataversePlugin.class.getCanonicalName())
                )
            ));
            
            assertFalse(result.success(), "Compilation should fail");
            assertDiagnosticContains(result, Diagnostic.Kind.ERROR, "No implemented plugin contracts found");
        }
        
        @Test
        void failsWhenImplementationDirectlyImplementsPlugin() throws IOException {
            ProcessorTestCompiler.CompilationResult result = compiler.compile(List.of(
                source(
                    "test/RawPluginImpl.java",
                    """
                        package test;
                    
                        import %s;
                        import %s;
                    
                        @DataversePlugin
                        public class RawPluginImpl implements Plugin {
                            @Override
                            public String identity() {
                                return "raw-plugin";
                            }
                        }
                        """.formatted(
                        Plugin.class.getCanonicalName(),
                        DataversePlugin.class.getCanonicalName()
                    )
                )
            ));
            
            assertFalse(result.success(), "Compilation should fail");
            assertDiagnosticContains(
                result,
                Diagnostic.Kind.ERROR,
                "must implement a specific plugin contract interface, not Plugin directly"
            );
        }
        
        @Test
        void doesNotProcessAnnotatedImplementationTwice() throws IOException {
            ProcessorTestCompiler.CompilationResult result = compiler.compile(List.of(
                source(
                    "test/TestPlugin.java",
                    """
                        package test;
                    
                        import %s;
                        import %s;
                    
                        @PluginContract(role = PluginContract.Role.BASE)
                        public interface TestPlugin extends Plugin {
                            int API_LEVEL = 6;
                        }
                        """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                ),
                source(
                    "test/OncePlugin.java",
                    """
                        package test;
                    
                        import %s;
                    
                        @DataversePlugin
                        public class OncePlugin implements TestPlugin {
                            @Override
                            public String identity() {
                                return "once";
                            }
                        }
                        """.formatted(DataversePlugin.class.getCanonicalName())
                )
            ));
            
            assertTrue(result.success(), result.diagnosticsAsText());
            
            String servicePath = "META-INF/services/test.TestPlugin";
            assertTrue(Files.exists(result.generatedFile(servicePath)), "Service file should be generated");
            
            List<String> lines = Files.readAllLines(result.generatedFile(servicePath))
                .stream()
                .filter(line -> !line.isBlank())
                .toList();
            
            assertEquals(1, lines.size(), "Implementation should only be registered once");
            assertEquals("test.OncePlugin", lines.get(0));
        }
        
        @Test
        void aggregatesMultipleImplementationsIntoOneServiceFile() throws IOException {
            ProcessorTestCompiler.CompilationResult result = compiler.compile(List.of(
                source(
                    "test/TestPlugin.java",
                    """
                        package test;
                    
                        import %s;
                        import %s;
                    
                        @PluginContract(role = PluginContract.Role.BASE)
                        public interface TestPlugin extends Plugin {
                            int API_LEVEL = 1;
                        }
                        """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                ),
                source(
                    "test/APlugin.java",
                    """
                        package test;
                    
                        import %s;
                    
                        @DataversePlugin
                        public class APlugin implements TestPlugin {
                            @Override
                            public String identity() {
                                return "a";
                            }
                        }
                        """.formatted(DataversePlugin.class.getCanonicalName())
                ),
                source(
                    "test/BPlugin.java",
                    """
                        package test;
                    
                        import %s;
                    
                        @DataversePlugin
                        public class BPlugin implements TestPlugin {
                            @Override
                            public String identity() {
                                return "b";
                            }
                        }
                        """.formatted(DataversePlugin.class.getCanonicalName())
                )
            ));
            
            assertTrue(result.success(), result.diagnosticsAsText());
            
            String servicePath = "META-INF/services/test.TestPlugin";
            assertTrue(Files.exists(result.generatedFile(servicePath)), "Aggregated service file should be generated");
            
            List<String> lines = Files.readAllLines(result.generatedFile(servicePath))
                .stream()
                .filter(line -> !line.isBlank())
                .toList();
            
            assertEquals(List.of("test.APlugin", "test.BPlugin"), lines);
        }
        
        @Test
        void compilesWhenAnnotatedPluginInterfaceHasNoImplementation() throws IOException {
            ProcessorTestCompiler.CompilationResult result = compiler.compile(List.of(
                source(
                    "test/LonelyPluginContract.java",
                    """
                        package test;
                    
                        import %s;
                        import %s;
                    
                        @PluginContract(role = PluginContract.Role.BASE)
                        public interface LonelyPluginContract extends Plugin {
                            int API_LEVEL = 1;
                        }
                        """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                )
            ));
            
            assertTrue(result.success(), result.diagnosticsAsText());
        }
        
        @Test
        void compilesWhenUnusedProviderInterfaceHasApiLevel() throws IOException {
            ProcessorTestCompiler.CompilationResult result = compiler.compile(List.of(
                source(
                    "test/LonelyProvider.java",
                    """
                        package test;
                    
                        import %s;
                    
                        public interface LonelyProvider extends CoreProvider {
                            int API_LEVEL = 42;
                        }
                        """.formatted(CoreProvider.class.getCanonicalName())
                )
            ));
            
            assertTrue(result.success(), result.diagnosticsAsText());
        }
        
        @Test
        // because we want to allow base classes, as long as concrete classes are annotated @DataversePlugin
        void doesNotWarnForAbstractUnannotatedPluginBaseClass() throws IOException {
            ProcessorTestCompiler.CompilationResult result = compiler.compile(List.of(
                source(
                    "test/TestPlugin.java",
                    """
                        package test;
                    
                        import %s;
                        import %s;
                    
                        @PluginContract(role = PluginContract.Role.BASE)
                        public interface TestPlugin extends Plugin {
                            int API_LEVEL = 1;
                        }
                        """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                ),
                source(
                    "test/AbstractBasePlugin.java",
                    """
                        package test;
                    
                        public abstract class AbstractBasePlugin implements TestPlugin {
                            @Override
                            public String identity() {
                                return "base";
                            }
                        }
                        """
                )
            ));
            
            assertTrue(result.success(), result.diagnosticsAsText());
            assertDiagnosticDoesNotContain(result, Diagnostic.Kind.WARNING, "@DataversePlugin");
        }
        
        @Test
        void failsWhenIndirectPluginInterfaceLacksPluginContractAnnotation() throws IOException {
            ProcessorTestCompiler.CompilationResult result = compiler.compile(List.of(
                source(
                    "test/BasePlugin.java",
                    """
                        package test;
                    
                        import %s;
                        import %s;
                    
                        @PluginContract(role = PluginContract.Role.BASE)
                        public interface BasePlugin extends Plugin {
                            int API_LEVEL = 1;
                        }
                        """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                ),
                source(
                    "test/DerivedPlugin.java",
                    """
                        package test;
                    
                        public interface DerivedPlugin extends BasePlugin {
                            int API_LEVEL = 2;
                        }
                        """
                ),
                source(
                    "test/DerivedPluginImpl.java",
                    """
                        package test;
                    
                        import %s;
                    
                        @DataversePlugin
                        public class DerivedPluginImpl implements DerivedPlugin {
                            @Override
                            public String identity() {
                                return "derived";
                            }
                        }
                        """.formatted(DataversePlugin.class.getCanonicalName())
                )
            ));
            
            assertFalse(result.success(), "Compilation should fail");
            assertDiagnosticContains(result, Diagnostic.Kind.ERROR, "Plugin interfaces must declare @PluginContract");
        }
        
        @Test
        void suppressesServiceGenerationOnlyForPluginKindManagedByAutoService() throws IOException {
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
                    "test/PluginTypeA.java",
                    """
                        package test;
                    
                        import %s;
                        import %s;
                    
                        @PluginContract(role = PluginContract.Role.BASE)
                        public interface PluginTypeA extends Plugin {
                            int API_LEVEL = 1;
                        }
                        """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                ),
                source(
                    "test/PluginTypeB.java",
                    """
                        package test;
                    
                        import %s;
                        import %s;
                    
                        @PluginContract(role = PluginContract.Role.BASE)
                        public interface PluginTypeB extends Plugin {
                            int API_LEVEL = 1;
                        }
                        """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                ),
                source(
                    "test/AutoManagedA.java",
                    """
                        package test;
                    
                        import com.google.auto.service.AutoService;
                        import %s;
                    
                        @DataversePlugin
                        @AutoService(PluginTypeA.class)
                        public class AutoManagedA implements PluginTypeA {
                            @Override
                            public String identity() {
                                return "a";
                            }
                        }
                        """.formatted(DataversePlugin.class.getCanonicalName())
                ),
                source(
                    "test/NormalB.java",
                    """
                        package test;
                    
                        import %s;
                    
                        @DataversePlugin
                        public class NormalB implements PluginTypeB {
                            @Override
                            public String identity() {
                                return "b";
                            }
                        }
                        """.formatted(DataversePlugin.class.getCanonicalName())
                )
            ));
            
            assertTrue(result.success(), result.diagnosticsAsText());
            
            String servicePathA = "META-INF/services/test.PluginTypeA";
            String servicePathB = "META-INF/services/test.PluginTypeB";
            
            assertFalse(Files.exists(result.generatedFile(servicePathA)), "PluginTypeA service file should be suppressed");
            assertTrue(Files.exists(result.generatedFile(servicePathB)), "PluginTypeB service file should still be generated");
            
            String serviceFileB = Files.readString(result.generatedFile(servicePathB));
            assertEquals("test.NormalB", serviceFileB.trim());
        }
    }
    
    @Nested
    class EdgeCases {
        @Test
        void failsWhenContractIsOnNonPluginInterface() throws IOException {
            ProcessorTestCompiler.CompilationResult result = compiler.compile(List.of(
                source(
                    "test/MissingExtendsPlugin.java",
                    """
                    package test;

                    import %s;
                    import %s;

                    @PluginContract(role = PluginContract.Role.BASE)
                    public interface MissingExtendsPlugin {
                    }
                    """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                ),
                source(
                    "test/MissingExtendsPluginImpl.java",
                    """
                    package test;

                    import %s;

                    @DataversePlugin
                    public class MissingExtendsPluginImpl implements MissingExtendsPlugin {
                        @Override
                        public String identity() {
                            return "missing-extends-plugin";
                        }
                    }
                    """.formatted(DataversePlugin.class.getCanonicalName())
                )
            ));
            
            assertFalse(result.success(), "Compilation should fail");
            assertDiagnosticContains(result, Diagnostic.Kind.ERROR, "must implement a specific @PluginContract interface");
        }
        
        @Test
        void failsWhenContractApiLevelIsMissing() throws IOException {
            ProcessorTestCompiler.CompilationResult result = compiler.compile(List.of(
                source(
                    "test/MissingApiLevelPlugin.java",
                    """
                    package test;

                    import %s;
                    import %s;

                    @PluginContract(role = PluginContract.Role.BASE)
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

                    @PluginContract(role = PluginContract.Role.BASE)
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
                        role = PluginContract.Role.BASE,
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
                        role = PluginContract.Role.BASE,
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
                        role = PluginContract.Role.BASE,
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
                        role = PluginContract.Role.BASE,
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
                        role = PluginContract.Role.CAPABILITY,
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
                        role = PluginContract.Role.CAPABILITY,
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
            
            String descriptorPath = DescriptorFormat.toPath("test.TransitiveImpl");
            assertTrue(Files.exists(result.generatedFile(descriptorPath)), "Descriptor should be generated");
            
            Descriptor descriptor = DescriptorFormat.read(Files.readString(result.generatedFile(descriptorPath)));
            assertEquals("test.TransitiveImpl", descriptor.klass());
            assertEquals("test.BasePlugin", descriptor.kind());
            assertEquals(3, descriptor.contractLevel("test.BasePlugin"));
            assertEquals(4, descriptor.contractLevel("test.IntermediateCapability"));
            assertEquals(5, descriptor.contractLevel("test.LeafCapability"));
            assertEquals(11, descriptor.requiredProviderLevel("test.TestProvider"));
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

                    @PluginContract(role = PluginContract.Role.BASE)
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
            
            String autoDescriptorPath = DescriptorFormat.toPath("test.AutoServiceImpl");
            String normalDescriptorPath = DescriptorFormat.toPath("test.NormalImpl");
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

                    @PluginContract(role = PluginContract.Role.BASE)
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
                        role = PluginContract.Role.CAPABILITY,
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
                        role = PluginContract.Role.CAPABILITY,
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
            
            String descriptorPath = DescriptorFormat.toPath("test.MergedProviderImpl");
            Descriptor descriptor = DescriptorFormat.read(Files.readString(result.generatedFile(descriptorPath)));
            assertEquals(8, descriptor.requiredProviderLevel("test.SharedProvider"));
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
    
    private static void assertDiagnosticDoesNotContain(
        ProcessorTestCompiler.CompilationResult result,
        Diagnostic.Kind kind,
        String fragment
    ) {
        boolean found = result.diagnostics().stream()
            .filter(diagnostic -> diagnostic.getKind() == kind)
            .map(diagnostic -> diagnostic.getMessage(null))
            .anyMatch(message -> message.contains(fragment));
        
        assertFalse(
            found,
            () -> "Did not expect diagnostic containing '%s' but got:%n%s".formatted(fragment, result.diagnosticsAsText())
        );
    }
    
    private static int countOccurrences(String text, String fragment) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(fragment, index)) >= 0) {
            count++;
            index += fragment.length();
        }
        return count;
    }
}