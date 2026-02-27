package io.gdcc.spi.core.loader;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * This record captures metadata about where a plugin was discovered and loaded from, associating
 * 1) the plugin's logical identity (as returned by {@link io.gdcc.spi.core.plugin.Plugin#identity()}),
 * 2) the plugin's class name, and
 * 3) the JAR file path containing the plugin implementation.
 */
public record PluginSource(Path location, String className, String identity) {
    
    @Override
    public String toString() {
        return String.format("%s: className=%s, identity=%s", location, className, identity);
    }
    
    /**
     * Checks if this PluginSource is a duplicate of another based on ANY of:
     * - Same class name
     * - Same normalized identity (case-insensitive, separators removed)
     *
     * "Same location" doesn't count as a duplicate to enable loading multiple plugins from the same location.
     *
     * While {@link #equals(Object)} checks for strict logical equality (important for {@code Set} or {@code Map}),
     * this method is targeted at detecting logical duplicates (that may have different locations) but share
     * other identifying characteristics.
     */
    public boolean isDuplicateOf(PluginSource other) {
        if (other == null) return false;
        return Objects.equals(className, other.className) ||
               Objects.equals(normalizeIdentity(identity), normalizeIdentity(other.identity));
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        PluginSource that = (PluginSource) obj;
        
        return Objects.equals(location, that.location) &&
               Objects.equals(className, that.className) &&
               Objects.equals(normalizeIdentity(identity), normalizeIdentity(that.identity));
    }
    
    /**
     * Normalizes the given identity string for comparison purposes by converting it to lowercase
     * and removing all occurrences of the characters "/\-_:.#~*" which are commonly used to separate words.
     *
     * @param identity the identity string to normalize
     * @return the normalized identity string, or null if the input is null
     */
    private String normalizeIdentity(String identity) {
        if (identity == null) return null;
        return identity.toLowerCase().replaceAll("[/\\\\_\\-:.#~*]+", "");
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(location, className, normalizeIdentity(identity));
    }
    
    /**
     * Groups duplicate {@link PluginSource} instances together based on shared identifying characteristics.
     * Two sources are considered duplicates if they have the same class name or the same normalized identity
     * (case-insensitive, with common separator characters removed), as determined by {@link PluginSource#isDuplicateOf(PluginSource)}.
     * Sources at the same location are NOT considered duplicates, allowing multiple plugins to be loaded from the same file.
     *
     * @param sources the set of plugin sources to group; may be empty or contain duplicates
     * @return a set of disjoint groups, where each group is a set of mutually duplicate sources
     */
    public static Set<Set<PluginSource>> groupDuplicates(Set<PluginSource> sources) {
        Set<Set<PluginSource>> groups = new LinkedHashSet<>();
        // Create a copy of the sources as a working set
        Set<PluginSource> remaining = new HashSet<>(sources);
        
        // Iterate over all remaining sources
        while (!remaining.isEmpty()) {
            PluginSource source = remaining.iterator().next();
            Set<PluginSource> group = new LinkedHashSet<>();
            
            // Check against every remaining source if this is a new duplicate
            for (PluginSource candidate : remaining) {
                if (source.isDuplicateOf(candidate)) {
                    group.add(candidate);
                }
            }
            
            // Add the group to the result set and remove any found duplicates from the working set (so they are not checked again)
            groups.add(group);
            remaining.removeAll(group);
        }
        
        return groups;
    }
}
