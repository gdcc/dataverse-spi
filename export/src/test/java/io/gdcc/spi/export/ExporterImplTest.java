
package io.gdcc.spi.export;

import io.gdcc.spi.export.fixtures.StubDdiExporter;
import io.gdcc.spi.export.fixtures.StubJsonExporter;
import io.gdcc.spi.meta.descriptor.Descriptor;
import io.gdcc.spi.meta.descriptor.DescriptorFormat;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the annotation processor generates correct descriptors and service files
 * when compiling real Exporter SPI implementations.
 *
 * <p>The test implementation classes in this package are compiled with the processor on the
 * classpath. The processor writes descriptors and service files into {@code target/test-classes/},
 * which this test reads at runtime to verify correctness.</p>
 */
class ExporterImplTest {
    
    @Test
    void generatesDescriptorAndServiceFileForBaseExporterImplementation() throws IOException {
        Class<?> implClass = StubJsonExporter.class;
        
        Descriptor descriptor = readDescriptor(implClass);
        assertNotNull(descriptor, "Descriptor should be generated for " + implClass);
        
        assertEquals(DescriptorFormat.transformClassName(implClass), descriptor.klass());
        assertEquals(Exporter.class.getCanonicalName(), descriptor.kind());
        assertEquals(Exporter.API_LEVEL, descriptor.contractLevel(Exporter.class.getCanonicalName()));
        assertEquals(ExportDataProvider.API_LEVEL, descriptor.requiredProviderLevel(ExportDataProvider.class.getCanonicalName()));
        
        String serviceFile = readServiceFile(Exporter.class);
        assertNotNull(serviceFile, "Service file should be generated for Exporter");
        assertTrue(serviceFile.contains(DescriptorFormat.transformClassName(implClass)), "Service file should contain " + implClass);
    }
    
    @Test
    void generatesDescriptorWithBaseAndCapabilityForXmlExporterImplementation() throws IOException {
        Class<?> implClass = StubDdiExporter.class;
        
        Descriptor descriptor = readDescriptor(implClass);
        assertNotNull(descriptor, "Descriptor should be generated for " + implClass);
        
        assertEquals(DescriptorFormat.transformClassName(implClass), descriptor.klass());
        assertEquals(Exporter.class.getCanonicalName(), descriptor.kind());
        assertEquals(Exporter.API_LEVEL, descriptor.contractLevel(Exporter.class.getCanonicalName()));
        assertEquals(XMLExporter.API_LEVEL, descriptor.contractLevel(XMLExporter.class.getCanonicalName()));
        assertEquals(ExportDataProvider.API_LEVEL, descriptor.requiredProviderLevel(ExportDataProvider.class.getCanonicalName()));
        
        String serviceFile = readServiceFile(Exporter.class);
        assertNotNull(serviceFile, "Service file should be generated for Exporter");
        assertTrue(serviceFile.contains(DescriptorFormat.transformClassName(implClass)), "Service file should contain " + implClass);
    }
    
    @Test
    void doesNotGenerateServiceFileForXmlExporterCapability() {
        String serviceFile = readServiceFile(XMLExporter.class);
        assertNull(serviceFile, "Service file must never be generated for capability contract XMLExporter");
    }
    
    @Test
    void xmlExporterDefaultMediaTypeSatisfiesBaseContract() throws IOException {
        // StubDdiExporter implements XMLExporter (which extends Exporter) and does NOT
        // override getMediaType(). Because XMLExporter extends Exporter in the Java type
        // hierarchy, the default on XMLExporter satisfies the abstract declaration on Exporter.
        // If this were not the case, compilation would have failed and no descriptor would exist.
        Class<?> implClass = StubDdiExporter.class;
        
        Descriptor descriptor = readDescriptor(implClass);
        assertNotNull(descriptor, "Descriptor should exist, proving compilation succeeded without explicit getMediaType() override");
    }
    
    // ── Helpers ─────────────────────────────────────────────────────────────────
    
    private Descriptor readDescriptor(Class implClass) throws IOException {
        String resourcePath = DescriptorFormat.toPath(implClass);
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                return null;
            }
            return DescriptorFormat.read(new String(is.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
    
    private String readServiceFile(Class<?> serviceType) {
        String resourcePath = "META-INF/services/" + serviceType.getName();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                return null;
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }
}