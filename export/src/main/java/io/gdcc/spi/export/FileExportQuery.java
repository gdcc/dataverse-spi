package io.gdcc.spi.export;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
 * Instances are immutable and must be constructed via {@link #builder()} or cloned using {@link #builder(FileExportQuery)}.
 * Use {@link #all()} for the standard "all files" query with no special filtering.
 * Use {@link #none()} for the standard "no files" query.
 *
 * @see FileMetadataPredicates
 */
public final class FileExportQuery {
    
    private final Set<FileMetadataPredicates> filePredicates;
    
    /**
     * Query: "include all files without filtering any nor including special details"
     */
    private static final FileExportQuery ALL = builder().addFilePredicate(ALL_FILES).build();
    /**
     * Query: "skip all files"
     */
    private static final FileExportQuery NONE = builder().addFilePredicate(SKIP_FILES).build();
    
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
     * Creates a new {@code Builder} instance initialized with the properties of the given {@code FileExportQuery}.
     *
     * @param source the {@code FileExportQuery} instance from which to copy properties
     * @return a new {@code Builder} instance with properties copied from the provided query
     */
    public static Builder builder(FileExportQuery source) {
        return new Builder().from(source);
    }
    
    /**
     * Get a simple query: "include all files without filtering any nor including special details"
     * @return {@link ALL}
     */
    public static FileExportQuery all() {
        return ALL;
    }
    
    /**
     * Get a simple query: "skip all files"
     * @return {@link NONE}
     */
    public static FileExportQuery none() {
        return NONE;
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
         * <p>As per design, the query may not be lacking a description of which files to include and optionally what
         * metadata about the selected files. If no, no selective or conflicting predicates have been set,
         * an exception is thrown.</p>
         *
         * @return validated context
         * @throws IllegalArgumentException if validation fails
         */
        public FileExportQuery build() {
            if (this.filePredicates.isEmpty()) {
                throw new IllegalArgumentException("At least one file metadata predicate must be given for a valid query.");
            }
            
            if (this.filePredicates.stream()
                .filter(p -> !FileMetadataPredicates.relatesToFileMetadata(p))
                .findFirst()
                .isEmpty()) {
                throw new IllegalArgumentException("At least one file metadata predicate must be about selection of files");
            }
            
            Set<FileMetadataPredicates> conflicts = FileMetadataPredicates.checkConflicts(this.filePredicates);
            if (!conflicts.isEmpty()) {
                throw new IllegalArgumentException(
                    "Conflicting predicates detected: " +
                    conflicts.stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining(", "))
                );
            }
            
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
    public Set<FileMetadataPredicates> predicates() {
        return Collections.unmodifiableSet(filePredicates);
    }
    
    /**
     * Determine if this query was built requiring a certain {@link FileMetadataPredicates}.
     * @param predicate to check for
     * @return true if required, false otherwise
     */
    public boolean requires(FileMetadataPredicates predicate) {
        return filePredicates.contains(predicate);
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
