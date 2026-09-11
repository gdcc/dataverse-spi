
package io.gdcc.spi.export;

import io.gdcc.spi.meta.plugin.CoreProvider;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.stream.JsonCollectors;
import org.w3c.dom.Document;

import java.io.InputStream;
import java.util.Optional;
import java.util.stream.Stream;

import static io.gdcc.spi.export.FileMetadataPredicates.INCLUDE_TABULAR_DATA_VARIABLES;

/**
 * Provides dataset metadata that can be used by an {@link Exporter} to create
 * new metadata export formats.
 * <p>
 * This interface offers multiple methods for retrieving dataset metadata in various
 * formats and levels of detail. Exporters should choose the method that best fits
 * their needs, considering the completeness of metadata and performance implications.
 *
 * <h2>Implementation Guide</h2>
 * Implementers must override the context-accepting versions of all data retrieval
 * methods. No-argument convenience methods are provided as default implementations
 * for backward compatibility but are deprecated and will be removed in a future version.
 *
 * <h2>Context Handling</h2>
 * Implementations should respect context options where applicable.
 * Not all methods support all context options - see individual method documentation for details.
 * <p>
 * All methods require a non-null {@link DatasetExportQuery} or {@link FileExportQuery}.
 * Passing null will result in a {@link NullPointerException}.
 * <p>
 * Callers should use {@link DatasetExportQuery#defaults()} respectivelly {@link FileExportQuery#all()}
 * or {@link FileExportQuery#all()} or build their own instead of passing null.
 *
 * @see Exporter
 * @see DatasetExportQuery
 * @see FileExportQuery
 */
public interface ExportDataProvider extends CoreProvider {
    
    int API_LEVEL = 2;
    
    /**
     * Returns complete dataset metadata in Dataverse's standard JSON format.
     * <p>
     * This format prioritizes comprehensive dataset-level metadata.
     * It is the same JSON format used in the Dataverse API and available as a metadata export option in the UI.
     * <p>
     * Optionally, it may include metadata for each file in the dataset, depending on the {@link FileExportQuery}
     * contained in the given {@link DatasetExportQuery} {@code query}.
     *
     * @param query specification for data retrieval
     * @return dataset metadata in Dataverse JSON format
     * @throws ExportException if metadata retrieval fails
     * @throws NullPointerException if the query is null
     * @since 2.1.0
     * @apiNote While no formal JSON schema exists for this format, it is well-documented
     *          in the Dataverse guides. Along with OAI_ORE, this is one of only two export
     *          formats that provide complete dataset and file metadata.
     * @implNote Implementations must respect the embedded file export query describing what file metadata to embed.
     */
    JsonObject getDatasetJson(DatasetExportQuery query);
    
    /**
     * Returns complete dataset metadata, including basic file metadata (but no details like tabular data metadata).
     * Note: for datasets with large numbers of files, this may be an issue with memory consumption!
     *
     * @return dataset metadata in Dataverse JSON format
     * @throws ExportException if metadata retrieval fails
     * @since 1.0.0
     * @deprecated since 2.1.0, for removal in 3.0.0. Use {@link #getDatasetJson(DatasetExportQuery)} instead.
     * @apiNote For backward compatibility, this method includes basic file metadata,
     *          while the newer methods will not include file metadata by default!
     */
    @Deprecated(since = "2.1.0", forRemoval = true)
    default JsonObject getDatasetJson() {
        return getDatasetJson(DatasetExportQuery.builder()
            .fileQuery(FileExportQuery.all())
            .build());
    }
    
    /**
     * Returns dataset metadata in JSON-LD-based OAI-ORE format.
     * <p>
     * OAI-ORE (Open Archives Initiative Object Reuse and Exchange) provides a structured way to describe
     * aggregations of web resources. This format is used in Dataverse's archival bag export mechanism
     * and available via UI and API.
     * <p>
     * Optionally, it may include metadata for each file in the dataset, depending on the {@link FileExportQuery}
     * contained in the given {@link DatasetExportQuery} {@code query}.
     *
     * @param query specification for data retrieval
     * @return dataset metadata in OAI-ORE format
     * @throws ExportException if metadata retrieval fails
     * @throws NullPointerException if the query is null
     * @since 2.1.0
     * @apiNote Along with the standard JSON format, this is one of only two export
     *          formats that provide complete dataset-level metadata along with basic
     *          file metadata for each file in the dataset.
     * @implNote Implementations must respect the embedded file export query describing what file metadata to embed.
     */
    JsonObject getDatasetORE(DatasetExportQuery query);
    
