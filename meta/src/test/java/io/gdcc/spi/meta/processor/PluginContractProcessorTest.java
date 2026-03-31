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
    class ImplementationContractGraphRules {
        @Test
        void compilesWhenImplementationOverridesConflictingDefaultMethodsFromCapabilities() throws IOException {
            ProcessorTestCompiler.CompilationResult result = compiler.compile(List.of(
                source(
                    "test/export/BaseExporter.java",
                    """
                    package test.export;

                    import %s;
                    import %s;

                    @PluginContract(role = PluginContract.Role.BASE)
                    public interface BaseExporter extends Plugin {
                        int API_LEVEL = 1;

                        String getMediaType();
                    }
                    """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                ),
                source(
                    "test/export/XmlCapability.java",
                    """
                    package test.export;

                    import %s;
                    import %s;

                    @PluginContract(
                        role = PluginContract.Role.CAPABILITY,
                        requires = { BaseExporter.class }
                    )
                    public interface XmlCapability extends Plugin {
                        int API_LEVEL = 2;

                        default String getMediaType() {
                            return "application/xml";
                        }
                    }
                    """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                ),
                source(
                    "test/export/TextCapability.java",
                    """
                    package test.export;

                    import %s;
                    import %s;

                    @PluginContract(
                        role = PluginContract.Role.CAPABILITY,
                        requires = { BaseExporter.class }
                    )
                    public interface TextCapability extends Plugin {
                        int API_LEVEL = 3;

                        default String getMediaType() {
                            return "text/plain";
                        }
                    }
                    """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                ),
                source(
                    "test/export/MultiCapabilityExporter.java",
                    """
                    package test.export;

                    import %s;

                    @DataversePlugin
                    public class MultiCapabilityExporter implements BaseExporter, XmlCapability, TextCapability {
                        @Override
                        public String identity() {
                            return "multi";
                        }

                        @Override
                        public String getMediaType() {
                            return XmlCapability.super.getMediaType();
                        }
                    }
                    """.formatted(DataversePlugin.class.getCanonicalName())
                )
            ));
            
            assertTrue(result.success(), result.diagnosticsAsText());
            
            String descriptorPath = DescriptorFormat.toPath("test.export.MultiCapabilityExporter");
            assertTrue(Files.exists(result.generatedFile(descriptorPath)), "Descriptor should be generated");
            
            Descriptor descriptor = DescriptorFormat.read(Files.readString(result.generatedFile(descriptorPath)));
            assertEquals("test.export.BaseExporter", descriptor.kind());
            assertEquals(1, descriptor.contractLevel("test.export.BaseExporter"));
            assertEquals(2, descriptor.contractLevel("test.export.XmlCapability"));
            assertEquals(3, descriptor.contractLevel("test.export.TextCapability"));
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
            assertDiagnosticContains(result, Diagnostic.Kind.ERROR, "exactly one Role.BASE @PluginContract");
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
    }
    
    @Nested
    class DescriptorFileGeneration {
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
        void compilesWhenPluginImplementsOneBaseAndOneCapability() throws IOException {
            ProcessorTestCompiler.CompilationResult result = compiler.compile(List.of(
                source(
                    "test/export/BaseExporter.java",
                    """
                    package test.export;
    
                    import %s;
                    import %s;
    
                    @PluginContract(role = PluginContract.Role.BASE)
                    public interface BaseExporter extends Plugin {
                        int API_LEVEL = 1;
    
                        String getMediaType();
                    }
                    """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                ),
                source(
                    "test/export/XmlCapability.java",
                    """
                    package test.export;
    
                    import %s;
                    import %s;
    
                    @PluginContract(
                        role = PluginContract.Role.CAPABILITY,
                        requires = { BaseExporter.class }
                    )
                    public interface XmlCapability extends BaseExporter {
                        int API_LEVEL = 2;
    
                        default String getMediaType() {
                            return "application/xml";
                        }
                    }
                    """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                ),
                source(
                    "test/export/XmlExporterImpl.java",
                    """
                    package test.export;
    
                    import %s;
    
                    @DataversePlugin
                    public class XmlExporterImpl implements XmlCapability {
                        @Override
                        public String identity() {
                            return "xml";
                        }
                    }
                    """.formatted(DataversePlugin.class.getCanonicalName())
                )
            ));
            
            assertTrue(result.success(), result.diagnosticsAsText());
            
            String descriptorPath = DescriptorFormat.toPath("test.export.XmlExporterImpl");
            assertTrue(Files.exists(result.generatedFile(descriptorPath)), "Descriptor should be generated");
            
            Descriptor descriptor = DescriptorFormat.read(Files.readString(result.generatedFile(descriptorPath)));
            assertEquals("test.export.XmlExporterImpl", descriptor.klass());
            assertEquals("test.export.BaseExporter", descriptor.kind());
            assertEquals(1, descriptor.contractLevel("test.export.BaseExporter"));
            assertEquals(2, descriptor.contractLevel("test.export.XmlCapability"));
        }
        
        @Test
        void compilesWhenPluginImplementsOneBaseAndTwoCapabilities() throws IOException {
            ProcessorTestCompiler.CompilationResult result = compiler.compile(List.of(
                source(
                    "test/export/BaseExporter.java",
                    """
                    package test.export;
    
                    import %s;
                    import %s;
    
                    @PluginContract(role = PluginContract.Role.BASE)
                    public interface BaseExporter extends Plugin {
                        int API_LEVEL = 1;
                    }
                    """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                ),
                source(
                    "test/export/XmlCapability.java",
                    """
                    package test.export;
    
                    import %s;
                    import %s;
    
                    @PluginContract(
                        role = PluginContract.Role.CAPABILITY,
                        requires = { BaseExporter.class }
                    )
                    public interface XmlCapability extends Plugin {
                        int API_LEVEL = 2;
                    }
                    """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                ),
                source(
                    "test/export/PrettyPrintCapability.java",
                    """
                    package test.export;
    
                    import %s;
                    import %s;
    
                    @PluginContract(
                        role = PluginContract.Role.CAPABILITY,
                        requires = { BaseExporter.class }
                    )
                    public interface PrettyPrintCapability extends Plugin {
                        int API_LEVEL = 3;
                    }
                    """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                ),
                source(
                    "test/export/XmlPrettyExporterImpl.java",
                    """
                    package test.export;
    
                    import %s;
    
                    @DataversePlugin
                    public class XmlPrettyExporterImpl implements BaseExporter, XmlCapability, PrettyPrintCapability {
                        @Override
                        public String identity() {
                            return "xml-pretty";
                        }
                    }
                    """.formatted(DataversePlugin.class.getCanonicalName())
                )
            ));
            
            assertTrue(result.success(), result.diagnosticsAsText());
            
            String descriptorPath = DescriptorFormat.toPath("test.export.XmlPrettyExporterImpl");
            assertTrue(Files.exists(result.generatedFile(descriptorPath)), "Descriptor should be generated");
            
            Descriptor descriptor = DescriptorFormat.read(Files.readString(result.generatedFile(descriptorPath)));
            assertEquals("test.export.BaseExporter", descriptor.kind());
            assertEquals(1, descriptor.contractLevel("test.export.BaseExporter"));
            assertEquals(2, descriptor.contractLevel("test.export.XmlCapability"));
            assertEquals(3, descriptor.contractLevel("test.export.PrettyPrintCapability"));
        }
        
        @Test
        void mergesProviderRequirementsFromBaseAndCapability() throws IOException {
            ProcessorTestCompiler.CompilationResult result = compiler.compile(List.of(
                source(
                    "test/export/BaseProvider.java",
                    """
                    package test.export;
    
                    import %s;
    
                    public interface BaseProvider extends CoreProvider {
                        int API_LEVEL = 10;
                    }
                    """.formatted(CoreProvider.class.getCanonicalName())
                ),
                source(
                    "test/export/XmlProvider.java",
                    """
                    package test.export;
    
                    import %s;
    
                    public interface XmlProvider extends CoreProvider {
                        int API_LEVEL = 20;
                    }
                    """.formatted(CoreProvider.class.getCanonicalName())
                ),
                source(
                    "test/export/BaseExporter.java",
                    """
                    package test.export;
    
                    import %s;
                    import %s;
                    import %s;
    
                    @PluginContract(
                        role = PluginContract.Role.BASE,
                        providers = { @RequiredProvider(BaseProvider.class) }
                    )
                    public interface BaseExporter extends Plugin {
                        int API_LEVEL = 1;
                    }
                    """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName(),
                        RequiredProvider.class.getCanonicalName()
                    )
                ),
                source(
                    "test/export/XmlCapability.java",
                    """
                    package test.export;
    
                    import %s;
                    import %s;
                    import %s;
    
                    @PluginContract(
                        role = PluginContract.Role.CAPABILITY,
                        requires = { BaseExporter.class },
                        providers = { @RequiredProvider(XmlProvider.class) }
                    )
                    public interface XmlCapability extends Plugin {
                        int API_LEVEL = 2;
                    }
                    """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName(),
                        RequiredProvider.class.getCanonicalName()
                    )
                ),
                source(
                    "test/export/XmlExporterImpl.java",
                    """
                    package test.export;
    
                    import %s;
    
                    @DataversePlugin
                    public class XmlExporterImpl implements BaseExporter, XmlCapability {
                        @Override
                        public String identity() {
                            return "xml";
                        }
                    }
                    """.formatted(DataversePlugin.class.getCanonicalName())
                )
            ));
            
            assertTrue(result.success(), result.diagnosticsAsText());
            
            String descriptorPath = DescriptorFormat.toPath("test.export.XmlExporterImpl");
            Descriptor descriptor = DescriptorFormat.read(Files.readString(result.generatedFile(descriptorPath)));
            
            assertEquals(10, descriptor.requiredProviderLevel("test.export.BaseProvider"));
            assertEquals(20, descriptor.requiredProviderLevel("test.export.XmlProvider"));
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
        void usesBaseContractAsDescriptorKindWhenCapabilityIsAlsoImplemented() throws IOException {
            ProcessorTestCompiler.CompilationResult result = compiler.compile(List.of(
                source(
                    "test/export/BaseExporter.java",
                    """
                    package test.export;

                    import %s;
                    import %s;

                    @PluginContract(role = PluginContract.Role.BASE)
                    public interface BaseExporter extends Plugin {
                        int API_LEVEL = 1;
                    }
                    """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                ),
                source(
                    "test/export/XmlCapability.java",
                    """
                    package test.export;

                    import %s;
                    import %s;

                    @PluginContract(
                        role = PluginContract.Role.CAPABILITY,
                        requires = { BaseExporter.class }
                    )
                    public interface XmlCapability extends Plugin {
                        int API_LEVEL = 2;
                    }
                    """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                ),
                source(
                    "test/export/XmlExporterImpl.java",
                    """
                    package test.export;

                    import %s;

                    @DataversePlugin
                    public class XmlExporterImpl implements BaseExporter, XmlCapability {
                        @Override
                        public String identity() {
                            return "xml";
                        }
                    }
                    """.formatted(DataversePlugin.class.getCanonicalName())
                )
            ));
            
            assertTrue(result.success(), result.diagnosticsAsText());
            
            String descriptorPath = DescriptorFormat.toPath("test.export.XmlExporterImpl");
            assertTrue(Files.exists(result.generatedFile(descriptorPath)), "Descriptor should be generated");
            
            Descriptor descriptor = DescriptorFormat.read(Files.readString(result.generatedFile(descriptorPath)));
            assertEquals("test.export.BaseExporter", descriptor.kind());
            assertEquals(1, descriptor.contractLevel("test.export.BaseExporter"));
            assertEquals(2, descriptor.contractLevel("test.export.XmlCapability"));
        }
    }
    
    @Nested
    class AutoServiceFileGeneration {
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
        
        @Test
        void doesNotGenerateServiceFileForCapabilityContracts() throws IOException {
            ProcessorTestCompiler.CompilationResult result = compiler.compile(List.of(
                source(
                    "test/export/BaseExporter.java",
                    """
                    package test.export;
        
                    import %s;
                    import %s;
        
                    @PluginContract(role = PluginContract.Role.BASE)
                    public interface BaseExporter extends Plugin {
                        int API_LEVEL = 1;
                    }
                    """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                ),
                source(
                    "test/export/XmlCapability.java",
                    """
                    package test.export;
        
                    import %s;
                    import %s;
        
                    @PluginContract(
                        role = PluginContract.Role.CAPABILITY,
                        requires = { BaseExporter.class }
                    )
                    public interface XmlCapability extends Plugin {
                        int API_LEVEL = 2;
                    }
                    """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                ),
                source(
                    "test/export/XmlExporterImpl.java",
                    """
                    package test.export;
        
                    import %s;
        
                    @DataversePlugin
                    public class XmlExporterImpl implements BaseExporter, XmlCapability {
                        @Override
                        public String identity() {
                            return "xml";
                        }
                    }
                    """.formatted(DataversePlugin.class.getCanonicalName())
                )
            ));
            
            assertTrue(result.success(), result.diagnosticsAsText());
            
            String baseServicePath = "META-INF/services/test.export.BaseExporter";
            String capabilityServicePath = "META-INF/services/test.export.XmlCapability";
            
            assertTrue(Files.exists(result.generatedFile(baseServicePath)), "Base service file should be generated");
            assertFalse(Files.exists(result.generatedFile(capabilityServicePath)), "Capability service file must never be generated");
            
            String serviceFile = Files.readString(result.generatedFile(baseServicePath));
            assertEquals("test.export.XmlExporterImpl", serviceFile.trim());
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
    }
    
    @Nested
    class Providers {
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
    
    @Nested
    class ContractApiLevels {
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
    }
    
    @Nested
    class InvalidOrMissingAnnotationTargets {
        @Test
        void failsWhenContractIsPlacedOnClass() throws IOException {
            ProcessorTestCompiler.CompilationResult result = compiler.compile(List.of(
                source(
                    "test/InvalidContract.java",
                    """
                        package test;
                    
                        import %s;
                        import %s;
                    
                        @PluginContract(role = PluginContract.Role.BASE)
                        public class InvalidContract implements Plugin {
                            @Override
                            public String identity() {
                                return "invalid";
                            }
                        }
                        """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                )
            ));
            
            assertFalse(result.success(), "Compilation should fail");
            assertDiagnosticContains(result, Diagnostic.Kind.ERROR, "@PluginContract may only be declared on interfaces extending Plugin");
        }
        
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
                )
            ));
            
            assertFalse(result.success(), "Compilation should fail");
            assertDiagnosticContains(result, Diagnostic.Kind.ERROR, "@PluginContract may only be declared on interfaces extending Plugin");
        }
        
        @Test
        void failsWhenRequiredContractIsUnannotatedPluginInterface() throws IOException {
            ProcessorTestCompiler.CompilationResult result = compiler.compile(List.of(
                source(
                    "test/ImplicitPluginType.java",
                    """
                    package test;

                    import %s;

                    public interface ImplicitPluginType extends Plugin {
                        int API_LEVEL = 1;
                    }
                    """.formatted(Plugin.class.getCanonicalName())
                ),
                source(
                    "test/BadCapability.java",
                    """
                    package test;

                    import %s;
                    import %s;

                    @PluginContract(
                        role = PluginContract.Role.CAPABILITY,
                        requires = { ImplicitPluginType.class }
                    )
                    public interface BadCapability extends Plugin {
                        int API_LEVEL = 1;
                    }
                    """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                )
            ));
            
            assertFalse(result.success(), "Compilation should fail");
            assertDiagnosticContains(result, Diagnostic.Kind.ERROR, "Interfaces extending Plugin must declare @PluginContract");
        }
        
        @Test
        void failsWhenContractIsInDifferentPackage() throws IOException {
            ProcessorTestCompiler.CompilationResult result = compiler.compile(List.of(
                source(
                    "test/foobar/BasePlugin.java",
                    """
                        package test.foobar;
                        
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
                    "test/barbeque/CapabilityPluginA.java",
                    """
                        package test.barbeque;
                        
                        import %s;
                        import %s;
                        import test.foobar.BasePlugin;
                        
                        @PluginContract(
                            role = PluginContract.Role.CAPABILITY,
                            requires = BasePlugin.class
                        )
                        public interface CapabilityPluginA extends Plugin {
                            int API_LEVEL = 1;
                        }
                        """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                )));
            
            assertFalse(result.success(), "Compilation should fail");
            assertDiagnosticContains(result, Diagnostic.Kind.ERROR, "share same package path");
        }
        
        @Test
        void compilesWhenCapabilityRequiresBaseInSamePackage() throws IOException {
            ProcessorTestCompiler.CompilationResult result = compiler.compile(List.of(
                source(
                    "test/export/BaseExporter.java",
                    """
                    package test.export;
    
                    import %s;
                    import %s;
    
                    @PluginContract(role = PluginContract.Role.BASE)
                    public interface BaseExporter extends Plugin {
                        int API_LEVEL = 1;
                    }
                    """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                ),
                source(
                    "test/export/XmlCapability.java",
                    """
                    package test.export;
    
                    import %s;
                    import %s;
    
                    @PluginContract(
                        role = PluginContract.Role.CAPABILITY,
                        requires = { BaseExporter.class }
                    )
                    public interface XmlCapability extends Plugin {
                        int API_LEVEL = 1;
    
                        default String getMediaType() {
                            return "application/xml";
                        }
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
        void compilesWhenCapabilityRequiresBaseInSubpackage() throws IOException {
            ProcessorTestCompiler.CompilationResult result = compiler.compile(List.of(
                source(
                    "test/export/BaseExporter.java",
                    """
                    package test.export;
    
                    import %s;
                    import %s;
    
                    @PluginContract(role = PluginContract.Role.BASE)
                    public interface BaseExporter extends Plugin {
                        int API_LEVEL = 1;
                    }
                    """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                ),
                source(
                    "test/export/xml/XmlCapability.java",
                    """
                    package test.export.xml;
    
                    import test.export.BaseExporter;
                    import %s;
                    import %s;
    
                    @PluginContract(
                        role = PluginContract.Role.CAPABILITY,
                        requires = { BaseExporter.class }
                    )
                    public interface XmlCapability extends Plugin {
                        int API_LEVEL = 1;
    
                        default String getMediaType() {
                            return "application/xml";
                        }
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
        void failsWhenCapabilityRequiresBaseInSiblingPackage() throws IOException {
            ProcessorTestCompiler.CompilationResult result = compiler.compile(List.of(
                source(
                    "test/export/BaseExporter.java",
                    """
                    package test.export;
    
                    import %s;
                    import %s;
    
                    @PluginContract(role = PluginContract.Role.BASE)
                    public interface BaseExporter extends Plugin {
                        int API_LEVEL = 1;
                    }
                    """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                ),
                source(
                    "test/importing/XmlCapability.java",
                    """
                    package test.importing;
    
                    import test.export.BaseExporter;
                    import %s;
                    import %s;
    
                    @PluginContract(
                        role = PluginContract.Role.CAPABILITY,
                        requires = { BaseExporter.class }
                    )
                    public interface XmlCapability extends Plugin {
                        int API_LEVEL = 1;
                    }
                    """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                )
            ));
            
            assertFalse(result.success(), "Compilation should fail");
            assertDiagnosticContains(result, Diagnostic.Kind.ERROR, "same package path");
        }
        
        @Test
        void failsWhenCapabilityRequiresBaseInParentPackage() throws IOException {
            ProcessorTestCompiler.CompilationResult result = compiler.compile(List.of(
                source(
                    "test/export/xml/BaseExporter.java",
                    """
                    package test.export.xml;
    
                    import %s;
                    import %s;
    
                    @PluginContract(role = PluginContract.Role.BASE)
                    public interface BaseExporter extends Plugin {
                        int API_LEVEL = 1;
                    }
                    """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                ),
                source(
                    "test/export/XmlCapability.java",
                    """
                    package test.export;
    
                    import test.export.xml.BaseExporter;
                    import %s;
                    import %s;
    
                    @PluginContract(
                        role = PluginContract.Role.CAPABILITY,
                        requires = { BaseExporter.class }
                    )
                    public interface XmlCapability extends Plugin {
                        int API_LEVEL = 1;
                    }
                    """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                )
            ));
            
            assertFalse(result.success(), "Compilation should fail");
            assertDiagnosticContains(result, Diagnostic.Kind.ERROR, "same package path");
        }
        
        @Test
        void failsWhenBaseContractLacksPluginContractAnnotation() throws IOException {
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
                )
            ));
            
            assertFalse(result.success(), "Compilation should fail");
            assertDiagnosticContains(result, Diagnostic.Kind.ERROR, "Interfaces extending Plugin must declare @PluginContract");
        }
    }
    
    @Nested
    class BaseContractGraphRules {
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
        void failsWhenBaseContractRequiresSomething() throws IOException {
            ProcessorTestCompiler.CompilationResult result = compiler.compile(List.of(
                source(
                    "test/BasePlugin.java",
                    """
                        package test;
                        
                        import %s;
                        import %s;
                        
                        @PluginContract(
                            role = PluginContract.Role.BASE
                        )
                        public interface BasePlugin extends Plugin {
                            int API_LEVEL = 1;
                        }
                        """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                ),
                source(
                    "test/InvalidPlugin.java",
                    """
                        package test;
                        
                        import %s;
                        import %s;
                        
                        @PluginContract(
                            role = PluginContract.Role.BASE,
                            requires = BasePlugin.class
                        )
                        public interface InvalidPlugin extends Plugin {
                            int API_LEVEL = 1;
                        }
                        """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                )
            ));
            
            assertFalse(result.success(), "Compilation should fail");
            assertDiagnosticContains(result, Diagnostic.Kind.ERROR, "may not require");
        }
        
        @Test
        void failsWhenBaseContractIsExtended() throws IOException {
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
                    "test/DerivedBase.java",
                    """
                        package test;
                        
                        import %s;
                        
                        @PluginContract(role = PluginContract.Role.BASE)
                        public interface DerivedBase extends BasePlugin {
                            int API_LEVEL = 2;
                        }
                        """.formatted(PluginContract.class.getCanonicalName())
                )
            ));
            
            assertFalse(result.success(), "Compilation should fail");
            assertDiagnosticContains(result, Diagnostic.Kind.ERROR, "may not be extended");
        }
        
        @Test
        void failsWhenBaseContractIsExtendedByCapabilityButMissingRequired() throws IOException {
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
                    "test/MissingRequiredBaseCapability.java",
                    """
                        package test;
                        
                        import %s;
                        
                        @PluginContract(role = PluginContract.Role.CAPABILITY)
                        public interface MissingRequiredBaseCapability extends BasePlugin {
                            int API_LEVEL = 2;
                        }
                        """.formatted(PluginContract.class.getCanonicalName())
                )
            ));
            
            assertFalse(result.success(), "Compilation should fail");
            assertDiagnosticContains(result, Diagnostic.Kind.ERROR, "must require single base @PluginContract interface");
        }
        
        @Test
        void failsWhenContractRequiresMultipleBaseContracts() throws IOException {
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
                    "test/BadDerivedContract.java",
                    """
                        package test;
                        
                        import %s;
                        import %s;
                        
                        @PluginContract(
                            role = PluginContract.Role.CAPABILITY,
                            requires = { BasePluginA.class, BasePluginB.class }
                        )
                        public interface BadDerivedContract extends Plugin {
                            int API_LEVEL = 2;
                        }
                        """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName())
                )
            ));
            
            assertFalse(result.success(), "Compilation should fail");
            assertDiagnosticContains(result, Diagnostic.Kind.ERROR, "must require single base @PluginContract interface");
        }
        
        @Test
        void compilesWhenBaseContractHasUnrelatedIntermediateInterface() throws IOException {
            ProcessorTestCompiler.CompilationResult result = compiler.compile(List.of(
                source(
                    "test/BaseSupertype.java",
                    """
                        package test;
                        
                        import %s;
                        
                        public interface BaseSupertype {
                            String test();
                        }
                        """.formatted(Plugin.class.getCanonicalName())
                ),
                source(
                    "test/BasePlugin.java",
                    """
                        package test;
                        
                        import %s;
                        import %s;
                        
                        @PluginContract(
                            role = PluginContract.Role.BASE
                        )
                        public interface BasePlugin extends BaseSupertype, Plugin {
                            int API_LEVEL = 1;
                            default String test() {
                                return "test";
                            }
                        }
                        """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                )
            ));
            
            assertTrue(result.success(), "Compilation should not fail");
        }
    }
    
    @Nested
    class CapabilityContractGraphRules {
        @Test
        void compilesWhenCapabilityContractExtendsRequiredBaseContract() throws IOException {
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
                    "test/ExtendingCapability.java",
                    """
                        package test;
                        
                        import %s;
                        
                        @PluginContract(
                            role = PluginContract.Role.CAPABILITY,
                            requires = BasePlugin.class
                        )
                        public interface ExtendingCapability extends BasePlugin {
                            int API_LEVEL = 2;
                        }
                        """.formatted(PluginContract.class.getCanonicalName())
                )
            ));
            
            assertTrue(result.success(), "Compilation should not fail");
        }
        
        @Test
        void failsWhenCapabilityContractExtendsNonRequiredBaseContract() throws IOException {
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
                    "test/ExtendingCapability.java",
                    """
                        package test;
                        
                        import %s;
                        
                        @PluginContract(
                            role = PluginContract.Role.CAPABILITY,
                            requires = BasePluginA.class
                        )
                        public interface ExtendingCapability extends BasePluginB {
                            int API_LEVEL = 2;
                        }
                        """.formatted(PluginContract.class.getCanonicalName())
                )
            ));
            
            assertFalse(result.success(), "Compilation should fail");
            assertDiagnosticContains(result, Diagnostic.Kind.ERROR, "extended base must match the required base");
        }
        
        @Test
        void compilesWhenCapabilityContractExtendsUnrelatedIntermediateInterface() throws IOException {
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
                    "test/Intermediate.java",
                    """
                        package test;
                        
                        import %s;
                        import %s;
                        
                        public interface Intermediate {
                            String test();
                        }
                        """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                ),
                source(
                    "test/ExtendsIntermediate.java",
                    """
                        package test;
                        
                        import %s;
                        
                        @PluginContract(
                            role = PluginContract.Role.CAPABILITY,
                            requires = BasePlugin.class
                        )
                        public interface ExtendsIntermediate extends BasePlugin, Intermediate {
                            int API_LEVEL = 2;
                            
                            default String test() {
                                return "test";
                            }
                        }
                        """.formatted(PluginContract.class.getCanonicalName())
                )
            ));
            
            assertTrue(result.success(), "Compilation should not fail");
        }
        
        @Test
        void failsWhenCapabilityContractIsExtendedByCapability() throws IOException {
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
                    "test/Capability.java",
                    """
                        package test;
                        
                        import %s;
                        import %s;
                        
                        @PluginContract(
                            role = PluginContract.Role.CAPABILITY,
                            requires = BasePlugin.class
                        )
                        public interface Capability extends Plugin {
                            int API_LEVEL = 1;
                        }
                        """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                ),
                source(
                    "test/DerivedCapability.java",
                    """
                        package test;
                        
                        import %s;
                        
                        @PluginContract(
                            role = PluginContract.Role.CAPABILITY,
                            requires = BasePlugin.class
                        )
                        public interface DerivedCapability extends Capability {
                            int API_LEVEL = 2;
                        }
                        """.formatted(PluginContract.class.getCanonicalName())
                )
            ));
            
            assertFalse(result.success(), "Compilation should fail");
            assertDiagnosticContains(result, Diagnostic.Kind.ERROR, "may not be extended");
        }
        
        @Test
        void failsWhenCapabilityDoesNotDeclareRequiredBaseContract() throws IOException {
            ProcessorTestCompiler.CompilationResult result = compiler.compile(List.of(
                source(
                    "test/MissingRequiresPlugin.java",
                    """
                        package test;
                        
                        import %s;
                        import %s;
                        
                        @PluginContract(role = PluginContract.Role.CAPABILITY)
                        public interface MissingRequiresPlugin extends Plugin {
                            int API_LEVEL = 1;
                        }
                        """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                )
            ));
            
            assertFalse(result.success(), "Compilation should fail");
            assertDiagnosticContains(result, Diagnostic.Kind.ERROR, "must require single base @PluginContract interface");
        }
        
        @Test
        void failsWhenCapabilityDeclaresEmptyRequiredBaseContract() throws IOException {
            ProcessorTestCompiler.CompilationResult result = compiler.compile(List.of(
                source(
                    "test/EmptyRequiresPlugin.java",
                    """
                        package test;
                        
                        import %s;
                        import %s;
                        
                        @PluginContract(
                            role = PluginContract.Role.CAPABILITY,
                            requires = {}
                        )
                        public interface EmptyRequiresPlugin extends Plugin {
                            int API_LEVEL = 1;
                        }
                        """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                )
            ));
            
            assertFalse(result.success(), "Compilation should fail");
            assertDiagnosticContains(result, Diagnostic.Kind.ERROR, "must require single base @PluginContract interface");
        }
        
        @Test
        void failsWhenCapabilityRequiresItself() throws IOException {
            ProcessorTestCompiler.CompilationResult result = compiler.compile(List.of(
                source(
                    "test/SelfReferencingCapability.java",
                    """
                    package test;
    
                    import %s;
                    import %s;
    
                    @PluginContract(
                        role = PluginContract.Role.CAPABILITY,
                        requires = { SelfReferencingCapability.class }
                    )
                    public interface SelfReferencingCapability extends Plugin {
                        int API_LEVEL = 1;
                    }
                    """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                )
            ));
            
            assertFalse(result.success(), "Compilation should fail");
            assertDiagnosticContains(result, Diagnostic.Kind.ERROR, "must require single base @PluginContract interface");
        }
        
        @Test
        void failsWhenCapabilityContractRequiresCapabilityNoBase() throws IOException {
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
                        
                        @PluginContract(
                            role = PluginContract.Role.CAPABILITY,
                            requires = BasePlugin.class
                        )
                        public interface CapabilityPlugin extends Plugin {
                            int API_LEVEL = 1;
                        }
                        """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                ),
                source(
                    "test/BadRequiringContract.java",
                    """
                        package test;
                        
                        import %s;
                        import %s;
                        
                        @PluginContract(
                            role = PluginContract.Role.CAPABILITY,
                            requires = { CapabilityPlugin.class }
                        )
                        public interface BadRequiringContract extends Plugin {
                            int API_LEVEL = 2;
                        }
                        """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName())
                )
            ));
            
            assertFalse(result.success(), "Compilation should fail");
            assertDiagnosticContains(result, Diagnostic.Kind.ERROR, "must require single base @PluginContract interface");
        }
        
        @Test
        void failsWhenContractRequiresMultipleCapabilityContracts() throws IOException {
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
                    "test/CapabilityPluginA.java",
                    """
                        package test;
                        
                        import %s;
                        import %s;
                        
                        @PluginContract(
                            role = PluginContract.Role.CAPABILITY,
                            requires = BasePlugin.class
                        )
                        public interface CapabilityPluginA extends Plugin {
                            int API_LEVEL = 1;
                        }
                        """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                ),
                source(
                    "test/CapabilityPluginB.java",
                    """
                        package test;
                        
                        import %s;
                        import %s;
                        
                        @PluginContract(
                            role = PluginContract.Role.CAPABILITY,
                            requires = BasePlugin.class
                        )
                        public interface CapabilityPluginB extends Plugin {
                            int API_LEVEL = 1;
                        }
                        """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                ),
                source(
                    "test/BadCapabilityContract.java",
                    """
                        package test;
                        
                        import %s;
                        import %s;
                        
                        @PluginContract(
                            role = PluginContract.Role.CAPABILITY,
                            requires = { CapabilityPluginA.class, CapabilityPluginB.class }
                        )
                        public interface BadCapabilityContract extends Plugin {
                            int API_LEVEL = 2;
                        }
                        """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName())
                )
            ));
            
            assertFalse(result.success(), "Compilation should fail");
            assertDiagnosticContains(result, Diagnostic.Kind.ERROR, "must require single base @PluginContract interface");
        }
        
        @Test
        void failsWhenCapabilityRequiresSameBaseTwice() throws IOException {
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
                    "test/BadCapability.java",
                    """
                    package test;
    
                    import %s;
                    import %s;
    
                    @PluginContract(
                        role = PluginContract.Role.CAPABILITY,
                        requires = { BasePlugin.class, BasePlugin.class }
                    )
                    public interface BadCapability extends Plugin {
                        int API_LEVEL = 1;
                    }
                    """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                )
            ));
            
            assertFalse(result.success(), "Compilation should fail");
            assertDiagnosticContains(result, Diagnostic.Kind.ERROR, "must require single base @PluginContract interface");
        }
        
        @Test
        void failsWhenContractRequiresConcretePluginClass() throws IOException {
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
                    "test/SomePluginImpl.java",
                    """
                    package test;

                    public class SomePluginImpl implements BasePlugin {
                        @Override
                        public String identity() {
                            return "impl";
                        }
                    }
                    """
                ),
                source(
                    "test/BadCapability.java",
                    """
                    package test;

                    import %s;
                    import %s;

                    @PluginContract(
                        role = PluginContract.Role.CAPABILITY,
                        requires = { SomePluginImpl.class }
                    )
                    public interface BadCapability extends Plugin {
                        int API_LEVEL = 1;
                    }
                    """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                )
            ));
            
            assertFalse(result.success(), "Compilation should fail");
            assertDiagnosticContains(result, Diagnostic.Kind.ERROR, "must require single base @PluginContract interface");
        }
        
        @Test
        void failsWhenContractDeclaresMultiplePluginContractAnnotations() throws IOException {
            ProcessorTestCompiler.CompilationResult result = compiler.compile(List.of(
                source(
                    "test/DuplicateAnnotatedPlugin.java",
                    """
                        package test;
                        
                        import %s;
                        import %s;
                        
                        @PluginContract(role = PluginContract.Role.BASE)
                        @PluginContract(role = PluginContract.Role.CAPABILITY)
                        public interface DuplicateAnnotatedPlugin extends Plugin {
                            int API_LEVEL = 1;
                        }
                        """.formatted(
                        Plugin.class.getCanonicalName(),
                        PluginContract.class.getCanonicalName()
                    )
                )
            ));
            
            assertFalse(result.success(), "Compilation should fail");
            assertDiagnosticContains(result, Diagnostic.Kind.ERROR, "PluginContract is not a repeatable annotation type");
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