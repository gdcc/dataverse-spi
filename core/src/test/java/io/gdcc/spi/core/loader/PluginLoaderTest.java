package io.gdcc.spi.core.loader;

import io.gdcc.spi.core.plugin.Plugin;
import io.gdcc.spi.core.test.TestPlugin;
import org.junit.jupiter.api.Test;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginLoaderTest {
    
    @Test
    void findSources_HappyPath() {
        // Given
        Class<TestPlugin> sut = TestPlugin.class;
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
        Class<TestPlugin> sut = TestPlugin.class;
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
        Class<TestPlugin> sut = TestPlugin.class;
        PluginLoader<?> loader = new PluginLoader<>(sut);
        Path notDirectory = Path.of("target/test-classes/" + sut.getCanonicalName().replaceAll("\\.", "/") + ".class");
        
        // When
        LoaderException ex = assertThrows(LoaderException.class, () -> loader.findSources(notDirectory));
        
        // Then
        assertEquals("NotDirectoryException: " + notDirectory, ex.getProblems().get(0).message());
    }
    
    @Test
    void load() throws MalformedURLException {
        // Given
        Class<Plugin> sut = Plugin.class;
        PluginLoader<Plugin> loader = new PluginLoader<>(sut);
        Path rootClassPath = Path.of("target/test-classes/");
        Map<Path,URL[]> sources = Map.of(rootClassPath, new URL[]{PluginLoader.pathToUrl(rootClassPath, null, null)});
        
        // When
        Map<PluginSource,Plugin> plugins = loader.load(sources);
        
        // Then
        assertFalse(plugins.isEmpty());
        List<PluginSource> pluginSources = plugins.keySet().stream().toList();
        assertEquals(1, pluginSources.size());
        PluginSource source = pluginSources.get(0);
        assertEquals(rootClassPath, source.location());
        assertEquals(TestPlugin.class.getName(), source.className());
        assertEquals(new TestPlugin().identity(), source.identity());
    }
}