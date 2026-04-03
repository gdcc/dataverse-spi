package io.gdcc.spi.core.loader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Represents the result of validating a set of plugins.
 * It's intended for internal use only.
 *
 * @param <T> The type of the plugin descriptors being validated.
 * @param accepted The set of plugins that were successfully validated and accepted.
 * @param rejected A mapping of plugins to a list of {@link LoaderProblem} instances providing detailed
 *                  reasons for their rejection.
 * @param warning A mapping of plugins to a list of {@link LoaderProblem} instances providing detailed
 *                 reasons why they would usually be rejected but are included by configuration choice.
 */
record PluginValidationResult<T>(
    Set<T> accepted,
    Map<T, List<LoaderProblem>> rejected,
    Map<T, List<LoaderProblem>> warning
) {
    
    /**
     * Combines multiple {@code PluginValidationResult} instances into a single result by merging their
     * accepted keys, rejected problems, and warnings. Accepted keys that are also present in either
     * rejected or warning collections are excluded from the final result.
     *
     * @param <T> the type of keys in the validation results
     * @param results the array of {@code PluginValidationResult} instances to combine; may include null values
     * @return a new {@code PluginValidationResult} instance where all provided results are merged,
     *         ensuring no overlap between accepted, rejected, and warning entries
     */
    @SafeVarargs
    static <T> PluginValidationResult<T> merge(PluginValidationResult<T>... results) {
        Set<T> mergedAccepted = new HashSet<>();
        Map<T, List<LoaderProblem>> mergedRejected = new HashMap<>();
        Map<T, List<LoaderProblem>> mergedWarning = new HashMap<>();
        
        Arrays.stream(results)
            .filter(Objects::nonNull)
            .forEach(result -> {
                if (result.accepted() != null) {
                    mergedAccepted.addAll(result.accepted());
                }
                
                if (result.rejected() != null) {
                    result.rejected().forEach((key, value) ->
                        mergedRejected.merge(key, new ArrayList<>(value), (left, right) -> {
                            left.addAll(right);
                            return left;
                        })
                    );
                }
                
                if (result.warning() != null) {
                    result.warning().forEach((key, value) ->
                        mergedWarning.merge(key, new ArrayList<>(value), (left, right) -> {
                            left.addAll(right);
                            return left;
                        })
                    );
                }
            });
        
        // Warnings and rejection win over acceptance.
        // Note: Keep warnings around even if a rejection also exists for the same descriptor,
        //       because the diagnostic information is still useful.
        mergedAccepted.removeAll(mergedWarning.keySet());
        mergedAccepted.removeAll(mergedRejected.keySet());
        
        return new PluginValidationResult<>(
            Set.copyOf(mergedAccepted),
            copyProblemMap(mergedRejected),
            copyProblemMap(mergedWarning)
        );
    }
    
    /**
     * Creates a defensive copy of the provided map where each key-value mapping is preserved, and the lists of
     * {@link LoaderProblem} are converted into immutable copies.
     *
     * @param <T> the type of the keys in the map
     * @param input the map containing keys and lists of {@link LoaderProblem} that needs to be copied
     * @return a new map where the original map's structure is maintained, and all lists are immutable
     */
    static <T> Map<T, List<LoaderProblem>> copyProblemMap(Map<T, List<LoaderProblem>> input) {
        Map<T, List<LoaderProblem>> copy = new HashMap<>();
        input.forEach((key, value) -> copy.put(key, List.copyOf(value)));
        return Map.copyOf(copy);
    }
    
}
