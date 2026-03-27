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

class DescriptorFormatTest {
    
    @Nested
    class Fields {
        @Test
        void toFilename_UsesDescriptorExtension_ForString() {
            String result = DescriptorFormat.toFilename("io.gdcc.example.MyPlugin");
            
            assertEquals("io.gdcc.example.MyPlugin.properties", result);
        }
        
        @Test
        void toFilename_UsesDescriptorExtension_ForClass() {
            String result = DescriptorFormat.toFilename(SamplePlugin.class);
            
            assertEquals("io.gdcc.spi.meta.descriptor.DescriptorFormatTest.SamplePlugin.properties", result);
        }
        
        @Test
        void toPath_PrependsDescriptorDirectory_ForString() {
            String result = DescriptorFormat.toPath("io.gdcc.example.MyPlugin");
            
            assertEquals(
                "META-INF/dataverse/plugins/io.gdcc.example.MyPlugin.properties",
                result
            );
        }
        
        @Test
        void toPath_PrependsDescriptorDirectory_ForClass() {
            String result = DescriptorFormat.toPath(SamplePlugin.class);
            
            assertEquals(
                "META-INF/dataverse/plugins/io.gdcc.spi.meta.descriptor.DescriptorFormatTest.SamplePlugin.properties",
                result
            );
        }
        
        @Test
        void toContractLevel_CreatesExpectedPropertyKey_ForString() {
            String result = DescriptorFormat.toContractLevel("io.gdcc.example.ExportPlugin");
            
            assertEquals(
                "plugin.implements.io.gdcc.example.ExportPlugin.level",
                result
            );
        }
        
        @Test
        void toContractLevel_CreatesExpectedPropertyKey_ForClass() {
            String result = DescriptorFormat.toContractLevel(SampleContract.class);
            
            assertEquals(
                "plugin.implements.io.gdcc.spi.meta.descriptor.DescriptorFormatTest$SampleContract.level",
                result
            );
        }
        
        @Test
        void toRequiredProviderLevel_CreatesExpectedPropertyKey_ForString() {
            String result = DescriptorFormat.toRequiredProviderLevel("io.gdcc.example.ExportProvider");
            
            assertEquals(
                "plugin.requires.io.gdcc.example.ExportProvider.level",
                result
            );
        }
        
        @Test
        void toRequiredProviderLevel_CreatesExpectedPropertyKey_ForClass() {
            String result = DescriptorFormat.toRequiredProviderLevel(SampleProvider.class);
            
            assertEquals(
                "plugin.requires.io.gdcc.spi.meta.descriptor.DescriptorFormatTest$SampleProvider.level",
                result
            );
        }
    }
    
    @Nested
    class Write {
        @Test
        void write_WritesCoreFieldsContractsAndProviders() throws IOException {
            Descriptor descriptor = new Descriptor(
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
            
            DescriptorFormat.write(descriptor, writer);
            
            Properties properties = loadProperties(writer.toString());
            
            assertEquals("io.gdcc.example.MyPlugin", properties.getProperty(DescriptorFormat.PLUGIN_CLASS_FIELD));
            assertEquals("io.gdcc.example.ExportPlugin", properties.getProperty(DescriptorFormat.PLUGIN_KIND_FIELD));
            assertEquals("2", properties.getProperty("plugin.implements.io.gdcc.example.ExportPlugin.level"));
            assertEquals("1", properties.getProperty("plugin.implements.io.gdcc.example.XmlCapability.level"));
            assertEquals("5", properties.getProperty("plugin.requires.io.gdcc.example.ExportProvider.level"));
        }
        
        @Test
        void write_WritesCoreFields_WhenContractsAndProvidersAreEmpty() throws IOException {
            Descriptor descriptor = new Descriptor(
                "io.gdcc.example.MinimalPlugin",
                "io.gdcc.example.ExportPlugin",
                Map.of(),
                Map.of()
            );
            
            StringWriter writer = new StringWriter();
            
            DescriptorFormat.write(descriptor, writer);
            
            Properties properties = loadProperties(writer.toString());
            
            assertEquals("io.gdcc.example.MinimalPlugin", properties.getProperty(DescriptorFormat.PLUGIN_CLASS_FIELD));
            assertEquals("io.gdcc.example.ExportPlugin", properties.getProperty(DescriptorFormat.PLUGIN_KIND_FIELD));
            assertEquals(2, properties.size(), "Only the two mandatory core fields should be present");
        }
        
        @Test
        void write_UsesHelperGeneratedPropertyKeys() throws IOException {
            Descriptor descriptor = new Descriptor(
                "io.gdcc.example.MyPlugin",
                "io.gdcc.example.ExportPlugin",
                Map.of("io.gdcc.example.ExportPlugin", 7),
                Map.of("io.gdcc.example.ExportProvider", 11)
            );
            
            StringWriter writer = new StringWriter();
            
            DescriptorFormat.write(descriptor, writer);
            
            String serialized = writer.toString();
            
            assertTrue(serialized.contains(DescriptorFormat.toContractLevel("io.gdcc.example.ExportPlugin") + "=7"));
            assertTrue(serialized.contains(DescriptorFormat.toRequiredProviderLevel("io.gdcc.example.ExportProvider") + "=11"));
        }
        
        @Test
        void stringAndClassOverloadsProduceEquivalentResults() {
            assertEquals(
                DescriptorFormat.toFilename(SamplePlugin.class),
                DescriptorFormat.toFilename(SamplePlugin.class.getName())
            );
            
            assertEquals(
                DescriptorFormat.toPath(SamplePlugin.class),
                DescriptorFormat.toPath(SamplePlugin.class.getName())
            );
            
            assertEquals(
                DescriptorFormat.toContractLevel(SampleContract.class),
                DescriptorFormat.toContractLevel(SampleContract.class.getName())
            );
            
            assertEquals(
                DescriptorFormat.toRequiredProviderLevel(SampleProvider.class),
                DescriptorFormat.toRequiredProviderLevel(SampleProvider.class.getName())
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
            
            Descriptor descriptor = DescriptorFormat.read(new StringReader(serialized));
            
            assertEquals("io.gdcc.example.MyPlugin", descriptor.klass());
            assertEquals("io.gdcc.example.ExportPlugin", descriptor.kind());
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
            
            Descriptor descriptor = DescriptorFormat.read(new StringReader(serialized));
            
            assertEquals("io.gdcc.example.MyPlugin", descriptor.klass());
            assertEquals("io.gdcc.example.ExportPlugin", descriptor.kind());
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
            
            Descriptor descriptor = DescriptorFormat.read(new StringReader(serialized));
            
            assertEquals("io.gdcc.example.MyPlugin", descriptor.klass());
            assertEquals("io.gdcc.example.ExportPlugin", descriptor.kind());
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
                () -> DescriptorFormat.read(new StringReader(serialized))
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
                () -> DescriptorFormat.read(new StringReader(serialized))
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
                () -> DescriptorFormat.read(new StringReader(serialized))
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
                () -> DescriptorFormat.read(new StringReader(serialized))
            );
            
            assertEquals(
                "Invalid integer value for property plugin.requires.io.gdcc.example.ExportProvider.level: nope",
                ex.getMessage()
            );
        }
        
        @Test
        void read_RoundTripsWithWrite() throws IOException {
            Descriptor original = new Descriptor(
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
            DescriptorFormat.write(original, writer);
            
            Descriptor reread = DescriptorFormat.read(new StringReader(writer.toString()));
            
            assertEquals(original.klass(), reread.klass());
            assertEquals(original.kind(), reread.kind());
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