package io.gdcc.spi.export;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Enum representing predicates for filtering file metadata during export operations.
 * Each predicate defines criteria for including or excluding specific types of files.
 * Conflicts between predicates are explicitly defined to prevent ambiguous or contradictory filtering rules.
 * Any predicates should follow the pattern (SKIP|ONLY|INCLUDE)_[ADJECTIVE]_[OBJECT].
 */
public enum FileMetadataPredicates {
    // NOTE: We can only define backward conflicts, as forward conflicts would lead
    //       to circular dependencies disallowed by the Java compiler.
    
    /**
     * Includes metadata for all files without restriction.
     * Conflicts with any other predicate selecting files.
     */
    ALL_FILES(),
    /**
     * Excludes metadata for all files.
     * Conflicts with any other file selecting predicate.
     */
    SKIP_FILES(ALL_FILES),
    /**
     * Only include files with public visibility.
     * Conflicts with {@link #ALL_FILES} and {@link #SKIP_FILES}.
     */
    ONLY_PUBLIC_FILES(ALL_FILES, SKIP_FILES),
    /**
     * Only include tabular data files.
     * Conflicts with {@link #ALL_FILES} and {@link #SKIP_FILES}.
     */
    ONLY_TABULAR_FILES(ALL_FILES, SKIP_FILES),
    /**
     * For tabular data files, control if variable details are included or not.
     * (That can be huge and heterogeneous data with slow DB queries!)
     * It has no conflicting predicates, as it is about detail inclusion, not file selection.
     */
    INCLUDE_TABULAR_DATA_VARIABLES()
    ;
    
    /**
     * When adding new predicates to the enum, make sure to add any entry which is about *metadata*
     * of files and not the *selection* of files to this set. It is used to ensure any {@link FileExportQuery}
     * has at least one *selection* predicate present.
     */
    private static final Set<FileMetadataPredicates> fileMetadataRelated = Set.of(INCLUDE_TABULAR_DATA_VARIABLES);
    
    /**
     * Determines if the given {@code FileMetadataPredicates} relates to file metadata rather than file selection.
     *
     * @param predicate the {@code FileMetadataPredicates} to check
     * @return {@code true} if the provided predicate is contained in the set of file metadata-related predicates,
     *         {@code false} otherwise
     */
    public static boolean relatesToFileMetadata(FileMetadataPredicates predicate) {
        return fileMetadataRelated.contains(predicate);
    }
    
    final Set<FileMetadataPredicates> conflictingPredicates;
    
    FileMetadataPredicates(FileMetadataPredicates... conflictingPredicates) {
       this.conflictingPredicates = Set.of(conflictingPredicates);
    }
    
    public boolean conflictsWith(FileMetadataPredicates p) {
        if (p == null) {
            return false;
        }
        return conflictingPredicates.contains(p);
    }
    
    /**
     * Checks for conflicts among the given set of export file predicates.
     * A predicate is considered conflicting if it has a conflict relationship with
     * any other predicate defined in the {@link FileMetadataPredicates} enum.
     *
     * @param predicates the set of predicates to check for conflicts
     * @return an unmodifiable set of predicates from the input that conflict with at least one other predicate (empty if no conflict)
     */
    @SuppressWarnings("java:S2259")
    public static Set<FileMetadataPredicates> checkConflicts(Set<FileMetadataPredicates> predicates) {
        Set<FileMetadataPredicates> foundConflicts = new HashSet<>();
        
        // Iterate via O(n^2) through all predicates to check any existing predicate for a conflict.
        // This way, a forward check is enough, as we iterate through the cartesian product.
        for (FileMetadataPredicates predicate : predicates) {
            for (FileMetadataPredicates compare : predicates) {
                if (predicate.conflictsWith(compare) || compare.conflictsWith(predicate)) {
                    foundConflicts.add(predicate);
                    foundConflicts.add(compare);
                }
            }
        }
        
        return Collections.unmodifiableSet(foundConflicts);
    }
    
}
