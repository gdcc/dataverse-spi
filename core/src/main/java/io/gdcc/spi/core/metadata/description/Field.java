package io.gdcc.spi.core.metadata.description;

import io.gdcc.spi.core.metadata.Namespace;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Defines a metadata field: its identity, type, and display defaults.
 */
public final class Field {
    
    private final FieldId id;
    private final FieldType type;
    private final int generation;
    private final String displayName;
    private final String watermark;
    
    // We're only linking to children here, but not to parents. As at a later point the model must have
    // enough flexibility to be reused by multiple parents, let's not restrict here.
    private final Set<Field> children;
    
    private Field(FieldId id, FieldType type, int generation, String displayName,
                  String watermark, Set<Field> children) {
        this.id = id;
        this.type = type;
        this.generation = generation;
        this.displayName = displayName;
        this.watermark = watermark;
        this.children = children == null ? Set.of() : Set.copyOf(children);
    }
    
    public FieldId getId() { return id; }
    public FieldType getType() { return type; }
    public boolean isCompound() {
        return type == FieldType.COMPOUND;
    }
    
    /**
     * Retrieves the child fields associated with this field.
     * If there are no child fields, an empty set is returned.
     * @return a set containing the child fields, or an empty set if no child fields are defined
     */
    public Set<Field> getChildren() { return children; }
    /**
     * Retrieves the "generation" of this field, indicating the level of nesting within the metadata model.
     * @return an integer representing the generation of the field. Root equals 0, every level of nesting increments by 1.
     */
    public int getGeneration() { return generation; }
    public boolean hasChildren() { return !children.isEmpty(); }
    public boolean isRoot() { return generation == 0; }
    
    public Optional<String> getDisplayName() { return Optional.ofNullable(displayName); }
    public Optional<String> getWatermark() { return Optional.ofNullable(watermark); }
    
