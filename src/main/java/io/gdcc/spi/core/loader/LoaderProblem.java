package io.gdcc.spi.core.loader;

import java.nio.file.Path;
import java.util.Set;

public sealed interface LoaderProblem permits
    LoaderProblem.SourceFailure,
    LoaderProblem.LocationFailure,
    LoaderProblem.DuplicateSources {
    
    String message();
    
    record SourceFailure(Throwable cause) implements LoaderProblem {
        @Override
        public String message() {
            return cause.getClass().getSimpleName() + ": " + cause.getMessage();
        }
    }
    
    record LocationFailure(Path location, Throwable cause) implements LoaderProblem {
        @Override
        public String message() {
            return "Loading from " + location + " failed: " + cause.getMessage();
        }
    }
    
    record DuplicateSources(Set<PluginSource> duplicateGroup) implements LoaderProblem {
        @Override
        public String message() {
            return "Duplicate plugin sources detected: " + duplicateGroup;
        }
    }
}