    /**
     * Returns dataset metadata in OAI-ORE format, including basic file metadata (but no details like tabular data metadata).
     * Note: for datasets with large numbers of files, this may be an issue with memory consumption!
     *
     * @return dataset metadata in OAI-ORE format
     * @throws ExportException if metadata retrieval fails
     * @since 1.0.0
     * @deprecated since 2.1.0, for removal in 3.0.0. Use {@link #getDatasetORE(DatasetExportQuery)} instead.
     * @apiNote For backward compatibility, this method includes basic file metadata,
     *          while the newer methods will not include file metadata by default!
     */
    @Deprecated(since = "2.1.0", forRemoval = true)
    default JsonObject getDatasetORE() {
        return getDatasetORE(DatasetExportQuery.builder()
            .fileQuery(FileExportQuery.all())
            .build());
    }
    
    /**
     * Returns detailed metadata for files in the dataset.
     * <p>
     * If {@link FileExportQuery} has {@link FileMetadataPredicates#INCLUDE_TABULAR_DATA_VARIABLES} set,
     * for tabular files that have been successfully ingested, this may include DDI-centric metadata
     * extracted during the ingest process. This detailed metadata is not available through other methods in this interface.
     * <p>
     * The query may specify filters to skip certain files or how many metadata details should be included.
     * The resulting stream will contain a limited number of elements only, specified by a {@code PageRequest},
     * avoiding huge memory allocations in the provider.
     *
     * @param query specification for file data retrieval
     * @param request the page request containing pagination information such as page offset and page size
     * @return JSON array with one entry per dataset file (both tabular and non-tabular)
     * @throws ExportException if metadata retrieval fails
     * @throws NullPointerException if the query or request is null
     * @since 2.1.0
     * @apiNote No formal JSON schema is available for this output. The format is not
     *          extensively documented; implementers may wish to examine the DDIExporter
     *          and JSONPrinter classes in the Dataverse codebase for usage examples.
     */
    Stream<JsonObject> getDatasetFileDetails(FileExportQuery query, PageRequest request);
    
    /**
     * Returns detailed metadata for files in the dataset.
     * <p>
     * If {@link FileExportQuery} has {@link FileMetadataPredicates#INCLUDE_TABULAR_DATA_VARIABLES} set,
     * for tabular files that have been successfully ingested, this may include DDI-centric metadata
     * extracted during the ingest process. This detailed metadata is not available through other methods in this interface.
     * <p>
     * The query may specify filters to skip certain files or how many metadata details should be included.
     * The resulting stream will contain all matching files for consumption.
     * <p>
     * In cases with large metadata quantities use {@link #getDatasetFileDetails(FileExportQuery,PageRequest)}
     * for a stream containing a limited number of elements only, avoiding huge memory allocations in the provider.
     *
     * @param query specification for file data retrieval
     * @return JSON array with one entry per dataset file (both tabular and non-tabular)
     * @throws ExportException if metadata retrieval fails
     * @throws NullPointerException if the query is null
     * @since 2.1.0
     * @apiNote No formal JSON schema is available for this output. The format is not
     *          extensively documented; implementers may wish to examine the DDIExporter
     *          and JSONPrinter classes in the Dataverse codebase for usage examples.
     */
    Stream<JsonObject> getDatasetFileDetails(FileExportQuery query);
    
    /**
     * Returns all available metadata for all files, including tabular data metadata, if available.
     * <p>
     * Note that this method will serialize all file metadata into one large JSON array.
     * This can be memory-intensive for large datasets and should be used judiciously.
     * There have been reports of unexportable large datasets in production installations.
     * Using {@link #getDatasetFileDetails(FileExportQuery)} (or its paged variant) instead is advised.
     * </p>
     *
     * @return JSON array with one JSON object entry per dataset file
     * @throws ExportException if metadata retrieval fails
     * @since 1.0.0
     * @deprecated since 2.1.0, for removal in 3.0.0. Use {@link #getDatasetFileDetails(FileExportQuery)}
     *             or {@link #getDatasetFileDetails(FileExportQuery, PageRequest)}instead.
     */
    @Deprecated(since = "2.1.0", forRemoval = true)
    default JsonArray getDatasetFileDetails() {
        return this.getDatasetFileDetails(FileExportQuery.builder(FileExportQuery.all())
                                                         .addFilePredicate(INCLUDE_TABULAR_DATA_VARIABLES)
                                                         .build())
            .collect(JsonCollectors.toJsonArray());
    }
    
