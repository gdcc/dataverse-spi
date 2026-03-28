package io.gdcc.spi.core.loader;

/**
 * @param ENFORCE_SINGLE_SOURCE_MATCHING_PLUGINS_ONLY
 *        When activated, any source may only provide plugins for a single given base plugin contract interface.
 *        In case any non-compliant plugin is found within, the loader will refrain from loading from the source entirely.
 *        When deactivated, any non-matching plugins will simply be ignored.
 *        Default: false
 * @param EMIT_WARNINGS_ON_MULTI_PLUGIN_SOURCE
 *        If {@link #ENFORCE_SINGLE_SOURCE_MATCHING_PLUGINS_ONLY} is deactivated, either log a warning (true) or not (false).
 *        Default: false
 * @param ABORT_ON_COMPATIBILITY_PROBLEMS
 *        Abort loading when detecting any problems before actually loading classes (API level verification, etc.).
 *        Default: true
 * @param ABORT_ON_DUPLICATED_IDENTITIES
 *        Abort loading when detecting any plugins with duplicated identities, making them undistinguishable for users.
 *        Default: true
 * @param ENFORCE_UNAMBIGUOUS_PLUGIN_IDENTITIES
 *        When activated, any plugin must have a unique identity within its source, ensuring unambiguous identification.
 *        Any plugin's identity that differs by case or special chars only will be seen as a duplicate.
 *        Default: true
 */
public record LoaderConfiguration(
    boolean ENFORCE_SINGLE_SOURCE_MATCHING_PLUGINS_ONLY,
    boolean EMIT_WARNINGS_ON_MULTI_PLUGIN_SOURCE,
    boolean ABORT_ON_COMPATIBILITY_PROBLEMS,
    boolean ABORT_ON_DUPLICATED_IDENTITIES,
    boolean ENFORCE_UNAMBIGUOUS_PLUGIN_IDENTITIES
) {

    public static LoaderConfiguration defaultConfiguration() {
        return new LoaderConfiguration(
            false,
            false,
            true,
            true,
            true
        );
    }

}
