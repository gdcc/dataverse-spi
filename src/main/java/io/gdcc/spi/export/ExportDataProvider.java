
package io.gdcc.spi.export;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

import java.io.InputStream;
import java.util.Optional;

/**
 * Provides dataset metadata that can be used by an {@link Exporter} to create
 * new metadata export formats.
 * <p>
 * This interface offers multiple methods for retrieving dataset metadata in various
 * formats and levels of detail. Exporters should choose the method that best fits
 * their needs, considering the completeness of metadata and performance implications.
 *
 * <h3>Implementation Guide</h3>
 * Implementers must override the context-accepting versions of all data retrieval
 * methods. No-argument convenience methods are provided as default implementations
 * for backward compatibility but are deprecated and will be removed in a future version.
 *
 * <h3>Context Handling</h3>
 * Implementations should respect context options where applicable.
 * Not all methods support all context options - see individual method documentation for details.
 * All methods require a non-null {@link ExportDataContext}.
 * Passing null will result in a {@link NullPointerException}.
 * Callers should use {@link ExportDataContext#defaults()} instead of passing null.
 *
 * @see Exporter
 * @see ExportDataContext
 */
public interface ExportDataProvider {
    
    /**
     * Returns complete dataset metadata in Dataverse's standard JSON format.
     * <p>
     * This format includes comprehensive dataset-level metadata along with basic
     * metadata for each file in the dataset. It is the same JSON format used in
     * the Dataverse API and available as a metadata export option in the UI.
     *
     * @param context configuration for data retrieval
     * @return dataset metadata in Dataverse JSON format
     * @throws ExportException if metadata retrieval fails
     * @throws NullPointerException if context is null
     * @since 2.1.0
     * @apiNote While no formal JSON schema exists for this format, it is well-documented
     *          in the Dataverse guides. Along with OAI_ORE, this is one of only two export
     *          formats that provide complete dataset and file metadata.
     * @implNote Implementations must respect the {@code datasetMetadataOnly} flag.
     *           When true, file-level metadata should be excluded to optimize performance
     *           for datasets with large numbers of files. Other context options
     *           (publicFilesOnly, offset, length) do not apply and should be ignored.
     */
    JsonObject getDatasetJson(ExportDataContext context);
    
    /**
     * Returns complete dataset metadata using default options.
     *
     * @return dataset metadata in Dataverse JSON format
     * @throws ExportException if metadata retrieval fails
     * @since 1.0.0
     * @deprecated since 2.1.0, for removal in 3.0.0. Use {@link #getDatasetJson(ExportDataContext)} instead.
     */
    @Deprecated(since = "2.1.0", forRemoval = true)
    default JsonObject getDatasetJson() {
        return getDatasetJson(ExportDataContext.defaults());
    }
    
    /**
     * Returns dataset metadata in JSON-LD-based OAI-ORE format.
     * <p>
     * OAI-ORE (Open Archives Initiative Object Reuse and Exchange) provides a structured way to describe
     * aggregations of web resources. This format is used in Dataverse's archival bag export mechanism
     * and available via UI and API.
     *
     * @param context configuration for data retrieval
     * @return dataset metadata in OAI_ORE format
     * @throws ExportException if metadata retrieval fails
     * @throws NullPointerException if context is null
     * @since 2.1.0
     * @apiNote Along with the standard JSON format, this is one of only two export
     *          formats that provide complete dataset-level metadata along with basic
     *          file metadata for each file in the dataset.
     * @implNote Implementations must respect the {@code datasetMetadataOnly} flag.
     *           Other context options do not apply and should be ignored.
     */
    JsonObject getDatasetORE(ExportDataContext context);
    
    /**
     * Returns dataset metadata in OAI-ORE format using default options.
     *
     * @return dataset metadata in OAI-ORE format
     * @throws ExportException if metadata retrieval fails
     * @since 1.0.0
     * @deprecated since 2.1.0, for removal in 3.0.0. Use {@link #getDatasetORE(ExportDataContext)} instead.
     */
    @Deprecated(since = "2.1.0", forRemoval = true)
    default JsonObject getDatasetORE() {
        return getDatasetORE(ExportDataContext.defaults());
    }
    
    /**
     * Returns detailed metadata for all files in the dataset.
     * <p>
     * For tabular files that have been successfully ingested, this includes
     * DDI-centric metadata extracted during the ingest process. This detailed
     * metadata is not available through other methods in this interface.
     *
     * @param context configuration for data retrieval
     * @return JSON array with one entry per dataset file (both tabular and non-tabular)
     * @throws ExportException if metadata retrieval fails
     * @throws NullPointerException if context is null
     * @since 2.1.0
     * @apiNote No formal JSON schema is available for this output. The format is not
     *          extensively documented; implementers may wish to examine the DDIExporter
     *          and JSONPrinter classes in the Dataverse codebase for usage examples.
     * @implNote Implementations should respect both {@code datasetMetadataOnly} and
     *           {@code publicFilesOnly} flags. Pagination options do not apply and
     *           should be ignored.
     */
    JsonArray getDatasetFileDetails(ExportDataContext context);
    
    /**
     * Returns detailed metadata for all files using default options.
     *
     * @return JSON array with one entry per dataset file
     * @throws ExportException if metadata retrieval fails
     * @since 1.0.0
     * @deprecated since 2.1.0, for removal in 3.0.0. Use {@link #getDatasetFileDetails(ExportDataContext)} instead.
     */
    @Deprecated(since = "2.1.0", forRemoval = true)
    default JsonArray getDatasetFileDetails() {
        return getDatasetFileDetails(ExportDataContext.defaults());
    }
    
