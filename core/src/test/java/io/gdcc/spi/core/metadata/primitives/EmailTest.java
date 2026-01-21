package io.gdcc.spi.core.metadata.primitives;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmailTest {
    
    /**
     * Tests for Email.of(String address) and Email.of(String address, String personal).
     * The Email class creates immutable objects representing an email address optionally associated with a personal name.
     */
    
    @Test
    void testEmailWithValidAddress() {
        Email email = Email.of("test@example.com");
        assertNotNull(email);
        assertEquals("test@example.com", email.getAddress());
        assertTrue(email.getPersonal().isEmpty());
    }
    
    @Test
    void testEmailWithValidAddressAndPersonal() {
        Email email = Email.of("test@example.com", "John Doe");
        assertNotNull(email);
        assertEquals("test@example.com", email.getAddress());
        assertTrue(email.getPersonal().isPresent());
        assertEquals("John Doe", email.getPersonal().get());
    }
    
    @Test
    void testEmailWithValidAddressAndEmptyPersonal() {
        Email email = Email.of("test@example.com", "");
        assertNotNull(email);
        assertEquals("test@example.com", email.getAddress());
        assertTrue(email.getPersonal().isEmpty());
    }
    
    @Test
    void testEmailWithTrailingSpacesInAddress() {
        Email email = Email.of("  test@example.com  ");
        assertNotNull(email);
        assertEquals("test@example.com", email.getAddress());
        assertTrue(email.getPersonal().isEmpty());
    }
    
    @Test
    void testEmailWithTrailingSpacesInAddressAndPersonal() {
        Email email = Email.of("  test@example.com  ", "  Jane Doe  ");
        assertNotNull(email);
        assertEquals("test@example.com", email.getAddress());
        assertTrue(email.getPersonal().isPresent());
        assertEquals("Jane Doe", email.getPersonal().get());
    }
    
    @Test
    void testEmailNullAddressThrowsException() {
        Exception exception = assertThrows(NullPointerException.class, () -> Email.of(null));
        assertEquals("email address cannot be null", exception.getMessage());
    }
    
    @Test
    void testEmailEmptyAddressThrowsException() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> Email.of("   "));
        assertEquals("email address cannot be empty", exception.getMessage());
    }
    
    @Test
    void testEmailInvalidAddressFormatThrowsException() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> Email.of("invalid-email"));
        assertEquals("invalid email address format: 'invalid-email'", exception.getMessage());
    }
    
    @Test
    void testEmailEqualityWithSameEmail() {
        Email email1 = Email.of("test@example.com");
        Email email2 = Email.of("Test@Example.com");
        assertEquals(email1, email2);
    }
    
    @Test
    void testEmailWithDifferentPersonalButSameAddressEquality() {
        Email email1 = Email.of("test@example.com", "John");
        Email email2 = Email.of("test@example.com", "Jane");
        assertEquals(email1, email2);
    }
    
    @Test
    void testEmailHashCodeWithSameAddress() {
        Email email1 = Email.of("test@example.com");
        Email email2 = Email.of("Test@Example.com");
        assertEquals(email1.hashCode(), email2.hashCode());
    }
    
    @Test
    void testEmailToStringWithoutPersonal() {
        Email email = Email.of("test@example.com");
        assertEquals("test@example.com", email.toString());
    }
    
    @Test
    void testEmailToStringWithPersonal() {
        Email email = Email.of("test@example.com", "John Doe");
        assertEquals("John Doe <test@example.com>", email.toString());
    }
    
    @Test
    void testEmailToRfc2822WithoutPersonal() {
        Email email = Email.of("test@example.com");
        assertEquals("test@example.com", email.toRfc2822());
    }
    
    @Test
    void testEmailToRfc2822WithPersonal() {
        Email email = Email.of("test@example.com", "John Doe");
        assertEquals("John Doe <test@example.com>", email.toRfc2822());
    }
}