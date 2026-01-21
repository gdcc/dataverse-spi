package io.gdcc.spi.core.metadata.primitives;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Represents an email address with an optional personal / display name.
 * Provides basic structural validation without external dependencies.
 * Does not perform deep validation (DNS checks, deliverability) but ensures
 * the address has a reasonable format (local-part@domain.tld).
 * Does not do RFC822 validation, will allow any charsets.
 */
public final class Email {
    
    // Pragmatic pattern: local-part @ domain . TLD (at least 2 chars)
    // Intentionally permissive - catches typos but doesn't reject edge cases
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$"
    );
    
    private final String address;
    private final String personal;  // aka display name
    
    private Email(String address, String personal) {
        this.address = address;
        this.personal = personal;
    }
    
    /**
     * Creates an Email from just an address.
     *
     * @param address the email address string
     * @return an Email instance
     * @throws NullPointerException if address is null
     * @throws IllegalArgumentException if address is blank or malformed
     */
    public static Email of(String address) {
        return of(address, null);
    }
    
    /**
     * Creates an Email with address and personal/display name.
     *
     * @param address the email address string
     * @param personal the display name (e.g., "John Doe"), may be null
     * @return an Email instance
     * @throws NullPointerException if address is null
     * @throws IllegalArgumentException if address is blank or malformed
     */
    public static Email of(String address, String personal) {
        Objects.requireNonNull(address, "email address cannot be null");
        String normalized = address.trim();
        
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("email address cannot be empty");
        }
        
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("invalid email address format: '" + address + "'");
        }
        
        String normalizedPersonal = personal != null ? personal.trim() : null;
        if (normalizedPersonal != null && normalizedPersonal.isEmpty()) {
            normalizedPersonal = null;  // treat empty string as null
        }
        
        return new Email(normalized, normalizedPersonal);
    }
    
    public String getAddress() {
        return address;
    }
    
    public Optional<String> getPersonal() {
        return Optional.ofNullable(personal);
    }
    
    /**
     * Returns formatted email with personal name if present.
     * Examples:
     * - "user@example.com" (no personal)
     * - "John Doe <user@example.com>" (with personal)
     */
    @Override
    public String toString() {
        if (personal != null) {
            return personal + " <" + address + ">";
        }
        return address;
    }
    
    /**
     * Returns RFC 2822 formatted string (suitable for email headers).
     * Same as toString() but explicitly named for clarity.
     */
    public String toRfc2822() {
        return toString();
    }
    
    /**
     * Compares this Email object to another object for equality.
     * Two Email objects are considered equal if their email addresses are equal
     * when compared in a case-insensitive manner, as per RFC 5321.
     *
     * @param o the object to compare with this Email instance
     * @return true if the specified object is an Email and has the same
     *         address (case-insensitive), false otherwise
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Email email)) return false;
        // Email addresses are case-insensitive per RFC 5321.
        return address.equalsIgnoreCase(email.address);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(address.toLowerCase(), personal);
    }
}