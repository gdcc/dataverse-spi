package io.gdcc.spi.core.metadata.description;

import io.gdcc.spi.core.metadata.Namespace;

import java.net.URI;
import java.util.Objects;

/**
 * Identifies a field by namespace and local name.
 * Two FieldIds are equal only if both namespace and local name match.
 */
public final class FieldId {
    
    private final Namespace namespace;
    private final String localName;
    
    private FieldId(Namespace namespace, String localName) {
        this.namespace = Objects.requireNonNull(namespace, "namespace must not be null");
        this.localName = Objects.requireNonNull(localName, "localName must not be null");
        if (localName.isBlank()) {
            throw new IllegalArgumentException("localName cannot be empty or blank");
        }
    }
    
    /**
     * Creates a new {@code FieldId} instance with the specified namespace and local name.
     *
     * @param namespace the namespace associated with the field, must not be null
     * @param localName the local name of the field within the namespace, must not be null or blank
     * @return a new {@code FieldId} instance with the given namespace and local name
     * @throws NullPointerException if {@code namespace} or {@code localName} is null
     * @throws IllegalArgumentException if {@code localName} is blank
     */
    public static FieldId of(Namespace namespace, String localName) {
        return new FieldId(namespace, localName);
    }
    
    public Namespace getNamespace() {
        return namespace;
    }
    
    public String getLocalName() {
        return localName;
    }
    
    /**
     * Resolve to fully expanded URI.
     */
    public URI toUri() {
        return namespace.resolve(localName);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FieldId that)) return false;
        return namespace.equals(that.namespace) && localName.equals(that.localName);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(namespace, localName);
    }
    
    @Override
    public String toString() {
        return toUri().toString();
    }
}