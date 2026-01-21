package io.gdcc.spi.core.metadata;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

/**
 * A namespace with a base URI and a prefix for compact serialization and i18n lookups.
 * Typically defined at the metadata block level.
 */
public final class Namespace {
    
    private final URI baseUri;
    private final String prefix;
    
    private Namespace(URI baseUri, String prefix) {
        this.baseUri = baseUri;
        this.prefix = prefix;
    }
    
    /**
     * Creates a new {@code Namespace} instance with the specified base URI and prefix.
     *
     * @param baseUri the base URI of the namespace, must not be null
     * @param prefix the prefix for the namespace, must not be null
     * @return a new {@code Namespace} instance with the given base URI and prefix
     * @throws NullPointerException if the given base URI or prefix is null
     * @throws IllegalArgumentException if the given prefix is blank or the URI is not absolute (schemaless)
     */
    public static Namespace of(URI baseUri, String prefix) {
        Objects.requireNonNull(baseUri, "baseUri must not be null");
        // Validate that the URI is absolute (has a scheme)
        if (!baseUri.isAbsolute()) {
            throw new IllegalArgumentException("baseUri must be an absolute URI with a scheme: " + baseUri);
        }
        
        Objects.requireNonNull(prefix, "prefix must not be null");
        if (prefix.isBlank()) {
            throw new IllegalArgumentException("prefix cannot be blank");
        }
        
        return new Namespace(baseUri, prefix);
    }
    
    /**
     * Creates a new {@code Namespace} instance with the specified base URI as a string and prefix.
     *
     * @param baseUri the base URI of the namespace as a string, must not be null
     * @param prefix the prefix for the namespace, must not be null
     * @return a new {@code Namespace} instance with the given base URI and prefix
     * @throws NullPointerException if the given base URI string or prefix is null
     * @throws IllegalArgumentException if the given base URI string is not a valid and absolute URI or the prefix is blank
     */
    public static Namespace of(String baseUri, String prefix) {
        if (baseUri == null) {
            throw new NullPointerException("baseUri cannot be null");
        }
        if (baseUri.isBlank()) {
            throw new IllegalArgumentException("baseUri cannot be blank");
        }
        
        URI uri;
        try {
            uri = new URI(baseUri);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URI syntax: " + baseUri, e);
        }
        
        return of(uri, prefix);
    }
    
    public URI getBaseUri() { return baseUri; }
    public String getPrefix() { return prefix; }
    
    /**
     * Resolve a local name to a full URI within this namespace.
     * Supports both slash-based (http://example.org/ns/) and hash-based (http://example.org/ns#) URI patterns.
     * If the base URI doesn't end with '/' or '#', a '/' is automatically added.
     *
     * @param localName the local name to resolve, must not be null or blank
     * @return the resolved URI
     * @throws IllegalArgumentException if localName is null or blank
     */
    public URI resolve(String localName) {
        if (localName == null || localName.isBlank()) {
            throw new IllegalArgumentException("localName must not be null or blank");
        }
        String base = baseUri.toString();
        if (!base.endsWith("/") && !base.endsWith("#")) {
            base += "/";
        }
        return URI.create(base + localName);
    }
    
    /**
     * Format a local name as a compact IRI (e.g., "dc:title").
     */
    public String toCompactIri(String localName) {
        return prefix + ":" + localName;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Namespace that)) return false;
        return baseUri.equals(that.baseUri);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(baseUri);
    }
    
    @Override
    public String toString() {
        return prefix + " -> " + baseUri;
    }
}