package io.gdcc.spi.export;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static io.gdcc.spi.export.FileMetadataPredicates.*;

/**
 * Defines <em>what</em> file metadata to retrieve and at what level of detail
 * for file-oriented export operations.
 * <p>
 * This is a pure data-shape specification: it answers which files should be included
 * and how much detail about them should be fetched. It deliberately does not address
 * <em>how much</em> data to retrieve per call — pagination is a separate,
 * orthogonal concern expressed via a {@code PageRequest} at the method level.
 * <p>
 * A {@code FileExportQuery} may be used standalone in file-centric export methods,
 * or composed inside a {@code DatasetExportQuery} to specify how file metadata
 * should be shaped within a dataset export.
 * <p>
 * Instances are immutable and must be constructed via {@link #builder()}.
 * Use {@link #defaults()} for the standard all-files query with no special filtering.
 *
 * @see FileMetadataPredicates
 */
public final class FileExportQuery {
    
    private final Set<FileMetadataPredicates> filePredicates;
    
    /**
     * Default query with no special options.
     */
    private static final FileExportQuery DEFAULT = builder().addFilePredicate(ALL_FILES).build();
    
    private FileExportQuery(Builder builder) {
        this.filePredicates = builder.filePredicates;
    }
    
    /**
     * Returns a builder for creating new queries.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Returns a default query, which includes all files without filtering or detail restrictions.
     */
    public static FileExportQuery defaults() {
        return DEFAULT;
    }
    
    /**
     * Builder for {@link FileExportQuery}.
     * <p>
     * Obtain an instance via {@link FileExportQuery#builder()} or
     * {@link Builder#from(FileExportQuery)} to derive a new query from an existing one.
     */
    public static class Builder {
        private final Set<FileMetadataPredicates> filePredicates = new HashSet<>();
        
        private Builder() {
            // Hiding constructor to enforce use of the static factory method
        }
        
        /**
         * Sets the file metadata predicates, replacing any previously set predicates.
         *
         * @param predicates the file metadata predicates to set
         * @return this builder instance
         */
        public Builder filePredicates(FileMetadataPredicates... predicates) {
            this.filePredicates.clear();
            this.filePredicates.addAll(Set.of(predicates));
            return this;
        }
        
        /**
         * Sets the file metadata predicates, replacing any previously set predicates.
         *
         * @param predicates the file metadata predicates to set
         * @return this builder instance
         */
        public Builder filePredicates(Collection<FileMetadataPredicates> predicates) {
            this.filePredicates.clear();
            this.filePredicates.addAll(predicates);
            return this;
        }
        
        /**
         * Adds a file metadata predicate to the builder's collection of predicates.
         *
         * @param predicate the file metadata predicate to add
         * @return this builder instance
         */
        public Builder addFilePredicate(FileMetadataPredicates predicate) {
            this.filePredicates.add(predicate);
            return this;
        }
        
        /**
         * Builds an immutable {@link FileExportQuery}.
         *
         * @return validated context
         * @throws IllegalArgumentException if validation fails
         */
        public FileExportQuery build() {
            return new FileExportQuery(this);
        }
        
        /**
         * Copies the properties from the given {@link FileExportQuery} instance into a new {@code Builder}.
         *
         * @param source the {@code FileExportQuery} instance from which to copy properties
         * @return a new {@code Builder} instance with properties copied from the provided query
         */
        public Builder from(FileExportQuery source) {
            return new Builder()
                .filePredicates(source.filePredicates);
        }
    }
    
    // Getters
    
    /**
     * Returns the file metadata predicates that control which files are included
     * and what level of detail is fetched for each.
     *
     * @return an unmodifiable set of {@link FileMetadataPredicates}; never {@code null}
     */
    public Set<FileMetadataPredicates> getFilePredicates() {
        return Collections.unmodifiableSet(filePredicates);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FileExportQuery that = (FileExportQuery) o;
        return filePredicates.equals(that.filePredicates);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(filePredicates);
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("FileExportQuery{");
        
        if (!filePredicates.isEmpty()) {
            sb.append("filePredicates=").append(filePredicates);
        }
        
        sb.append("}");
        return sb.toString();
    }
}
