package io.gdcc.spi.export;

import java.util.Objects;

/**
 * Provides an optional mechanism for defining various data retrieval options 
 * for the export subsystem in a way that should allow us adding support for 
 * more options going forward with minimal or no changes to the already 
 * implemented export plugins. 
 */
public final class ExportDataContext {
    
    private final boolean datasetMetadataOnly;
    private final boolean publicFilesOnly;
    private final int offset;
    private final int length;
    
    /**
     * Default context with no special options.
     */
    private static final ExportDataContext DEFAULT = builder().build();
    
    private ExportDataContext(Builder builder) {
        this.datasetMetadataOnly = builder.datasetMetadataOnly;
        this.publicFilesOnly = builder.publicFilesOnly;
        this.offset = builder.offset;
        this.length = builder.length;
    }
    
    /**
     * Returns a builder for creating new contexts.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Returns a default context with no special options.
     */
    public static ExportDataContext defaults() {
        return DEFAULT;
    }
    
    /**
     * Builder for ExportDataContext.
     */
    public static class Builder {
        private int offset = 0; // default: no offset = beginning
        private int length = 0; // default: no length = no limit
        private boolean datasetMetadataOnly = false;
        private boolean publicFilesOnly = false;
        
        private Builder() {
            // Hiding constructor to enforce use of static factory method
        }
        
        /**
         * Excludes file-level metadata from the export.
         */
        public Builder datasetMetadataOnly() {
            this.datasetMetadataOnly = true;
            return this;
        }
        
        /**
         * Sets whether to exclude file-level metadata.
         */
        public Builder datasetMetadataOnly(boolean value) {
            this.datasetMetadataOnly = value;
            return this;
        }
        
        /**
         * Includes only public (non-restricted, non-embargoed) files.
         */
        public Builder publicFilesOnly() {
            this.publicFilesOnly = true;
            return this;
        }
        
        /**
         * Sets whether to include only public files.
         */
        public Builder publicFilesOnly(boolean value) {
            this.publicFilesOnly = value;
            return this;
        }
        
        /**
         * Sets the starting position for results (0-based).
         *
         * @param offset zero-based starting position (must be >= 0)
         * @return this builder
         */
        public Builder offset(int offset) {
            this.offset = offset;
            return this;
        }
        
        /**
         * Sets the maximum number of results to return.
         *
         * @param length maximum number of items (0 = unlimited, must be >= 0)
         * @return this builder
         */
        public Builder length(int length) {
            this.length = length;
            return this;
        }
        
        /**
         * Convenience method to set both offset and length together.
         *
         * @param offset zero-based starting position (must be >= 0)
         * @param length maximum number of items (0 = unlimited, must be >= 0)
         * @return this builder
         * @apiNote Pagination is primarily intended for retrieving specific subsets,
         *          not for iterating through large datasets. For full exports of
         *          large datasets, consider using streaming methods if available.
         */
        public Builder pagination(int offset, int length) {
            this.offset = offset;
            this.length = length;
            return this;
        }
        
        /**
         * Builds an immutable ExportDataContext.
         *
         * @return validated context
         * @throws IllegalArgumentException if validation fails
         */
        public ExportDataContext build() {
            // Validate business rules
            if (offset < 0) {
                throw new IllegalArgumentException(
                    "offset must be non-negative, got: " + offset
                );
            }
            
            if (length < 0) {
                throw new IllegalArgumentException(
                    "length must be non-negative (0 = unlimited), got: " + length
                );
            }
            
            return new ExportDataContext(this);
        }
        
        /**
         * Copies the properties from the given {@link ExportDataContext} instance into a new {@code Builder}.
         *
         * @param source the {@code ExportDataContext} instance from which to copy properties
         * @return a new {@code Builder} instance with properties copied from the provided context
         */
        public Builder from(ExportDataContext source) {
            return new Builder()
                .datasetMetadataOnly(source.datasetMetadataOnly)
                .publicFilesOnly(source.publicFilesOnly)
                .offset(source.offset)
                .length(source.length);
        }
    }
    
    // Getters
    
    public boolean isDatasetMetadataOnly() {
        return datasetMetadataOnly;
    }
    
    public boolean isPublicFilesOnly() {
        return publicFilesOnly;
    }
    
    /**
     * Returns the starting offset for results.
     *
     * @return zero-based offset (0 = start from beginning)
     */
    public int getOffset() {
        return offset;
    }
    
    /**
     * Returns the maximum number of results to return.
     *
     * @return maximum length (0 = unlimited)
     */
    public int getLength() {
        return length;
    }
    
    /**
     * @return true if a non-zero offset is configured
     */
    public boolean hasOffset() {
        return offset > 0;
    }
    
    /**
     * @return true if length is limited (non-zero)
     */
    public boolean hasLengthLimit() {
        return length > 0;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExportDataContext that = (ExportDataContext) o;
        return datasetMetadataOnly == that.datasetMetadataOnly &&
            publicFilesOnly == that.publicFilesOnly &&
            offset == that.offset &&
            length == that.length;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(datasetMetadataOnly, publicFilesOnly, offset, length);
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ExportDataContext{");
        boolean hasContent = false;
        
        if (datasetMetadataOnly) {
            sb.append("datasetMetadataOnly");
            hasContent = true;
        }
        
        if (publicFilesOnly) {
            if (hasContent) sb.append(", ");
            sb.append("publicFilesOnly");
            hasContent = true;
        }
        
        if (offset > 0 || length > 0) {
            if (hasContent) sb.append(", ");
            sb.append("offset=").append(offset);
            sb.append(", length=").append(length == 0 ? "unlimited" : length);
            hasContent = true;
        }
        
        if (!hasContent) {
            sb.append("defaults");
        }
        
        sb.append("}");
        return sb.toString();
    }
}
