package io.gdcc.spi.export;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Predicates for controlling which dataset metadata is included in an export
 * and at what level of detail.
 * <p>
 * These predicates are used in a {@link DatasetExportQuery} to shape dataset-level
 * retrieval. They are exclusively concerned with dataset-level concerns — file-level
 * filtering is handled separately by {@link FileMetadataPredicates} via
 * {@link FileExportQuery}.
 * <p>
 * Predicates may conflict with each other; use {@link #checkConflicts(Set)} to
 * validate a combination before use.
 *
 * @see DatasetExportQuery
 * @see FileMetadataPredicates
 */
public enum DatasetMetadataPredicates {
    // NOTE: We can only define backward conflicts, as forward conflicts would lead
    //       to circular dependencies disallowed by the Java compiler.
    
    // Placeholder — dataset-level predicates to be added here as requirements emerge.
    // Examples of future candidates:
    //   PUBLISHED_DATASETS_ONLY  — restrict to published versions
    //   DRAFT_INCLUDED           — include draft versions
    //   METADATA_BLOCKS_ONLY     — exclude file metadata entirely
    ;
    
    final Set<DatasetMetadataPredicates> conflicts;
    
    DatasetMetadataPredicates(DatasetMetadataPredicates... predicates) {
        this.conflicts = Set.of(predicates);
    }
    
    /**
     * Returns {@code true} if this predicate conflicts with the given predicate.
     *
     * @param p the predicate to check against; {@code null} is safe and returns {@code false}
     * @return {@code true} if a conflict exists, {@code false} otherwise
     */
    public boolean conflictsWith(DatasetMetadataPredicates p) {
        if (p == null) {
            return false;
        }
        return conflicts.contains(p);
    }
    
    /**
     * Checks for conflicts among the given set of dataset metadata predicates.
     * A predicate is considered conflicting if it has a conflict relationship with
     * any other predicate in the set.
     *
     * @param predicates the set of predicates to check for conflicts
     * @return an unmodifiable set of predicates from the input that conflict with at
     *         least one other predicate; empty if no conflicts exist
     */
    @SuppressWarnings("java:S2259")
    public static Set<DatasetMetadataPredicates> checkConflicts(Set<DatasetMetadataPredicates> predicates) {
        Set<DatasetMetadataPredicates> foundConflicts = new HashSet<>();
        
        for (DatasetMetadataPredicates predicate : predicates) {
            for (DatasetMetadataPredicates compare : predicates) {
                if (predicate.conflictsWith(compare) || compare.conflictsWith(predicate)) {
                    foundConflicts.add(predicate);
                    foundConflicts.add(compare);
                }
            }
        }
        
        return Collections.unmodifiableSet(foundConflicts);
    }
}