    /**
     * Returns detailed metadata for tabular files only, with support for filtering and pagination.
     * <p>
     * This method is specifically designed for datasets with large numbers of tabular
     * files and data variables. It provides access to the complete hierarchy of
     * datafile → filemetadata → datatable → datavariable metadata.
     *
     * @param context configuration for data retrieval
     * @return JSON array containing metadata for tabular files only
     * @throws ExportException if metadata retrieval fails
     * @throws NullPointerException if context is null
     * @since 2.1.0
     * @apiNote Pagination is intended for retrieving specific subsets, not for iterating
     *          through large result sets. For complete exports, call once without pagination
     *          or iterate by checking for empty results.
     * @implNote Implementations should respect {@code publicFilesOnly} to filter restricted
     *           or embargoed files. Pagination via {@code offset} and {@code length} should
     *           be supported where feasible. The {@code datasetMetadataOnly} flag does not
     *           apply and should be ignored.
     */
    JsonArray getTabularDataDetails(ExportDataContext context);
    
    /**
     * Returns dataset metadata conforming to the schema.org standard.
     * <p>
     * This metadata subset is used in dataset page headers to improve discoverability by search engines.
     * It provides structured data markup (JSON-LD) following the schema.org vocabulary.
     *
     * @param context configuration for data retrieval
     * @return dataset metadata in schema.org format
     * @throws ExportException if metadata retrieval fails
     * @throws NullPointerException if context is null
     * @since 2.1.0
     * @apiNote This metadata export is not complete. It should only be used as a starting
     *          point for an Exporter if it simplifies implementation compared to using
     *          the complete JSON or OAI_ORE exports.
     * @implNote All context options are ignored by this method.
     */
    JsonObject getDatasetSchemaDotOrg(ExportDataContext context);
    
    /**
     * Returns dataset metadata in schema.org format using default options.
     *
     * @return dataset metadata in schema.org format
     * @throws ExportException if metadata retrieval fails
     * @since 1.0.0
     * @deprecated since 2.1.0, for removal in 3.0.0. Use {@link #getDatasetSchemaDotOrg(ExportDataContext)} instead.
     */
    @Deprecated(since = "2.1.0", forRemoval = true)
    default JsonObject getDatasetSchemaDotOrg() {
        return getDatasetSchemaDotOrg(ExportDataContext.defaults());
    }
    
    /**
     * Returns dataset metadata conforming to the DataCite standard as XML.
     * <p>
     * This is the same metadata format sent to DataCite when DataCite DOIs are used.
     * It provides citation metadata following the DataCite Metadata Schema.
     *
     * @param context configuration for data retrieval
     * @return dataset metadata as DataCite XML string
     * @throws ExportException if metadata retrieval fails
     * @throws NullPointerException if context is null
     * @since 2.1.0
     * @apiNote This metadata export is not complete. It should only be used as a starting
     *          point for an Exporter if it simplifies implementation compared to using
     *          the complete JSON or OAI_ORE exports.
     * @implNote All context options are ignored by this method.
     */
    String getDataCiteXml(ExportDataContext context);
    
    /**
     * Returns dataset metadata in DataCite XML format using default options.
     *
     * @return dataset metadata as DataCite XML string
     * @throws ExportException if metadata retrieval fails
     * @since 1.0.0
     * @deprecated since 2.1.0, for removal in 3.0.0. Use {@link #getDataCiteXml(ExportDataContext)} instead.
     */
    @Deprecated(since = "2.1.0", forRemoval = true)
    default String getDataCiteXml() {
        return getDataCiteXml(ExportDataContext.defaults());
    }
    
    /**
     * Returns metadata in the format specified by an Exporter's prerequisite.
     * <p>
     * Some Exporters transform metadata from one standard format to another (e.g.,
     * DDI XML to DDI HTML). Such Exporters declare a prerequisite format via
     * {@link Exporter#getPrerequisiteFormatName()}, and this method provides access
     * to that prerequisite metadata.
     *
     * @param context configuration passed to the prerequisite exporter
     * @return metadata in the prerequisite format, or empty if no prerequisite is configured
     * @throws ExportException if metadata retrieval fails
     * @throws NullPointerException if context is null
     * @since 2.1.0
     * @apiNote This is useful for creating alternate representations of the same metadata
     *          (e.g., XML, HTML, PDF versions of a standard like DDI), especially when
     *          conversion libraries exist. Note that if a third-party Exporter replaces
     *          the internal exporter you depend on, this method may return unexpected results.
     * @implNote The default implementation returns empty. Override only if your provider
     *           supports prerequisite format chaining. The prerequisite exporter receives
     *           the same context as specified in this call.
     */
    default Optional<InputStream> getPrerequisiteInputStream(ExportDataContext context) {
        return Optional.empty();
    }
    
    /**
     * Returns metadata in the prerequisite format using default options.
     *
     * @return metadata in the prerequisite format, or empty if no prerequisite is configured
     * @throws ExportException if metadata retrieval fails
     * @since 1.0.0
     * @deprecated since 2.1.0, for removal in 3.0.0. Use {@link #getPrerequisiteInputStream(ExportDataContext)} instead.
     */
    @Deprecated(since = "2.1.0", forRemoval = true)
    default Optional<InputStream> getPrerequisiteInputStream() {
        return getPrerequisiteInputStream(ExportDataContext.defaults());
    }
}
