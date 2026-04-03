package io.gdcc.spi.export.fixtures;

import io.gdcc.spi.export.ExportDataProvider;
import io.gdcc.spi.export.Exporter;
import io.gdcc.spi.meta.annotations.DataversePlugin;

import java.io.OutputStream;
import java.util.Locale;

/**
 * Minimal base-only Exporter implementation for processor integration testing.
 */
@DataversePlugin
public class StubJsonExporter implements Exporter {
    @Override
    public void exportDataset(ExportDataProvider dataProvider, OutputStream outputStream) {
        /* Intentionally left blank for test class */
    }

    @Override
    public String getFormatName() {
        return "stub-json";
    }

    @Override
    public String getDisplayName(Locale locale) {
        return "Stub JSON";
    }

    @Override
    public Boolean isHarvestable() {
        return false;
    }

    @Override
    public Boolean isAvailableToUsers() {
        return true;
    }

    @Override
    public String getMediaType() {
        return "application/json";
    }
}
