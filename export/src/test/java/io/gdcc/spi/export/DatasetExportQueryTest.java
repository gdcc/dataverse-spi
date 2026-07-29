package io.gdcc.spi.export;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatasetExportQueryTest {
    
    /**
     * Tests the builder method with an empty builder.
     * Verifies that the default fileQuery is set to "none" when no explicit fileQuery is provided.
     */
    @Test
    void testBuilderWithDefaults() {
        DatasetExportQuery result = DatasetExportQuery.builder().build();
        
        assertNotNull(result);
        assertTrue(result.predicates().isEmpty());
        assertEquals(FileExportQuery.none(), result.fileQuery());
    }
}