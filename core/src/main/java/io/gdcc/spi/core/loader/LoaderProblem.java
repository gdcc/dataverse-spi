package io.gdcc.spi.core.loader;

import io.gdcc.spi.meta.descriptor.PluginDescriptor;

import java.nio.file.Path;
import java.util.Set;

public sealed interface LoaderProblem permits LoaderProblem.DuplicateIdentity, LoaderProblem.LocationFailure, LoaderProblem.MissingServiceProviderRecord, LoaderProblem.PluginClassApiLevelMismatch, LoaderProblem.PluginClassApiLevelMissing, LoaderProblem.PluginClassMismatch, LoaderProblem.PluginClassNameCollision, LoaderProblem.PluginClassNameCollisionWithCore, LoaderProblem.PluginClassUnsupported, LoaderProblem.SourceFailure {
    
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
    
    record DuplicateIdentity(String normalizedIdentity, PluginDescriptor<?> source, PluginDescriptor<?> duplicate) implements LoaderProblem {
        @Override
        public String message() {
            return """
                   Plugin %s ( %s @ %s) normalized identity %s collides with Plugin %s ( %s @ %s)
                   """.formatted(
                        source.pluginClass().getCanonicalName(),
                        source.identity(),
                        source.sourceLocation(),
                        normalizedIdentity,
                        duplicate.pluginClass().getCanonicalName(),
                        duplicate.identity(),
                        duplicate.sourceLocation()
            );
        }
    }
    
    record MissingServiceProviderRecord(String className, String kind, Path source) implements LoaderProblem {
        @Override
        public String message() {
            return "Class " + className + " in " + source + " is missing entry in META-INF/services/" + kind;
        }
    }
    
    record PluginClassNameCollision(String className, Path source1, Path source2) implements LoaderProblem {
        @Override
        public String message() {
            return "Class " + className + " is defined in both " + source1 + " and " + source2;
        }
    }
    
    record PluginClassNameCollisionWithCore(String className, Path source) implements LoaderProblem {
        @Override
        public String message() {
            return "Class " + className + " is defined in both core and " + source;
        }
    }
    
    record PluginClassMismatch(String className, Path source, String pluginKind) implements LoaderProblem {
        @Override
        public String message() {
            return "Class " + className + " in " + source + " does not implement " + pluginKind;
        }
    }
    
    record PluginClassUnsupported(String className, Path source, String pluginContract) implements LoaderProblem {
        @Override
        public String message() {
            return "Class " + className + " in " + source + " implements unsupported plugin contract " + pluginContract;
        }
    }
    
    record PluginClassApiLevelMissing(String classname, Path source, String contractClass, int coreLevel) implements LoaderProblem {
        @Override
        public String message() {
            return "Class " + classname + " in " + source + " provides no API level for " + contractClass + ", but core expects " + coreLevel;
        }
    }
    
    record PluginClassApiLevelMismatch(String classname, Path source, int coreLevel, int pluginLevel) implements LoaderProblem {
        @Override
        public String message() {
            return "Class " + classname + " in " + source + " uses API level " + pluginLevel + " but core expects " + coreLevel;
        }
    }
}
