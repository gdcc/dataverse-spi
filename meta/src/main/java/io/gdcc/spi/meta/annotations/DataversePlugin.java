package io.gdcc.spi.meta.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a concrete plugin implementation class for metadata generation.
 * Plugin authors use this annotation to mark their plugin for scanning and loading.
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface DataversePlugin {
}
