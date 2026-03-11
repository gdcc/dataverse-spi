package io.gdcc.spi.meta.descriptor;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginDescriptorFormatTest {
    
    @Nested
    class Fields {
        @Test
        void toFilename_UsesDescriptorExtension_ForString() {
            String result = PluginDescriptorFormat.toFilename("io.gdcc.example.MyPlugin");
            
            assertEquals("io.gdcc.example.MyPlugin.properties", result);
        }
        
        @Test
        void toFilename_UsesDescriptorExtension_ForClass() {
            String result = PluginDescriptorFormat.toFilename(SamplePlugin.class);
            
            assertEquals("io.gdcc.spi.meta.descriptor.PluginDescriptorFormatTest.SamplePlugin.properties", result);
        }
        
        @Test
        void toPath_PrependsDescriptorDirectory_ForString() {
            String result = PluginDescriptorFormat.toPath("io.gdcc.example.MyPlugin");
            
            assertEquals(
                "META-INF/dataverse/plugins/io.gdcc.example.MyPlugin.properties",
                result
            );
        }
        
        @Test
        void toPath_PrependsDescriptorDirectory_ForClass() {
            String result = PluginDescriptorFormat.toPath(SamplePlugin.class);
            
            assertEquals(
                "META-INF/dataverse/plugins/io.gdcc.spi.meta.descriptor.PluginDescriptorFormatTest.SamplePlugin.properties",
                result
            );
        }
        
        @Test
        void toContractLevel_CreatesExpectedPropertyKey_ForString() {
            String result = PluginDescriptorFormat.toContractLevel("io.gdcc.example.ExportPlugin");
            
            assertEquals(
                "plugin.implements.io.gdcc.example.ExportPlugin.level",
                result
            );
        }
        
        @Test
        void toContractLevel_CreatesExpectedPropertyKey_ForClass() {
            String result = PluginDescriptorFormat.toContractLevel(SampleContract.class);
            
            assertEquals(
                "plugin.implements.io.gdcc.spi.meta.descriptor.PluginDescriptorFormatTest.SampleContract.level",
                result
            );
        }
        
        @Test
        void toRequiredProviderLevel_CreatesExpectedPropertyKey_ForString() {
            String result = PluginDescriptorFormat.toRequiredProviderLevel("io.gdcc.example.ExportProvider");
            
            assertEquals(
                "plugin.requires.io.gdcc.example.ExportProvider.level",
                result
            );
        }
        
        @Test
        void toRequiredProviderLevel_CreatesExpectedPropertyKey_ForClass() {
            String result = PluginDescriptorFormat.toRequiredProviderLevel(SampleProvider.class);
            
            assertEquals(
                "plugin.requires.io.gdcc.spi.meta.descriptor.PluginDescriptorFormatTest.SampleProvider.level",
                result
            );
        }
    }
    
    @Nested
    class Write {
        @Test
        void write_WritesCoreFieldsContractsAndProviders() throws IOException {
            PluginDescriptor descriptor = new PluginDescriptor(
                "io.gdcc.example.MyPlugin",
                "io.gdcc.example.ExportPlugin",
                Map.of(
                    "io.gdcc.example.ExportPlugin", 2,
                    "io.gdcc.example.XmlCapability", 1
                ),
                Map.of(
                    "io.gdcc.example.ExportProvider", 5
                )
            );
            
            StringWriter writer = new StringWriter();
            
            PluginDescriptorFormat.write(descriptor, writer);
            
            Properties properties = loadProperties(writer.toString());
            
            assertEquals("io.gdcc.example.MyPlugin", properties.getProperty(PluginDescriptorFormat.PLUGIN_CLASS_FIELD));
            assertEquals("io.gdcc.example.ExportPlugin", properties.getProperty(PluginDescriptorFormat.PLUGIN_KIND_FIELD));
            assertEquals("2", properties.getProperty("plugin.implements.io.gdcc.example.ExportPlugin.level"));
            assertEquals("1", properties.getProperty("plugin.implements.io.gdcc.example.XmlCapability.level"));
            assertEquals("5", properties.getProperty("plugin.requires.io.gdcc.example.ExportProvider.level"));
        }
        
        @Test
        void write_WritesCoreFields_WhenContractsAndProvidersAreEmpty() throws IOException {
            PluginDescriptor descriptor = new PluginDescriptor(
                "io.gdcc.example.MinimalPlugin",
                "io.gdcc.example.ExportPlugin",
                Map.of(),
                Map.of()
            );
            
            StringWriter writer = new StringWriter();
            
            PluginDescriptorFormat.write(descriptor, writer);
            
            Properties properties = loadProperties(writer.toString());
            
            assertEquals("io.gdcc.example.MinimalPlugin", properties.getProperty(PluginDescriptorFormat.PLUGIN_CLASS_FIELD));
            assertEquals("io.gdcc.example.ExportPlugin", properties.getProperty(PluginDescriptorFormat.PLUGIN_KIND_FIELD));
            assertEquals(2, properties.size(), "Only the two mandatory core fields should be present");
        }
        
        @Test
        void write_UsesHelperGeneratedPropertyKeys() throws IOException {
            PluginDescriptor descriptor = new PluginDescriptor(
                "io.gdcc.example.MyPlugin",
                "io.gdcc.example.ExportPlugin",
                Map.of("io.gdcc.example.ExportPlugin", 7),
                Map.of("io.gdcc.example.ExportProvider", 11)
            );
            
            StringWriter writer = new StringWriter();
            
            PluginDescriptorFormat.write(descriptor, writer);
            
            String serialized = writer.toString();
            
            assertTrue(serialized.contains(PluginDescriptorFormat.toContractLevel("io.gdcc.example.ExportPlugin") + "=7"));
            assertTrue(serialized.contains(PluginDescriptorFormat.toRequiredProviderLevel("io.gdcc.example.ExportProvider") + "=11"));
        }
        
        @Test
        void stringAndClassOverloadsProduceEquivalentResults() {
            assertEquals(
                PluginDescriptorFormat.toFilename(SamplePlugin.class),
                PluginDescriptorFormat.toFilename(SamplePlugin.class.getCanonicalName())
            );
            
            assertEquals(
                PluginDescriptorFormat.toPath(SamplePlugin.class),
                PluginDescriptorFormat.toPath(SamplePlugin.class.getCanonicalName())
            );
            
            assertEquals(
                PluginDescriptorFormat.toContractLevel(SampleContract.class),
                PluginDescriptorFormat.toContractLevel(SampleContract.class.getCanonicalName())
            );
            
            assertEquals(
                PluginDescriptorFormat.toRequiredProviderLevel(SampleProvider.class),
                PluginDescriptorFormat.toRequiredProviderLevel(SampleProvider.class.getCanonicalName())
            );
        }
        
        private static Properties loadProperties(String text) throws IOException {
            Properties properties = new Properties();
            properties.load(new java.io.StringReader(text));
            return properties;
        }
    }
    
    @Nested
    class Read {
        @Test
        void read_ReadsMandatoryFieldsAndEmptyMaps() throws IOException {
            String serialized = """
            plugin.class=io.gdcc.example.MyPlugin
            plugin.kind=io.gdcc.example.ExportPlugin
            """;
            
            PluginDescriptor descriptor = PluginDescriptorFormat.read(new StringReader(serialized));
            
            assertEquals("io.gdcc.example.MyPlugin", descriptor.pluginClass());
            assertEquals("io.gdcc.example.ExportPlugin", descriptor.pluginKind());
            assertEquals(Map.of(), descriptor.contracts());
            assertEquals(Map.of(), descriptor.requiredProviders());
        }
        
        @Test
        void read_ReadsContractsAndRequiredProviders() throws IOException {
            String serialized = """
            plugin.class=io.gdcc.example.MyPlugin
            plugin.kind=io.gdcc.example.ExportPlugin
            plugin.implements.io.gdcc.example.ExportPlugin.level=2
            plugin.implements.io.gdcc.example.XmlCapability.level=1
            plugin.requires.io.gdcc.example.ExportProvider.level=5
            plugin.requires.io.gdcc.example.BatchProvider.level=9
            """;
            
            PluginDescriptor descriptor = PluginDescriptorFormat.read(new StringReader(serialized));
            
            assertEquals("io.gdcc.example.MyPlugin", descriptor.pluginClass());
            assertEquals("io.gdcc.example.ExportPlugin", descriptor.pluginKind());
            assertEquals(
                Map.of(
                    "io.gdcc.example.ExportPlugin", 2,
                    "io.gdcc.example.XmlCapability", 1
                ),
                descriptor.contracts()
            );
            assertEquals(
                Map.of(
                    "io.gdcc.example.ExportProvider", 5,
                    "io.gdcc.example.BatchProvider", 9
                ),
                descriptor.requiredProviders()
            );
        }
        
        @Test
        void read_IgnoresUnknownProperties() throws IOException {
            String serialized = """
            plugin.class=io.gdcc.example.MyPlugin
            plugin.kind=io.gdcc.example.ExportPlugin
            plugin.implements.io.gdcc.example.ExportPlugin.level=2
            plugin.requires.io.gdcc.example.ExportProvider.level=5
            plugin.something.unrelated=value
            unrelated.field=42
            """;
            
            PluginDescriptor descriptor = PluginDescriptorFormat.read(new StringReader(serialized));
            
            assertEquals("io.gdcc.example.MyPlugin", descriptor.pluginClass());
            assertEquals("io.gdcc.example.ExportPlugin", descriptor.pluginKind());
            assertEquals(Map.of("io.gdcc.example.ExportPlugin", 2), descriptor.contracts());
            assertEquals(Map.of("io.gdcc.example.ExportProvider", 5), descriptor.requiredProviders());
        }
        
        @Test
        void read_FailsWhenPluginClassIsMissing() {
            String serialized = """
            plugin.kind=io.gdcc.example.ExportPlugin
            plugin.implements.io.gdcc.example.ExportPlugin.level=2
            """;
            
            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> PluginDescriptorFormat.read(new StringReader(serialized))
            );
            
            assertEquals("Missing required property plugin.class", ex.getMessage());
        }
        
        @Test
        void read_FailsWhenPluginKindIsMissing() {
            String serialized = """
            plugin.class=io.gdcc.example.MyPlugin
            plugin.implements.io.gdcc.example.ExportPlugin.level=2
            """;
            
            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> PluginDescriptorFormat.read(new StringReader(serialized))
            );
            
            assertEquals("Missing required property plugin.kind", ex.getMessage());
        }
        
        @Test
        void read_FailsWhenContractLevelIsNotAnInteger() {
            String serialized = """
            plugin.class=io.gdcc.example.MyPlugin
            plugin.kind=io.gdcc.example.ExportPlugin
            plugin.implements.io.gdcc.example.ExportPlugin.level=not-a-number
            """;
            
            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> PluginDescriptorFormat.read(new StringReader(serialized))
            );
            
            assertEquals(
                "Invalid integer value for property plugin.implements.io.gdcc.example.ExportPlugin.level: not-a-number",
                ex.getMessage()
            );
        }
        
        @Test
        void read_FailsWhenRequiredProviderLevelIsNotAnInteger() {
            String serialized = """
            plugin.class=io.gdcc.example.MyPlugin
            plugin.kind=io.gdcc.example.ExportPlugin
            plugin.requires.io.gdcc.example.ExportProvider.level=nope
            """;
            
            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> PluginDescriptorFormat.read(new StringReader(serialized))
            );
            
            assertEquals(
                "Invalid integer value for property plugin.requires.io.gdcc.example.ExportProvider.level: nope",
                ex.getMessage()
            );
        }
        
        @Test
        void read_RoundTripsWithWrite() throws IOException {
            PluginDescriptor original = new PluginDescriptor(
                "io.gdcc.example.MyPlugin",
                "io.gdcc.example.ExportPlugin",
                Map.of(
                    "io.gdcc.example.ExportPlugin", 2,
                    "io.gdcc.example.XmlCapability", 1
                ),
                Map.of(
                    "io.gdcc.example.ExportProvider", 5
                )
            );
            
            StringWriter writer = new StringWriter();
            PluginDescriptorFormat.write(original, writer);
            
            PluginDescriptor reread = PluginDescriptorFormat.read(new StringReader(writer.toString()));
            
            assertEquals(original.pluginClass(), reread.pluginClass());
            assertEquals(original.pluginKind(), reread.pluginKind());
            assertEquals(original.contracts(), reread.contracts());
            assertEquals(original.requiredProviders(), reread.requiredProviders());
        }
    }
    
    
    private static final class SamplePlugin {
    }
    
    private interface SampleContract {
    }
    
    private interface SampleProvider {
    }
}