    /**
     * Returns dataset metadata conforming to the schema.org standard.
     * <p>
     * This metadata subset is used in dataset page headers to improve discoverability by search engines.
     * It provides structured data markup (JSON-LD) following the schema.org vocabulary.
     *
     * @param query specification for data retrieval
     * @return dataset metadata in schema.org format
     * @throws ExportException if metadata retrieval fails
     * @throws NullPointerException if the query is null
     * @since 2.1.0
     * @apiNote This metadata export is not complete. It should only be used as a starting
     *          point for an Exporter if it simplifies implementation compared to using
     *          the complete JSON or OAI_ORE exports.
     * @implNote All context options are ignored by this method.
     */
    JsonObject getDatasetSchemaDotOrg(DatasetExportQuery query);
    
    /**
     * Returns dataset metadata in schema.org format using default options.
     *
     * @return dataset metadata in schema.org format
     * @throws ExportException if metadata retrieval fails
     * @since 1.0.0
     * @deprecated since 2.1.0, for removal in 3.0.0. Use {@link #getDatasetSchemaDotOrg(DatasetExportQuery)} instead.
     */
    @Deprecated(since = "2.1.0", forRemoval = true)
    default JsonObject getDatasetSchemaDotOrg() {
        return getDatasetSchemaDotOrg(DatasetExportQuery.defaults());
    }
    
    /**
     * Returns dataset metadata conforming to the DataCite standard as XML.
     * <p>
     * This is the same metadata format sent to DataCite when DataCite DOIs are used.
     * It provides citation metadata following the DataCite Metadata Schema.
     * </p><p>
     * Note: the returned XML document can easily be queried using XPath and other techniques
     * </p>
     *
     * @param query specification for data retrieval
     * @return dataset metadata as DataCite XML string
     * @throws ExportException if metadata retrieval fails
     * @throws NullPointerException if the query is null
     * @since 2.1.0
     * @apiNote This metadata export is not complete. It should only be used as a starting
     *          point for an Exporter if it simplifies implementation compared to using
     *          the complete JSON or OAI_ORE exports.
     * @implNote All context options are ignored by this method.
     */
    Document getDataCiteXml(DatasetExportQuery query);
    
    /**
     * Returns dataset metadata in DataCite XML format using default options.
     *
     * @return dataset metadata as DataCite XML string
     * @throws ExportException if metadata retrieval fails
     * @since 1.0.0
     * @deprecated since 2.1.0, for removal in 3.0.0. Use {@link #getDataCiteXml(DatasetExportQuery)} instead.
     */
    @Deprecated(since = "2.1.0", forRemoval = true)
    String getDataCiteXml();
    
    /**
     * Returns metadata in the format specified by an Exporter's prerequisite.
     * <p>
     * Some Exporters transform metadata from one standard format to another (e.g.,
     * DDI XML to DDI HTML). Such Exporters declare a prerequisite format via
     * {@link Exporter#getPrerequisiteFormatName()}, and this method provides access
     * to that prerequisite metadata.
     *
     * @param query specifcation passed to the prerequisite exporter
     * @return metadata in the prerequisite format, or empty if no prerequisite is configured
     * @throws ExportException if metadata retrieval fails
     * @throws NullPointerException if the query is null
     * @since 2.1.0
     * @apiNote This is useful for creating alternate representations of the same metadata
     *          (e.g., XML, HTML, PDF versions of a standard like DDI), especially when
     *          conversion libraries exist. Note that if a third-party Exporter replaces
     *          the internal exporter you depend on, this method may return unexpected results.
     * @implNote The default implementation returns empty. Override only if your provider
     *           supports prerequisite format chaining. The prerequisite exporter receives
     *           the same context as specified in this call.
     */
    default Optional<InputStream> getPrerequisiteInputStream(DatasetExportQuery query) {
        return Optional.empty();
    }
    
    /**
     * Returns metadata in the prerequisite format using default options.
     *
     * @return metadata in the prerequisite format, or empty if no prerequisite is configured
     * @throws ExportException if metadata retrieval fails
     * @since 1.0.0
     * @deprecated since 2.1.0, for removal in 3.0.0. Use {@link #getPrerequisiteInputStream(DatasetExportQuery)} instead.
     */
    @Deprecated(since = "2.1.0", forRemoval = true)
    default Optional<InputStream> getPrerequisiteInputStream() {
        return getPrerequisiteInputStream(DatasetExportQuery.defaults());
    }
}
