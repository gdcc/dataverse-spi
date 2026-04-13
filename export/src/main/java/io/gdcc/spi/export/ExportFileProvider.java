package io.gdcc.spi.export;

import io.gdcc.spi.meta.plugin.CoreProvider;
import jakarta.json.JsonObject;

public interface ExportFileProvider extends CoreProvider {
    
    int API_LEVEL = 1;
    
    /**
     * Retrieve the metadata of a single file for export purposes.
     *
     * @return a JSON object of a Dataverse DataFile with FileMetadata contained in it
     */
    JsonObject getDatasetFileDetails();
    
}
