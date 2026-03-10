package io.gdcc.spi.export;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static io.gdcc.spi.export.FileMetadataPredicates.*;
import static org.junit.jupiter.api.Assertions.*;

class FileMetadataPredicatesTest {
    
    @Test
    void checkForwardAndBackwardConflicts_All_None() {
        Set<FileMetadataPredicates> predicates = Set.of(ALL_FILES, SKIP_FILES);
        Set<FileMetadataPredicates> conflicts = FileMetadataPredicates.checkConflicts(predicates);
        assertEquals(2, conflicts.size(), conflicts::toString);
    }
    
    @Test
    void checkForwardAndBackwardConflicts_All_SthElse() {
        Set<FileMetadataPredicates> predicates = Set.of(ALL_FILES, ONLY_PUBLIC_FILES);
        Set<FileMetadataPredicates> conflicts = FileMetadataPredicates.checkConflicts(predicates);
        assertEquals(2, conflicts.size(), conflicts::toString);
    }
    
    @Test
    void checkNoConflicts_Public_Tabular() {
        Set<FileMetadataPredicates> predicates = Set.of(ONLY_PUBLIC_FILES, ONLY_TABULAR_FILES);
        Set<FileMetadataPredicates> conflicts = FileMetadataPredicates.checkConflicts(predicates);
        assertTrue(conflicts.isEmpty(), conflicts::toString);
    }
}