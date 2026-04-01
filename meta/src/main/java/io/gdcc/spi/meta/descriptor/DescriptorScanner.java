package io.gdcc.spi.meta.descriptor;

import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static io.gdcc.spi.meta.descriptor.DescriptorFormat.DESCRIPTOR_DIRECTORY;
import static io.gdcc.spi.meta.descriptor.DescriptorFormat.DESCRIPTOR_EXTENSION;
import static io.gdcc.spi.meta.descriptor.DescriptorFormat.read;

public final class DescriptorScanner {
    
    private DescriptorScanner() {
        /* Intentionally private constructor for helper class without instances */
    }
    
    /**
     * Scans the specified path to identify plugin descriptors. The path can either be a directory
     * or a JAR file. The method determines the type of the path and invokes the appropriate
     * scanning logic to extract plugin descriptors.
     *
     * @param path the path to be scanned; must not be null. If the path represents a directory,
     *             plugin files contained within it will be scanned. If the path represents
     *             a JAR file, its internal entries will be scanned for descriptors.
     * @return a list of {@code SourcedPluginDescriptor} objects representing plugin descriptors
     *         found at the given path. The list will be empty if no descriptors are found.
     * @throws IllegalArgumentException if the provided {@code path} is {@code null} or scanning fails for other reasons caused by user.
     * @throws IOException if an I/O error occurs while accessing the specified path or its contents.
     */
    public static List<SourcedDescriptor> scanPath(Path path) throws IOException {
        List<SourcedDescriptor> scanResult = new ArrayList<>();
        
        if (path == null) {
            throw new IllegalArgumentException("Path may not be null");
        }
        if (Files.isDirectory(path)) {
            scanDirectory(path).forEach(plugin -> scanResult.add(new SourcedDescriptor(path, plugin)));
        } else {
            scanJar(path).forEach(plugin -> scanResult.add(new SourcedDescriptor(path, plugin)));
        }
        
        return List.copyOf(scanResult);
    }
    
    /**
     * Scans the specified JAR file for plugin plugin entries and extracts them into a list of
     * {@link Descriptor} objects. The method looks for plugin plugin files based on predefined
     * directory and file extension constants.
     *
     * @param jarPath the path to the JAR file to be scanned; must be a valid, readable, and regular file
     *                with a ".jar" extension. Usage of symbolic links is allowed.
     * @return a list of {@code PluginDescriptor} objects extracted from the JAR file. If no plugin
     *         descriptors are found, the returned list will be empty.
     * @throws IllegalArgumentException if the provided {@code jarPath} is {@code null}, does not exist,
     *                                  is unreadable, is not a regular file, or does not have a ".jar"
     *                                  extension.
     * @throws IOException if an I/O error occurs while reading the JAR file or its entries.
     */
    static List<Descriptor> scanJar(Path jarPath) throws IOException {
        if (jarPath == null || !Files.exists(jarPath) || !Files.isReadable(jarPath) ||
            !Files.isRegularFile(jarPath) || !jarPath.getFileName().toString().toLowerCase().endsWith(".jar")) {
            throw new IllegalArgumentException("jarPath '" + jarPath + "' is not a readable JAR file");
        }
        
        // Iterate over the entries in the JAR file, read the ones we know to be plugin descriptors
        List<Descriptor> descriptors = new ArrayList<>();
        try (var jarFile = new JarFile(jarPath.toFile())) {
            for (Iterator<JarEntry> it = jarFile.entries().asIterator(); it.hasNext(); ) {
                JarEntry entry = it.next();
                String name = entry.getName();
                
                if (name.startsWith(DESCRIPTOR_DIRECTORY) && name.endsWith(DESCRIPTOR_EXTENSION)) {
                    try(InputStreamReader reader = new InputStreamReader(jarFile.getInputStream(entry), StandardCharsets.UTF_8)) {
                        Descriptor descriptor = read(reader);
                        descriptors.add(descriptor);
                    }
                }
            }
        }
        
        return List.copyOf(descriptors);
    }
    
    /**
     * Scans the specified directory for plugin plugin files and extracts them into a list of
     * {@link Descriptor} objects. The method searches for plugin files in a predefined
     * subdirectory and processes files with a specific file extension.
     *
     * @param root the root directory to be scanned; must be a valid, readable, and existing directory.
     * @return a list of {@code PluginDescriptor} objects extracted from the directory. If no plugin
     *         descriptors are found, the returned list will be empty.
     * @throws IllegalArgumentException if the provided {@code root} is {@code null}, does not exist,
     *                                  is unreadable, or is not a directory.
     * @throws IOException if an I/O error occurs while reading the directory or its contents.
     */
    static List<Descriptor> scanDirectory(Path root) throws IOException {
        if (root == null || !Files.exists(root) || !Files.isReadable(root) || !Files.isDirectory(root)) {
            throw new IllegalArgumentException("directory '" + root + "' is not a readable directory");
        }
        
        // Look up the plugin metadata directory - if it does not exist, there are no plugins here.
        Path descriptorDir = root.resolve(DescriptorFormat.DESCRIPTOR_DIRECTORY);
        if (!Files.isDirectory(descriptorDir)) {
            return List.of();
        }
        
        // Scan the directory for plugin metadata, read it, and add it to a list
        List<Descriptor> descriptors = new ArrayList<>();
        try (var paths = Files.list(descriptorDir)) {
            for (Path path : paths.toList()) {
                String name = path.getFileName().toString();
                
                if (name.endsWith(DESCRIPTOR_EXTENSION)) {
                    try (FileReader reader = new FileReader(path.toFile(), StandardCharsets.UTF_8)) {
                        Descriptor descriptor = read(reader);
                        descriptors.add(descriptor);
                    }
                }
            }
        }
        
        return List.copyOf(descriptors);
    }
    
    // TODO: a method to check that for a given plugin class and plugin kind there is a service loader entry present
    
}
