package io.gdcc.spi.export;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Defines <em>what</em> dataset metadata to retrieve and at what level of detail
 * for dataset-oriented export operations.
 * <p>
 * This is a pure data-shape specification: it answers which aspects of a dataset
 * should be included in an export, and optionally how file metadata nested within
 * that dataset should be shaped. It deliberately does not address <em>which</em>
 * datasets to operate on (that is a selection concern at a higher level), nor
 * <em>how much</em> data to retrieve per call — pagination is a separate,
 * orthogonal concern expressed via a {@code PageRequest} at the method level.
 * <p>
 * File metadata shaping is optional: if no {@link FileExportQuery} is provided,
 * methods that include file metadata will apply their own defaults. Methods that
 * do not return file metadata will ignore any nested {@link FileExportQuery}.
 * <p>
 * Instances are immutable and must be constructed via {@link #builder()}.
 * Use {@link #defaults()} for the standard query with no special filtering.
 *
 * @see FileExportQuery
 * @see DatasetMetadataPredicates
 */
public final class DatasetExportQuery {
    
    private final Set<DatasetMetadataPredicates> datasetPredicates;
    private final FileExportQuery fileQuery;
    
    /**
     * Default query, including all dataset metadata and applying file metadata defaults.
     */
    private static final DatasetExportQuery DEFAULT = builder().build();
    
    private DatasetExportQuery(Builder builder) {
        this.datasetPredicates = Set.copyOf(builder.datasetPredicates);
        this.fileQuery = builder.fileQuery;
    }
    
    /**
     * Returns a builder for creating new queries.
     *
     * @return a new {@link Builder} instance
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Returns the default query, which includes all dataset metadata with no special
     * filtering, and defers file metadata shaping to method-level defaults.
     *
     * @return the shared default {@link DatasetExportQuery} instance
     */
    public static DatasetExportQuery defaults() {
        return DEFAULT;
    }
    
    /**
     * Builder for {@link DatasetExportQuery}.
     * <p>
     * Obtain an instance via {@link DatasetExportQuery#builder()} or
     * {@link Builder#from(DatasetExportQuery)} to derive a new query from an existing one.
     */
    public static class Builder {
        private final Set<DatasetMetadataPredicates> datasetPredicates = new HashSet<>();
        private FileExportQuery fileQuery = null;
        
        private Builder() {
            // Hiding constructor to enforce use of the static factory method
        }
        
        /**
         * Sets the dataset metadata predicates, replacing any previously set predicates.
         *
         * @param predicates the dataset metadata predicates to set
         * @return this builder instance
         */
        public Builder datasetPredicates(DatasetMetadataPredicates... predicates) {
            this.datasetPredicates.clear();
            this.datasetPredicates.addAll(Set.of(predicates));
            return this;
        }
        
        /**
         * Sets the dataset metadata predicates, replacing any previously set predicates.
         *
         * @param predicates the dataset metadata predicates to set
         * @return this builder instance
         */
        public Builder datasetPredicates(Collection<DatasetMetadataPredicates> predicates) {
            this.datasetPredicates.clear();
            this.datasetPredicates.addAll(predicates);
            return this;
        }
        
        /**
         * Adds a dataset metadata predicate to the builder's collection of predicates.
         *
         * @param predicate the dataset metadata predicate to add
         * @return this builder instance
         */
        public Builder addDatasetPredicate(DatasetMetadataPredicates predicate) {
            this.datasetPredicates.add(predicate);
            return this;
        }
        
        /**
         * Sets the {@link FileExportQuery} to use for shaping file metadata nested
         * within this dataset query. Replaces any previously set file query.
         * <p>
         * If not set, methods that include file metadata will apply their own defaults.
         *
         * @param fileQuery the file export query to compose into this dataset query
         * @return this builder instance
         */
        public Builder fileQuery(FileExportQuery fileQuery) {
            this.fileQuery = fileQuery;
            return this;
        }
        
        /**
         * Builds an immutable {@link DatasetExportQuery}.
         *
         * @return a new, validated {@link DatasetExportQuery}
         * @throws IllegalArgumentException if the predicate combination is invalid,
         *         e.g. due to conflicting predicates
         */
        public DatasetExportQuery build() {
            return new DatasetExportQuery(this);
        }
        
        /**
         * Creates a new {@link Builder} pre-populated with the state of the given query,
         * useful for deriving a modified copy without altering the original.
         *
         * @param source the {@link DatasetExportQuery} instance to copy from
         * @return a new {@code Builder} with the same predicates and file query as {@code source}
         */
        public Builder from(DatasetExportQuery source) {
            return new Builder()
                .datasetPredicates(source.datasetPredicates)
                .fileQuery(source.fileQuery);
        }
    }
    
    // Getters
    
    /**
     * Returns the dataset metadata predicates that control which aspects of the dataset
     * are included in the export.
     *
     * @return an unmodifiable set of {@link DatasetMetadataPredicates}; never {@code null}
     */
    public Set<DatasetMetadataPredicates> getDatasetPredicates() {
        return datasetPredicates;
    }
    
    /**
     * Returns the optional {@link FileExportQuery} that controls how file metadata
     * nested within this dataset export should be shaped.
     * <p>
     * An empty {@link Optional} means no explicit file query was specified; methods
     * that include file metadata will apply their own defaults in that case.
     *
     * @return an {@link Optional} containing the file export query, or empty if not set
     */
    public Optional<FileExportQuery> getFileQuery() {
        return Optional.ofNullable(fileQuery);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DatasetExportQuery that = (DatasetExportQuery) o;
        return datasetPredicates.equals(that.datasetPredicates)
            && Objects.equals(fileQuery, that.fileQuery);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(datasetPredicates, fileQuery);
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("DatasetExportQuery{");
        
        if (!datasetPredicates.isEmpty()) {
            sb.append("datasetPredicates=").append(datasetPredicates).append(", ");
        }
        if (fileQuery != null) {
            sb.append("fileQuery=").append(fileQuery);
        } else {
            sb.append("fileQuery=<default>");
        }
        sb.append("}");
        return sb.toString();
    }
}
