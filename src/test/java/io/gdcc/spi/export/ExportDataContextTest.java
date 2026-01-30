package io.gdcc.spi.export;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExportDataContextTest {
    
    @Test
    void testBuildThrowsExceptionForNegativeOffset() {
        // Given
        var builder = ExportDataContext.builder().offset(-5);
        // When & Then
        assertThrows(IllegalArgumentException.class, builder::build);
    }
    
    @Test
    void testBuildThrowsExceptionForNegativeLength() {
        // Given
        var builder = ExportDataContext.builder().length(-5);
        // When & Then
        assertThrows(IllegalArgumentException.class, builder::build);
    }
    
}