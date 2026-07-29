package io.gdcc.spi.export;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static io.gdcc.spi.export.FileMetadataPredicates.ALL_FILES;
import static io.gdcc.spi.export.FileMetadataPredicates.INCLUDE_TABULAR_DATA_VARIABLES;
import static io.gdcc.spi.export.FileMetadataPredicates.SKIP_FILES;

class FileExportQueryTest {
    
    @Test
    void testBuilderWithSinglePredicate() {
        FileExportQuery query = FileExportQuery.builder()
            .addFilePredicate(ALL_FILES)
            .build();
        
        Assertions.assertNotNull(query);
        Assertions.assertEquals(Set.of(ALL_FILES), query.predicates());
    }
    
    @Test
    void testBuilderWithMultiplePredicates() {
        FileExportQuery query = FileExportQuery.builder()
            .filePredicates(ALL_FILES, INCLUDE_TABULAR_DATA_VARIABLES)
            .build();
        
        Assertions.assertNotNull(query);
        Assertions.assertEquals(Set.of(ALL_FILES, INCLUDE_TABULAR_DATA_VARIABLES), query.predicates());
    }
    
    @Test
    void testBuilderFromExistingQuery() {
        FileExportQuery baseQuery = FileExportQuery.builder()
            .addFilePredicate(ALL_FILES)
            .build();
        
        FileExportQuery newQuery = FileExportQuery.builder(baseQuery)
            .addFilePredicate(INCLUDE_TABULAR_DATA_VARIABLES)
            .build();
        
        Assertions.assertNotNull(newQuery);
        Assertions.assertEquals(Set.of(ALL_FILES, INCLUDE_TABULAR_DATA_VARIABLES), newQuery.predicates());
    }
    
    @Test
    void testBuilderThrowsExceptionForEmptyPredicates() {
        IllegalArgumentException exception = Assertions.assertThrows(IllegalArgumentException.class, () ->
            FileExportQuery.builder().build()
        );
        
        Assertions.assertEquals("At least one file metadata predicate must be given for a valid query.", exception.getMessage());
    }
    
    @Test
    void testBuilderThrowsExceptionForConflictingPredicates() {
        IllegalArgumentException exception = Assertions.assertThrows(IllegalArgumentException.class, () ->
            FileExportQuery.builder()
                .filePredicates(ALL_FILES, SKIP_FILES)
                .build()
        );
        
        Assertions.assertTrue(exception.getMessage().contains("Conflicting predicates detected"));
    }
    
    @Test
    void testBuilderThrowsExceptionWhenNoFileSelectionPredicatePresent() {
        IllegalArgumentException exception = Assertions.assertThrows(IllegalArgumentException.class, () ->
            FileExportQuery.builder()
                .filePredicates(INCLUDE_TABULAR_DATA_VARIABLES)
                .build()
        );
        
        Assertions.assertTrue(exception.getMessage().contains("At least one file metadata predicate must be about selection of files"));
    }
    
    @Test
    void testAllStaticMethodReturnsPredefinedQuery() {
        FileExportQuery query = FileExportQuery.all();
        
        Assertions.assertNotNull(query);
        Assertions.assertEquals(Set.of(ALL_FILES), query.predicates());
    }
    
    @Test
    void testNoneStaticMethodReturnsPredefinedQuery() {
        FileExportQuery query = FileExportQuery.none();
        
        Assertions.assertNotNull(query);
        Assertions.assertEquals(Set.of(SKIP_FILES), query.predicates());
    }
    
    @Test
    void testRequiresMethod() {
        FileExportQuery query = FileExportQuery.builder()
            .addFilePredicate(ALL_FILES)
            .build();
        
        Assertions.assertTrue(query.requires(ALL_FILES));
        Assertions.assertFalse(query.requires(SKIP_FILES));
    }
}