    public static Builder builder() {
        return new Builder();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Field that)) return false;
        return id.equals(that.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return "Field[" + id + ", type=" + type + ", gen=" + generation + "]";
    }
    
    /**
     * <p>A builder class for constructing instances of {@code Field}.
     * This class provides a fluent API for setting various properties
     * of a {@code Field} instance, including required properties like
     * {@code id} and {@code type}, as well as optional properties like
     * {@code displayName}, {@code watermark}, and child fields.</p>
     *
     * <p>Instances of {@code Builder} can only be used once to build a {@code Field}.
     * This ensures immutability and consistency in the resulting {@code Field} objects.</p>
     *
     * <p>The {@code Builder} supports hierarchical relationships by allowing
     * the addition of child builders, which are recursively built into
     * {@code Field} instances when the {@code build} method is invoked.</p>
     */
    public static final class Builder {
        private FieldId id;
        private FieldType type;
        private String displayName;
        private String watermark;
        private Set<Builder> children;
        
        // This marks a builder as consumed, as they are only allowed to be used once per builder.
        // Prevents building multiple fields from the same builder instance.
        private boolean consumed;
        
        // TODO: Extend this builder with a method to add a parsing context, so any exception can be put in context.
        //       This will require a wrapper exception type or handing the builder something to add errors to.
        
        public Builder() {}
        
        // Getters (for external validation)
        public Optional<FieldId> getId() { return Optional.ofNullable(id); }
        public Optional<FieldType> getType() { return Optional.ofNullable(type); }
        public Optional<String> getDisplayName() { return Optional.ofNullable(displayName); }
        public Optional<String> getWatermark() { return Optional.ofNullable(watermark); }
        public Set<Builder> getChildren() { return children == null ? Set.of() : Set.copyOf(children); }
        public boolean isConsumed() { return consumed; }
        
        // Setters
        /**
         * Sets the (required) identifier for the field to the specified {@code FieldId}.
         *
         * @param id the {@code FieldId} to set as the identifier, must not be null
         * @return the updated {@code Builder} instance for method chaining
         */
        public Builder withId(FieldId id) {
            this.id = id;
            return this;
        }
        
        /**
         * Sets the (required) identifier of the field using the specified namespace and local name.
         * Constructs a {@code FieldId} based on the provided parameters and assigns it to the builder.
         *
         * @param namespace the {@code Namespace} associated with the identifier, must not be null
         * @param localName the local name within the namespace for the identifier, must not be null or blank
         * @return the updated {@code Builder} instance for method chaining
         * @throws NullPointerException if {@code namespace} or {@code localName} is null
         * @throws IllegalArgumentException if {@code localName} is blank
         */
        public Builder withId(Namespace namespace, String localName) {
            this.id = FieldId.of(namespace, localName);
            return this;
        }
        
        /**
         * Sets the (required) type of the field to the specified {@code FieldType}.
         *
         * @param type the {@code FieldType} to set for the field, must not be null
         * @return the updated {@code Builder} instance for method chaining
         */
        public Builder withType(FieldType type) {
            this.type = type;
            return this;
        }
        
        /**
         * Sets the (optional) display name for the field to the specified {@code displayName}.
         *
         * @param displayName the display name to set for the field
         * @return the updated {@code Builder} instance for method chaining
         */
        public Builder withDisplayName(String displayName) {
            this.displayName = displayName;
            return this;
        }
        
        /**
         * Sets the (optional) watermark for the field to the specified {@code watermark}.
         *
         * @param watermark the watermark to set for the field
         * @return the updated {@code Builder} instance for method chaining
         */
        public Builder withWatermark(String watermark) {
            this.watermark = watermark;
            return this;
        }
        
        /**
         * Sets the children for the current builder. (Any existing children will be replaced.)
         * This allows associating a set of child builders to form a hierarchical structure of fields.
         * Setting to null will remove any existing child relationships.
         *
         * @param children a set of {@code Builder} instances representing the children to associate with the current builder
         * @return the updated {@code Builder} instance for method chaining
         */
        public Builder withChildren(Set<Builder> children) {
            this.children = children;
            return this;
        }
        
        /**
         * Sets the children for the current builder using a variable number of {@code Builder} instances.
         * (Any existing children will be replaced.)
         * This allows associating a set of child builders to form a hierarchical structure of fields.
         * Setting to null will remove any existing child relationships.
         *
         * @param children an array of {@code Builder} instances representing the children to associate with the current builder
         * @return the updated {@code Builder} instance for method chaining
         */
        public Builder withChildren(Builder... children) {
            this.children = Set.of(children);
            return this;
        }
        
        /**
         * Adds a child {@code Builder} instance to the current builder's children set.
         * If the children set does not yet exist, it will be initialized.
         *
         * @param child the {@code Builder} instance to add as a child, must not be null
         * @return the updated {@code Builder} instance for method chaining
         */
        public Builder addChild(Builder child) {
            if (this.children == null) {
                this.children = new HashSet<>();
            }
            this.children.add(child);
            return this;
        }
        
        /**
         * Constructs and returns a {@code Field} instance based on the current configuration
         * of the builder. This method finalizes the building process and ensures that all
         * required properties have been set. It may recursively build child fields if they
         * have been defined.
         *
         * @return a {@code Field} instance created from the current state of the builder.
         * @throws NullPointerException if required properties such as {@code id} or {@code type} are not set.
         */
        public Field build() {
            return doBuild(0);
        }
        
        /**
         * Constructs a {@link Field} instance based on the builder configuration and specified generation.
         * Recursively builds the children fields if any are defined in the builder.
         *
         * @param generation the generation of the field being built, where 0 typically represents the root level.
         * @return a {@link Field} instance constructed using the current builder state, including its children.
         * @throws NullPointerException if the required properties {@code id} or {@code type} are not set.
         * @throws IllegalArgumentException if the builder configuration is invalid.
         * @throws IllegalStateException if the builder has already been used to build a {@link Field} before.
         */
        Field doBuild(int generation) {
            Objects.requireNonNull(id, "id is required");
            if (consumed) {
                throw new IllegalStateException("Builder for field "+ id +" has already been used.");
            }
            
            Objects.requireNonNull(type, "type is required");
            if (type == FieldType.COMPOUND && (children == null || children.isEmpty())) {
                throw new IllegalArgumentException("Compound field "+ id +" must have children defined.");
            }
            if (type != FieldType.COMPOUND && children != null && !children.isEmpty()) {
                throw new IllegalArgumentException("Non-compound field "+ id +" cannot have children defined.");
            }
            
            Set<Field> builtChildren = null;
            
            if (children != null && !children.isEmpty()) {
                builtChildren = children.stream()
                    .map(child -> child.doBuild(generation + 1))
                    .collect(Collectors.toUnmodifiableSet());
            }
            
            // Mark as consumed and return a freshly built Field instance
            this.consumed = true;
            return new Field(id, type, generation, displayName, watermark, builtChildren);
        }
    }
}