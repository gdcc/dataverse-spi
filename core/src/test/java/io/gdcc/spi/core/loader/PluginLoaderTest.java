package io.gdcc.spi.core.loader;

import io.gdcc.spi.core.test.basic.TestContract;
import io.gdcc.spi.meta.annotations.PluginContract;
import io.gdcc.spi.meta.descriptor.DescriptorFormat;
import io.gdcc.spi.meta.plugin.Plugin;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginLoaderTest {
    
    @Nested
    class ValidateBaseClass {
        @Test
        void validatePluginBaseClass_validBaseClass() {
            assertDoesNotThrow(() -> PluginLoader.validatePluginBaseClass(TestContract.class));
        }
        
        @Test
        void validatePluginBaseClass_invalidBaseClass_unrelated() {
            // Given
            interface UnrelatedInterfaceNotExtendingPlugin {
            }
            
            // When & Then
            assertThrows(IllegalArgumentException.class, () -> PluginLoader.validatePluginBaseClass(UnrelatedInterfaceNotExtendingPlugin.class));
        }
        
        @Test
        void validatePluginBaseClass_invalidBaseClass_wrongType() {
            // Given
            class NotAnInterfacePlugin {
            }
            
            // When & Then
            assertThrows(IllegalArgumentException.class, () -> PluginLoader.validatePluginBaseClass(NotAnInterfacePlugin.class));
        }
        
        @Test
        void validatePluginBaseClass_invalidBaseClass_wrongRole() {

            // NOTE:
            // This local interface bypasses the annotation processor intentionally.
            // The processor would reject this at compile time in real source files.
            // This test verifies the runtime validation in PluginLoader.
            
            // Given
            @PluginContract(role = PluginContract.Role.CAPABILITY)
            interface IncorrectRolePlugin extends Plugin {
            }
            
            // When & Then
            assertThrows(IllegalArgumentException.class, () -> PluginLoader.validatePluginBaseClass(IncorrectRolePlugin.class));
        }
    }
    
    @Nested
    class FindSources {
        @Test
        void findSources_HappyPath() {
            // Given
            Class<TestContract> sut = TestContract.class;
            PluginLoader<?> loader = new PluginLoader<>(sut);
            Path directory = Path.of("target/test-classes/" + sut.getPackageName().replaceAll("\\.", "/"));
            
            // When
            Map<Path, URL[]> sources = assertDoesNotThrow(() -> loader.findSources(directory));
            
            // Then
            assertFalse(sources.isEmpty());
            assertTrue(sources.containsKey(directory));
        }
        
        @Test
        void findSources_NoSuchFile() {
            // Given
            Class<TestContract> sut = TestContract.class;
            PluginLoader<?> loader = new PluginLoader<>(sut);
            Path directory = Path.of("nosuchdir");
            
            // When
            LoaderException ex = assertThrows(LoaderException.class, () -> loader.findSources(directory));
            
            // Then
            assertEquals("NoSuchFileException: nosuchdir", ex.getProblems().get(0).message());
        }
        
        @Test
        void findSources_NoDirectory() {
            // Given
            Class<TestContract> sut = TestContract.class;
            PluginLoader<?> loader = new PluginLoader<>(sut);
            Path notDirectory = Path.of("target/test-classes/" + DescriptorFormat.transformClassName(sut).replaceAll("\\.", "/") + ".class");
            
            // When
            LoaderException ex = assertThrows(LoaderException.class, () -> loader.findSources(notDirectory));
            
            // Then
            assertEquals("NotDirectoryException: " + notDirectory, ex.getProblems().get(0).message());
        }
    }
    
    @Nested
    class Preload {
        
        LoaderConfiguration enforcingConfig = LoaderConfiguration.defaults();
        
        LoaderConfiguration permissiveConfig = LoaderConfiguration.permissive();
        
        @Test
        void preLoad_throwsOnNormalProblemsWhenEnforcing() {
            // given
            Class<TestContract> sut = TestContract.class;
            PluginLoader<TestContract> loader = new PluginLoader<>(sut, enforcingConfig);
            Path rootClassPath = Path.of("target/test-classes/");
            // Should generate class name conflict with core
            SourceScanner scanner = source -> List.of(DescriptorBuilder.aDescriptor().build());
            
            // when
            var exception = assertThrows(LoaderException.class, () -> loader.preloadPlugins(Set.of(rootClassPath), scanner));
            
            // then
            assertInstanceOf(LoaderProblem.PluginClassNameCollisionWithCore.class, exception.getProblems().get(0));
        }
        
        @Test
        void preLoad_throwsOnIOExceptionsWhenEnforcing() {
            // given
            Class<TestContract> sut = TestContract.class;
            PluginLoader<TestContract> loader = new PluginLoader<>(sut, enforcingConfig);
            Path rootClassPath = Path.of("target/test-classes/");
            // Should generate class name conflict with core
            SourceScanner scanner = source -> {
                throw new IOException("Test exception");
            };
            
            // when
            var exception = assertThrows(LoaderException.class, () -> loader.preloadPlugins(Set.of(rootClassPath), scanner));
            
            // then
            assertInstanceOf(LoaderProblem.LocationFailure.class, exception.getProblems().get(0));
        }
        
        @Test
        void preLoad_continuesOnProblemsWhenPermissive() {
            // given
            Class<TestContract> sut = TestContract.class;
            PluginLoader<TestContract> loader = new PluginLoader<>(sut, permissiveConfig);
            Path rootClassPath = Path.of("target/test-classes/");
            // Should generate class name conflict with core
            SourceScanner scanner = source -> List.of(DescriptorBuilder.aDescriptor().build());
            
            // when
            var result = loader.preloadPlugins(Set.of(rootClassPath), scanner);
            
            // then
            assertEquals(0, result.size());
        }
    }
    
    @Test
    void load() throws MalformedURLException {
        // Given
        Class<TestContract> sut = TestContract.class;
        PluginLoader<TestContract> loader = new PluginLoader<>(sut);
        Path rootClassPath = Path.of("target/test-classes/");
        
        /*
        Map<Path,URL[]> sources = Map.of(rootClassPath, new URL[]{PluginLoader.pathToUrl(rootClassPath, null, null)});
        
        // When
        Map<PluginOrigin,TestContract> plugins = loader.load(sources);
        
        // Then
        assertFalse(plugins.isEmpty());
        List<PluginOrigin> pluginSources = plugins.keySet().stream().toList();
        assertEquals(1, pluginSources.size());
        PluginOrigin source = pluginSources.get(0);
        assertEquals(rootClassPath, source.location());
        assertEquals(TestPlugin.class.getName(), source.className());
        assertEquals(new TestPlugin().identity(), source.identity());
        
         */
    }
}