package io.gdcc.spi.export.fixtures;

import io.gdcc.spi.export.ExportDataProvider;
import io.gdcc.spi.export.XMLExporter;
import io.gdcc.spi.meta.annotations.DataversePlugin;

import java.io.OutputStream;
import java.util.Locale;

/**
 * Minimal XMLExporter implementation for processor integration testing.
 *
 * <p>Does NOT override {@code getMediaType()} — the default from {@link XMLExporter}
 * satisfies the abstract declaration on {@link io.gdcc.spi.export.Exporter} because XMLExporter extends Exporter.</p>
 */
@DataversePlugin
public class StubDdiExporter implements XMLExporter {
    @Override
    public void exportDataset(ExportDataProvider dataProvider, OutputStream outputStream) {
        /* Intentionally left blank for test class */
    }

    @Override
    public String getFormatName() {
        return "stub-ddi";
    }

    @Override
    public String getDisplayName(Locale locale) {
        return "Stub DDI";
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
    public String getXMLNameSpace() {
        return "ddi:codebook:2_5";
    }

    @Override
    public String getXMLSchemaLocation() {
        return "https://ddialliance.org/Specification/DDI-Codebook/2.5/XMLSchema/codebook.xsd";
    }

    @Override
    public String getXMLSchemaVersion() {
        return "2.5";
    }
}
