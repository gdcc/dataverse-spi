package io.gdcc.spi.export;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageRequestTest {
    
    /**
     * Tests for the `of` method in the `PageRequest` class.
     * The `of` method is responsible for creating a new `PageRequest` object with the given offset and limit values.
     * This test class ensures various scenarios for valid and invalid inputs are handled correctly.
     */
    
    @Test
    void testOf_createsValidPageRequest() {
        // Arrange & Act
        PageRequest pageRequest = PageRequest.of(10, 20);
        
        // Assert
        assertEquals(10, pageRequest.getOffset());
        assertEquals(20, pageRequest.getLimit());
        assertTrue(pageRequest.isPaged());
    }
    
    @Test
    void testOf_withZeroOffsetAndValidLimit() {
        // Arrange & Act
        PageRequest pageRequest = PageRequest.of(0, 5);
        
        // Assert
        assertEquals(0, pageRequest.getOffset());
        assertEquals(5, pageRequest.getLimit());
        assertTrue(pageRequest.isPaged());
    }
    
    @Test
    void testOf_throwsExceptionForNegativeOffset() {
        // Arrange & Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> PageRequest.of(-1, 10)
        );
        assertEquals("Offset must be >= 0, was: -1", exception.getMessage());
    }
    
    @Test
    void testOf_throwsExceptionForZeroOrNegativeLimit() {
        // Arrange & Act & Assert
        IllegalArgumentException exception1 = assertThrows(
            IllegalArgumentException.class,
            () -> PageRequest.of(5, 0)
        );
        assertEquals("Limit must be >= 1, was: 0", exception1.getMessage());
        
        IllegalArgumentException exception2 = assertThrows(
            IllegalArgumentException.class,
            () -> PageRequest.of(5, -1)
        );
        assertEquals("Limit must be >= 1, was: -1", exception2.getMessage());
    }
}