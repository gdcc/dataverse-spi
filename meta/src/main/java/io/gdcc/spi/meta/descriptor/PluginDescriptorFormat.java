package io.gdcc.spi.meta.descriptor;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Defines constants for the format and structure of plugin descriptor files.
 * These descriptors provide metadata about plugins, including their
 * implementation class, type, contracts, and required providers.
 *
 * <ul>
 * - DESCRIPTOR_DIRECTORY: Specifies the directory where plugin descriptor files are located.
 * - PLUGIN_CLASS_FIELD: Defines the key used to identify the plugin's implementation class.
 * - PLUGIN_KIND_FIELD: Defines the key used to specify the base contract type of the plugin.
 * - CONTRACT_PREFIX and CONTRACT_SUFFIX: Define the keys used to denote the contracts
 *   implemented by the plugin and their associated API levels.
 * - REQUIRED_PROVIDER_PREFIX and REQUIRED_PROVIDER_SUFFIX: Define the keys used to represent
 *   required providers and their associated API levels.
 * </ul>
 */
public final class PluginDescriptorFormat {
    public static final String DESCRIPTOR_DIRECTORY = "META-INF/dataverse/plugins/";
    public static final String DESCRIPTOR_EXTENSION = ".properties";
    
    public static final String PLUGIN_CLASS_FIELD = "plugin.class";
    public static final String PLUGIN_KIND_FIELD = "plugin.kind";
    public static final String CONTRACT_PREFIX = "plugin.implements.";
    public static final String CONTRACT_SUFFIX = ".level";
    public static final String REQUIRED_PROVIDER_PREFIX = "plugin.requires.";
    public static final String REQUIRED_PROVIDER_SUFFIX = ".level";
    
    private PluginDescriptorFormat() {
        /* Intentionally left blank for helper class */
    }
    
    public static String toFilename(Class<?> klazz) {
        return toFilename(klazz.getCanonicalName());
    }
    
    public static String toFilename(String fqcn) {
        return fqcn + DESCRIPTOR_EXTENSION;
    }
    
    public static String toPath(Class<?> klazz) {
        return toPath(klazz.getCanonicalName());
    }
    
    public static String toPath(String fqcn) {
        return DESCRIPTOR_DIRECTORY + toFilename(fqcn);
    }
    
    public static String toContractLevel(Class<?> contractClass) {
        return toContractLevel(contractClass.getCanonicalName());
    }
    
    public static String toContractLevel(String contractFQCN) {
        return CONTRACT_PREFIX + contractFQCN + CONTRACT_SUFFIX;
    }
    
    public static String toRequiredProviderLevel(Class<?> providerClass) {
        return toRequiredProviderLevel(providerClass.getCanonicalName());
    }
    
    public static String toRequiredProviderLevel(String providerFQCN) {
        return REQUIRED_PROVIDER_PREFIX + providerFQCN + REQUIRED_PROVIDER_SUFFIX;
    }
    
    /**
     * Serializes the provided {@link PluginDescriptor} into the given {@link Writer}
     * in the form of a properties file, encoding plugin metadata such as plugin class,
     * plugin kind, implemented contracts, and required providers.
     *
     * @param descriptor the {@link PluginDescriptor} containing the plugin metadata to be serialized
     * @param writer the {@link Writer} where the descriptor properties will be written
     * @throws IOException if an I/O error occurs while writing to the {@link Writer}
     */
    public static void write(PluginDescriptor descriptor, Writer writer) throws IOException {
        Properties properties = new Properties();
        properties.setProperty(PLUGIN_CLASS_FIELD, descriptor.pluginClass());
        properties.setProperty(PLUGIN_KIND_FIELD, descriptor.pluginKind());
        
        descriptor.contracts().forEach((contract, level) ->
            properties.setProperty(toContractLevel(contract), Integer.toString(level)));
        
        descriptor.requiredProviders().forEach((provider, level) ->
            properties.setProperty(toRequiredProviderLevel(provider), Integer.toString(level)));
        
        properties.store(writer, "Generated plugin contract metadata");
    }
    
    /**
     * Reads a plugin descriptor from the serialized properties format.
     *
     * <p>The returned descriptor contains the mandatory plugin class and base contract fields,
     * plus all parsed contract/provider API levels found in the input.</p>
     *
     * @param reader the character stream containing descriptor properties
     * @return the parsed descriptor
     * @throws IOException if the properties cannot be read
     * @throws IllegalArgumentException if mandatory fields are missing or if any level value
     *         cannot be parsed as an integer
     */
    public static PluginDescriptor read(Reader reader) throws IOException {
        Properties properties = new Properties();
        properties.load(reader);
        
        String pluginClass = properties.getProperty(PLUGIN_CLASS_FIELD);
        if (pluginClass == null || pluginClass.isBlank()) {
            throw new IllegalArgumentException("Missing required property " + PLUGIN_CLASS_FIELD);
        }
        
        String pluginKind = properties.getProperty(PLUGIN_KIND_FIELD);
        if (pluginKind == null || pluginKind.isBlank()) {
            throw new IllegalArgumentException("Missing required property " + PLUGIN_KIND_FIELD);
        }
        
        Map<String, Integer> contracts = new LinkedHashMap<>();
        Map<String, Integer> requiredProviders = new LinkedHashMap<>();
        
        for (String key : properties.stringPropertyNames()) {
            if (PLUGIN_CLASS_FIELD.equals(key) || PLUGIN_KIND_FIELD.equals(key)) {
                continue;
            }
            
            if (key.startsWith(CONTRACT_PREFIX) && key.endsWith(CONTRACT_SUFFIX)) {
                String contractName = key.substring(
                    CONTRACT_PREFIX.length(),
                    key.length() - CONTRACT_SUFFIX.length()
                );
                contracts.put(contractName, parseLevel(properties.getProperty(key), key));
            }
            
            if (key.startsWith(REQUIRED_PROVIDER_PREFIX) && key.endsWith(REQUIRED_PROVIDER_SUFFIX)) {
                String providerName = key.substring(
                    REQUIRED_PROVIDER_PREFIX.length(),
                    key.length() - REQUIRED_PROVIDER_SUFFIX.length()
                );
                requiredProviders.put(providerName, parseLevel(properties.getProperty(key), key));
            }
        }
        
        return new PluginDescriptor(
            pluginClass,
            pluginKind,
            Map.copyOf(contracts),
            Map.copyOf(requiredProviders)
        );
    }
    
    /**
     * Reads a plugin descriptor from the given string content.
     *
     * This method parses the input string into a {@link PluginDescriptor} object. It internally utilizes
     * a {@link StringReader} to read the string and expects the content to be in a properties-based serialized format.
     *
     * @param content the string content containing serialized descriptor properties
     * @return the parsed {@link PluginDescriptor}
     * @throws RuntimeException if an I/O error occurs
     * @throws IllegalArgumentException if mandatory fields are missing
     */
    public static PluginDescriptor read(String content) {
        PluginDescriptor descriptor = null;
        
        try (StringReader reader = new StringReader(content)) {
            descriptor = read(reader);
        } catch (IOException e) {
            // As we read from an in-memory string, this seems highly unlikely to happen.
            throw new RuntimeException(e);
        }
        
        return descriptor;
    }
    
    private static int parseLevel(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing level value for property " + key);
        }
        
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid integer value for property " + key + ": " + value, e);
        }
    }
}
