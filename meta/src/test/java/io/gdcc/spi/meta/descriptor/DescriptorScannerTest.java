package io.gdcc.spi.meta.descriptor;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DescriptorScannerTest {

    @TempDir
    Path tempDir;

    @Nested
    class Directory {
        
        @Test
        void scanDirectory_ReturnsEmpty_WhenDescriptorDirectoryDoesNotExist() throws IOException {
            List<Descriptor> descriptors = DescriptorScanner.scanDirectory(tempDir);
            
            assertTrue(descriptors.isEmpty());
        }
        
        @Test
        void scanDirectory_ReadsSingleDescriptor() throws IOException {
            Path descriptorDir = tempDir.resolve(DescriptorFormat.DESCRIPTOR_DIRECTORY);
            Files.createDirectories(descriptorDir);
            
            Path descriptorFile = descriptorDir.resolve("test.Plugin.properties");
            Files.writeString(
                descriptorFile,
                """
                    plugin.class=test.Plugin
                    plugin.kind=test.BasePlugin
                    plugin.implements.test.BasePlugin.level=1
                    plugin.requires.test.Provider.level=2
                    """,
                StandardCharsets.UTF_8
            );
            
            List<Descriptor> descriptors = DescriptorScanner.scanDirectory(tempDir);
            
            assertEquals(1, descriptors.size());
            Descriptor descriptor = descriptors.get(0);
            assertEquals("test.Plugin", descriptor.klass());
            assertEquals("test.BasePlugin", descriptor.kind());
            assertEquals(1, descriptor.contractLevel("test.BasePlugin"));
            assertEquals(2, descriptor.requiredProviderLevel("test.Provider"));
        }
        
        @Test
        void scanDirectory_IgnoresNonPropertyFiles() throws IOException {
            Path descriptorDir = tempDir.resolve(DescriptorFormat.DESCRIPTOR_DIRECTORY);
            Files.createDirectories(descriptorDir);
            
            Files.writeString(
                descriptorDir.resolve("test.Plugin.properties"),
                """
                    plugin.class=test.Plugin
                    plugin.kind=test.BasePlugin
                    """,
                StandardCharsets.UTF_8
            );
            Files.writeString(descriptorDir.resolve("README.txt"), "ignore me", StandardCharsets.UTF_8);
            
            List<Descriptor> descriptors = DescriptorScanner.scanDirectory(tempDir);
            
            assertEquals(1, descriptors.size());
            assertEquals("test.Plugin", descriptors.get(0).klass());
        }
        
        @Test
        void scanDirectory_RejectsNonDirectory() throws IOException {
            Path file = Files.createTempFile(tempDir, "not-a-directory", ".txt");
            
            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> DescriptorScanner.scanDirectory(file)
            );
            
            assertTrue(ex.getMessage().contains("not a readable directory"));
        }
    }
    
    @Nested
    class Jar {
        
        @Test
        void scanJar_ReadsSingleDescriptor() throws IOException {
            Path jar = createJar(Map.of(
                "META-INF/dataverse/plugins/test.Plugin.properties",
                """
                    plugin.class=test.Plugin
                    plugin.kind=test.BasePlugin
                    plugin.implements.test.BasePlugin.level=3
                    plugin.requires.test.Provider.level=4
                    """,
                "META-INF/services/test.BasePlugin",
                "test.Plugin"
            ));
            
            List<Descriptor> descriptors = DescriptorScanner.scanJar(jar);
            
            assertEquals(1, descriptors.size());
            Descriptor descriptor = descriptors.get(0);
            assertEquals("test.Plugin", descriptor.klass());
            assertEquals("test.BasePlugin", descriptor.kind());
            assertEquals(3, descriptor.contractLevel("test.BasePlugin"));
            assertEquals(4, descriptor.requiredProviderLevel("test.Provider"));
        }
        
        @Test
        void scanJar_ReadsMultipleDescriptors() throws IOException {
            Path jar = createJar(Map.of(
                "META-INF/dataverse/plugins/test.A.properties",
                """
                    plugin.class=test.A
                    plugin.kind=test.BasePlugin
                    plugin.implements.test.BasePlugin.level=1
                    """,
                "META-INF/dataverse/plugins/test.B.properties",
                """
                    plugin.class=test.B
                    plugin.kind=test.BasePlugin
                    plugin.implements.test.BasePlugin.level=2
                    """
            ));
            
            List<Descriptor> descriptors = DescriptorScanner.scanJar(jar);
            
            assertEquals(2, descriptors.size());
        }
        
        @Test
        void scanJar_IgnoresNonDescriptorEntries() throws IOException {
            Path jar = createJar(Map.of(
                "META-INF/dataverse/plugins/test.Plugin.properties",
                """
                    plugin.class=test.Plugin
                    plugin.kind=test.BasePlugin
                    """,
                "META-INF/dataverse/plugins/README.txt",
                "ignore me",
                "some/other/resource.txt",
                "ignore me too"
            ));
            
            List<Descriptor> descriptors = DescriptorScanner.scanJar(jar);
            
            assertEquals(1, descriptors.size());
            assertEquals("test.Plugin", descriptors.get(0).klass());
        }
        
        @Test
        void scanJar_RejectsNonJarFile() throws IOException {
            Path file = Files.createTempFile(tempDir, "not-a-jar", ".txt");
            
            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> DescriptorScanner.scanJar(file)
            );
            
            assertTrue(ex.getMessage().contains("not a readable JAR file"));
        }
    }

    private Path createJar(Map<String, String> entries) throws IOException {
        Path jar = Files.createTempFile(tempDir, "plugin-plugin-test-", ".jar");

        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                JarEntry jarEntry = new JarEntry(entry.getKey());
                out.putNextEntry(jarEntry);
                out.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }

        return jar;
    }
}
