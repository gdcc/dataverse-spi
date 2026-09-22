package io.gdcc.spi.export.fixtures;

import io.gdcc.spi.export.ExportDataProvider;
import io.gdcc.spi.export.ExportException;
import io.gdcc.spi.export.Exporter;
import io.gdcc.spi.export.caps.bulk.BulkDatasetContext;
import io.gdcc.spi.export.caps.bulk.BulkDatasetExporter;
import io.gdcc.spi.meta.annotations.DataversePlugin;

import java.io.OutputStream;
import java.util.Locale;

/**
 * Minimal exporter implementation for processor integration testing around capabilities with additional providers
 */
@DataversePlugin
public class StubBulkExporter implements Exporter, BulkDatasetExporter {
    @Override
    public void exportDataset(ExportDataProvider dataProvider, OutputStream outputStream) {
        /* Intentionally left blank for test class */
    }

    @Override
    public String getFormatName() {
        return "stub-bulk";
    }

    @Override
    public String getDisplayName(Locale locale) {
        return "Stub Bulk Dataset Export";
    }

    @Override
    public Boolean isHarvestable() {
        return true;
    }

    @Override
    public Boolean isAvailableToUsers() {
        return true;
    }
    
    @Override
    public String getMediaType() {
        return "test";
    }
    
    @Override
    public void exportBulk(BulkDatasetContext context, OutputStream output) throws ExportException {
        /* Intentionally left blank for test class */
    }
}
