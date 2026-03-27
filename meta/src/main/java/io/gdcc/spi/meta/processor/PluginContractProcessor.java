package io.gdcc.spi.meta.processor;

import io.gdcc.spi.meta.annotations.PluginContract;
import io.gdcc.spi.meta.descriptor.Descriptor;
import io.gdcc.spi.meta.descriptor.DescriptorFormat;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Annotation processor generating build-time metadata for plugin implementations.
 *
 * <p>This processor scans classes marked with {@code @DataversePlugin}, discovers all implemented
 * plugin contracts annotated with {@code @PluginContract}, validates the contract graph, and emits:</p>
 *
 * <ol>
 *   <li>a per-plugin descriptor under {@value DescriptorFormat#DESCRIPTOR_DIRECTORY}, and</li>
 *   <li>a {@code META-INF/services/...} entry for the base plugin contract when safe to do so.</li>
 * </ol>
 *
 * <p>The descriptor captures the build-time view of:</p>
 * <ul>
 *   <li>the plugin implementation class,</li>
 *   <li>the plugin's base contract,</li>
 *   <li>all implemented contract API levels,</li>
 *   <li>all required provider API levels.</li>
 * </ul>
 *
 * <p>Service registration generation is intentionally cautious. If any implementation of a given base
 * contract uses {@code @AutoService}, this processor suppresses generated service output for that
 * entire contract to avoid two processors writing the same {@code META-INF/services/...} file.</p>
 *
 * <p>Errors are reported against the offending source element and then converted into a local
 * {@link ProcessorException}. This aborts processing of the current implementation only, allowing
 * the processor to continue and surface additional problems in the same compilation run.</p>
 */
public final class PluginContractProcessor extends AbstractProcessor {
    
    /**
     * Fully qualified name of the implementation marker annotation.
     *
     * <p>A string constant is used instead of a direct class literal so this processor can stay
     * tolerant during bootstrapping and module boundary changes.</p>
     *
     * @see io.gdcc.spi.meta.annotations.DataversePlugin
     */
    private static final String PLUGIN_IMPLEMENTATION_ANNOTATION = "io.gdcc.spi.meta.annotations.DataversePlugin";
    
    /**
     * Fully qualified name of the contract annotation found on plugin contract interfaces.
     *
     * @see io.gdcc.spi.meta.annotations.PluginContract
     */
    private static final String PLUGIN_CONTRACT_ANNOTATION = "io.gdcc.spi.meta.annotations.PluginContract";
    
    /**
     * Fully qualified name of the nested provider requirement annotation used inside
     * {@code @PluginContract.providers()}.
     *
     * @see io.gdcc.spi.meta.annotations.RequiredProvider
     */
    private static final String REQUIRED_PROVIDER_ANNOTATION = "io.gdcc.spi.meta.annotations.RequiredProvider";
    
    /**
     * Fully qualified name of {@code @AutoService}.
     *
     * <p>This processor does not depend on AutoService directly. It merely detects the annotation by
     * name so it can avoid generating conflicting ServiceLoader resources.</p>
     */
    private static final String AUTO_SERVICE_ANNOTATION = "com.google.auto.service.AutoService";
    
    /**
     * Fully qualified name of the common plugin super-interface.
     * @see io.gdcc.spi.meta.plugin.Plugin
     */
    private static final String PLUGIN_INTERFACE = "io.gdcc.spi.meta.plugin.Plugin";
    
    /**
     * Fully qualified name of the common provider super-interface.
     * @see io.gdcc.spi.meta.plugin.CoreProvider
     */
    private static final String CORE_PROVIDER_INTERFACE = "io.gdcc.spi.meta.plugin.CoreProvider";
    
    /**
     * Name of the compile-time constant field carrying the contract version.
     */
    private static final String API_LEVEL_FIELD = "API_LEVEL";
    
    /**
     * Output directory for generated plugin descriptors.
     */
    private static final String DESCRIPTOR_DIRECTORY = "META-INF/dataverse/plugins/";
    
    /**
     * Output directory for generated ServiceLoader files.
     */
    private static final String SERVICES_DIRECTORY = "META-INF/services/";
    
    /**
     * Cached utility for type operations such as assignability checks.
     */
    private Types types;
    
    /**
     * Cached utility for element lookup and annotation default resolution.
     */
    private Elements elements;
    
    /**
     * Descriptor models accumulated during processing, keyed by implementation class name.
     *
     * <p>Descriptors are written only after processing is over, which keeps resource generation
     * deterministic and avoids partial aggregate state.</p>
     */
    private final Map<String, Descriptor> descriptors = new LinkedHashMap<>();
    
    /**
     * Service registrations grouped by base contract name.
     *
     * <p>Each map entry corresponds to one future {@code META-INF/services/<fqcn>} file. A sorted
     * set is used to make generated output stable across compiler runs.</p>
     */
    private final Map<String, Set<String>> serviceImplementationsByContract = new LinkedHashMap<>();
    
    /**
     * Base contract names for which service file generation must be skipped.
     *
     * <p>If any implementation of a base contract uses {@code @AutoService}, that service type is
     * considered externally managed and this processor suppresses its own output for the same path.
     * This way, we do not have a race condition / conflict over one service file.</p>
     */
    private final Set<String> serviceTypesManagedExternally = new LinkedHashSet<>();
    
    /**
     * Types already inspected during the current compilation.
     *
     * <p>The processor performs additional model-wide validation beyond explicit {@code @DataversePlugin}
     * usages. Since the same type may reappear through multiple roots or hierarchy traversals, this set
     * keeps those checks idempotent and avoids duplicate diagnostics.</p>
     */
    private final Set<String> inspectedTypes = new LinkedHashSet<>();
    
    /**
     * Plugin implementations already converted into generated output models.
     *
     * <p>This is needed because implementations may be processed either explicitly through
     * {@code @DataversePlugin} or implicitly when they are discovered as plain {@code Plugin}
     * implementations during hierarchy inspection.</p>
     */
    private final Set<String> processedImplementations = new LinkedHashSet<>();
    
    /**
     * Initializes compiler utility helpers from the processing environment.
     *
     * @param processingEnv the active annotation processing environment
     */
    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.types = processingEnv.getTypeUtils();
        this.elements = processingEnv.getElementUtils();
    }
    
    /**
     * Returns the annotation types directly claimed by this processor.
     *
     * <p>The processor claims all annotations because it does not only react to explicitly annotated
     * {@code @DataversePlugin} classes. It also performs project-wide validation for plugin contracts,
     * provider contracts, and unannotated plugin implementations discovered in the type model.</p>
     *
     * @return the supported top-level annotation types
     */
    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of("*");
    }
    
    /**
     * Advertises support for the latest source version understood by the current compiler.
     *
     * <p>This is preferred over a hard-coded release because the processor mainly operates on the
     * annotation/type model and should remain usable across newer Java releases automatically.</p>
     *
     * @return the latest source version supported by the running compiler
     */
    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }
    
    /**
     * Main processor entry point for each annotation processing round.
     *
     * <p>During normal rounds, this processor performs two tasks:</p>
     * <ol>
     *   <li>it inspects all root types and their hierarchies for project-wide contract validation,</li>
     *   <li>it processes explicitly annotated {@code @DataversePlugin} classes.</li>
     * </ol>
     *
     * <p>During the final round, all accumulated descriptor and service models are written to the
     * compiler output.</p>
     *
     * @param annotations the annotations requested for this round
     * @param roundEnv the current round environment
     * @return {@code false} so other processors may continue to participate normally
     */
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        TypeElement markerAnnotation = elements.getTypeElement(PLUGIN_IMPLEMENTATION_ANNOTATION);
        if (markerAnnotation == null) {
            // If the marker annotation itself cannot be resolved, something is wrong with the
            // processor classpath. Returning false leaves room for other processors to continue.
            return false;
        }
        
        // Inspect all roots, not just annotated types. This enables strict enforcement for
        // plugin/provider contracts and lets us discover plain Plugin implementations that
        // should have used @DataversePlugin.
        for (Element root : roundEnv.getRootElements()) {
            if (root instanceof TypeElement typeElement) {
                try {
                    inspectTypeHierarchy(typeElement);
                } catch (ProcessorException ignored) {
                    // The concrete error has already been reported with source location.
                    // Continue with remaining roots to surface as many problems as possible.
                }
            }
        }
        
        for (Element element : roundEnv.getElementsAnnotatedWith(markerAnnotation)) {
            if (!(element instanceof TypeElement implementation)) {
                error(element, "@DataversePlugin may only be used on classes");
                continue;
            }
            
            try {
                processImplementation(implementation);
            } catch (ProcessorException ignored) {
                // A concrete error has already been reported with source location.
                // Continue with the next implementation so the user gets more than one error per run.
            }
        }
        
        if (roundEnv.processingOver()) {
            writeAllGeneratedResources();
        }
        
        return false;
    }
    
    /**
     * Processes one plugin implementation class.
     *
     * <p>The workflow is:</p>
     * <ol>
     *   <li>validate the class structurally,</li>
     *   <li>discover all implemented contracts in the full type hierarchy,</li>
     *   <li>identify exactly one base contract,</li>
     *   <li>collect contract and provider API levels,</li>
     *   <li>record descriptor output,</li>
     *   <li>record ServiceLoader output unless {@code @AutoService} takes over.</li>
     * </ol>
     *
     * @param implementation the plugin implementation class
     */
    private void processImplementation(TypeElement implementation) {
        String implementationClassName = implementation.getQualifiedName().toString();
        if (!processedImplementations.add(implementationClassName)) {
            // The implementation was already processed earlier in this compilation, for example
            // when discovered implicitly during type hierarchy inspection.
            return;
        }
        
        validateImplementationClass(implementation);
        
        Set<TypeElement> contracts = collectImplementedContracts(implementation);
        if (contracts.isEmpty()) {
            error(
                implementation,
                "No implemented plugin contracts found; implementations must implement a specific @PluginContract interface"
            );
            throw new ProcessorException();
        }
        
        TypeElement baseContract = null;
        Map<String, Integer> contractLevels = new LinkedHashMap<>();
        Map<String, Integer> providerLevels = new LinkedHashMap<>();
        
        for (TypeElement contract : sortByQualifiedName(contracts)) {
            PluginContractModel model = readPluginContractModel(contract);
            
            if (model.role() == PluginContract.Role.BASE) {
                if (baseContract != null) {
                    error(
                        implementation,
                        "Implementation must not implement multiple base plugin contracts: "
                            + baseContract.getQualifiedName() + " and " + contract.getQualifiedName()
                    );
                    throw new ProcessorException();
                }
                baseContract = contract;
            }
            
            validateRequiredContracts(implementation, contract, contracts, model);
            
            // The API level is intentionally read from the compile-time constant present on the
            // contract interface visible during this compilation. This preserves the build-time
            // contract snapshot we later need at runtime.
            int contractApiLevel = readIntConstant(contract, API_LEVEL_FIELD);
            String contractFQCN = contract.getQualifiedName().toString();
            // The following is just a precaution. As we look into these during compile time, it's hard to imagine
            // a scenario where the levels ever actually differ.
            if (contractLevels.containsKey(contractFQCN) && contractLevels.get(contractFQCN) != contractApiLevel) {
                error(implementation, "Conflicting API levels on contract implementation: " + contractFQCN);
            } else {
                contractLevels.put(contract.getQualifiedName().toString(), contractApiLevel);
            }
            
            // Provider requirements accumulate across all implemented contracts/capabilities.
            // Conflicting requirements are rejected below.
            Map<String, Integer> requiredProviders = readProviderLevels(model.providers(), implementation);
            mergeProviderLevels(providerLevels, requiredProviders, implementation);
        }
        
        if (baseContract == null) {
            error(implementation, "Implementation must implement exactly one Role.BASE @PluginContract");
            throw new ProcessorException();
        }
        
        String baseContractName = baseContract.getQualifiedName().toString();
        
        descriptors.put(
            implementationClassName,
            new Descriptor(
                implementationClassName,
                baseContractName,
                contractLevels,
                providerLevels
            )
        );
        
        if (hasAutoServiceAnnotation(implementation)) {
            // Skip generated META-INF/services output for the entire base contract to avoid
            // resource collisions with AutoService, which writes the same aggregate file path.
            serviceTypesManagedExternally.add(baseContractName);
            warning(
                implementation,
                "@AutoService detected; generated META-INF/services entry for "
                    + baseContractName
                    + " will be skipped to avoid conflicts"
            );
        } else {
            serviceImplementationsByContract
                .computeIfAbsent(baseContractName, ignored -> new TreeSet<>())
                .add(implementationClassName);
        }
    }
    
    /**
     * Validates the basic structural requirements for a plugin implementation.
     *
     * @param implementation the implementation class to validate
     */
    private void validateImplementationClass(TypeElement implementation) {
        if (implementation.getKind() != ElementKind.CLASS) {
            error(implementation, "@DataversePlugin may only be used on classes");
            throw new ProcessorException();
        }
        
        if (!implementation.getModifiers().contains(Modifier.PUBLIC)) {
            error(implementation, "@DataversePlugin implementations must be public");
            throw new ProcessorException();
        }
        
        if (implementation.getModifiers().contains(Modifier.ABSTRACT)) {
            error(implementation, "@DataversePlugin implementations must not be abstract");
            throw new ProcessorException();
        }
    }
    
    /**
     * Collects all plugin contracts implemented by the given class, including inherited ones.
     *
     * <p>The traversal walks the full type hierarchy breadth-first across both superclasses and
     * interfaces so that indirectly inherited contracts and capability interfaces are discovered too.</p>
     *
     * @param implementation the implementation class to inspect
     * @return all implemented types recognized as plugin contracts
     */
    private Set<TypeElement> collectImplementedContracts(TypeElement implementation) {
        Set<TypeElement> result = new LinkedHashSet<>();
        Set<String> visited = new LinkedHashSet<>();
        Deque<TypeMirror> queue = new ArrayDeque<>();
        queue.addLast(implementation.asType());
        
        while (!queue.isEmpty()) {
            TypeMirror current = queue.removeFirst();
            if (current.getKind() == TypeKind.NONE) {
                continue;
            }
            if (!(current instanceof DeclaredType declaredType)) {
                continue;
            }
            
            Element currentElement = declaredType.asElement();
            if (!(currentElement instanceof TypeElement currentType)) {
                continue;
            }
            
            String qualifiedName = currentType.getQualifiedName().toString();
            if (!visited.add(qualifiedName)) {
                continue;
            }
            
            if (isPluginContract(currentType)) {
                result.add(currentType);
            }
            
            for (TypeMirror iface : currentType.getInterfaces()) {
                queue.addLast(iface);
            }
            
            TypeMirror superclass = currentType.getSuperclass();
            if (superclass != null && superclass.getKind() != TypeKind.NONE) {
                queue.addLast(superclass);
            }
        }
        
        return result;
    }
    
    /**
     * Traverses a type hierarchy and applies project-wide validation rules.
     *
     * <p>This method exists because the processor validates more than explicitly annotated
     * implementations. It also enforces that:</p>
     * <ul>
     *   <li>plugin interfaces carry {@code @PluginContract},</li>
     *   <li>provider interfaces declare {@code API_LEVEL},</li>
     *   <li>concrete plugin implementations use {@code @DataversePlugin}, or at least trigger a warning.</li>
     * </ul>
     *
     * @param typeElement the root type to inspect
     */
    private void inspectTypeHierarchy(TypeElement typeElement) {
        Deque<TypeElement> queue = new ArrayDeque<>();
        queue.addLast(typeElement);
        
        while (!queue.isEmpty()) {
            TypeElement current = queue.removeFirst();
            String qualifiedName = current.getQualifiedName().toString();
            if (!inspectedTypes.add(qualifiedName)) {
                continue;
            }
            
            inspectType(current);
            
            for (TypeMirror iface : current.getInterfaces()) {
                TypeElement interfaceType = asTypeElement(iface);
                if (interfaceType != null) {
                    queue.addLast(interfaceType);
                }
            }
            
            TypeMirror superclass = current.getSuperclass();
            TypeElement superType = asTypeElement(superclass);
            if (superType != null && superclass.getKind() != TypeKind.NONE) {
                queue.addLast(superType);
            }
        }
    }
    
    /**
     * Applies validation rules to a single type discovered during hierarchy inspection.
     *
     * @param typeElement the type to inspect
     */
    private void inspectType(TypeElement typeElement) {
        validatePluginContractUsage(typeElement);
        validateDirectBaseTypeImplementations(typeElement);
        
        if (isPluginInterfaceCandidate(typeElement)) {
            if (findAnnotationMirror(typeElement, PLUGIN_CONTRACT_ANNOTATION) == null) {
                error(typeElement, "Plugin interfaces must declare @PluginContract");
                throw new ProcessorException();
            }
            
            validateApiLevelConstant(typeElement);
        }
        
        if (isProviderInterfaceCandidate(typeElement)) {
            validateApiLevelConstant(typeElement);
        }
        
        if (isPluginImplementationCandidate(typeElement)
            && findAnnotationMirror(typeElement, PLUGIN_IMPLEMENTATION_ANNOTATION) == null) {
            warning(
                typeElement,
                "Plugin implementation should declare @DataversePlugin; processing it implicitly"
            );
            
            // Even without the annotation, we still process the implementation. This keeps the
            // migration path smooth and ensures metadata generation does not depend solely on
            // authors remembering one annotation.
            processImplementation(typeElement);
        }
    }
    
    /**
     * Rejects direct implementations of the foundational base types {@code Plugin} and
     * {@code CoreProvider}.
     *
     * <p>These two types are infrastructure-level marker/base interfaces only. Loadable plugins
     * and concrete providers must instead implement a specific contract interface extending one
     * of these base types. Otherwise, no meaningful compatibility contract can be derived.</p>
     *
     * @param typeElement the type currently being inspected
     */
    private void validateDirectBaseTypeImplementations(TypeElement typeElement) {
        if (typeElement.getKind() != ElementKind.CLASS) {
            return;
        }
        
        if (directlyImplementsType(typeElement, PLUGIN_INTERFACE)) {
            error(
                typeElement,
                "Plugin implementations must implement a specific plugin contract interface, not Plugin directly"
            );
            throw new ProcessorException();
        }
        
        if (directlyImplementsType(typeElement, CORE_PROVIDER_INTERFACE)) {
            error(
                typeElement,
                "Core provider implementations must implement a specific provider interface, not CoreProvider directly"
            );
            throw new ProcessorException();
        }
    }
    
    /**
     * Checks whether a type directly declares the given interface in its {@code implements} clause.
     *
     * <p>This is stricter than assignability: it only matches explicit direct implementation and is
     * used to reject classes that target the framework base interfaces {@code Plugin} or
     * {@code CoreProvider} directly.</p>
     *
     * @param typeElement the type to inspect
     * @param targetTypeName the fully qualified interface name to look for
     * @return {@code true} if the type directly implements the target interface
     */
    private boolean directlyImplementsType(TypeElement typeElement, String targetTypeName) {
        for (TypeMirror interfaceType : typeElement.getInterfaces()) {
            TypeElement interfaceElement = asTypeElement(interfaceType);
            if (interfaceElement != null && interfaceElement.getQualifiedName().contentEquals(targetTypeName)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Determines whether a type qualifies as an implementation candidate for a plugin.
     *
     * @param typeElement the type to inspect
     * @return {@code true} if the type is a concrete class implementing {@code Plugin}
     */
    private boolean isPluginImplementationCandidate(TypeElement typeElement) {
        if (typeElement.getKind() != ElementKind.CLASS) {
            return false;
        }
        if (typeElement.getModifiers().contains(Modifier.ABSTRACT)) {
            return false;
        }
        return implementsType(typeElement, PLUGIN_INTERFACE) && !isExactType(typeElement, PLUGIN_INTERFACE);
    }
    
    /**
     * Determines whether a type is a plugin interface candidate that must declare {@code @PluginContract}.
     *
     * @param typeElement the type to inspect
     * @return {@code true} if the type is an interface extending {@code Plugin}
     */
    private boolean isPluginInterfaceCandidate(TypeElement typeElement) {
        return typeElement.getKind() == ElementKind.INTERFACE
            && implementsType(typeElement, PLUGIN_INTERFACE)
            && !isExactType(typeElement, PLUGIN_INTERFACE);
    }
    
    /**
     * Determines whether a type is a provider interface candidate that must declare {@code API_LEVEL}.
     *
     * @param typeElement the type to inspect
     * @return {@code true} if the type is an interface extending {@code CoreProvider}
     */
    private boolean isProviderInterfaceCandidate(TypeElement typeElement) {
        return typeElement.getKind() == ElementKind.INTERFACE
            && implementsType(typeElement, CORE_PROVIDER_INTERFACE)
            && !isExactType(typeElement, CORE_PROVIDER_INTERFACE);
    }
    
    /**
     * Tests whether the given type is assignable to another type identified by fully qualified name.
     *
     * @param typeElement the source type
     * @param targetTypeName the fully qualified target type name
     * @return {@code true} if the source type is assignable to the target type
     */
    private boolean implementsType(TypeElement typeElement, String targetTypeName) {
        TypeElement targetType = elements.getTypeElement(targetTypeName);
        if (targetType == null) {
            return false;
        }
        
        return types.isAssignable(
            types.erasure(typeElement.asType()),
            types.erasure(targetType.asType())
        );
    }
    
    /**
     * Checks whether the given type is exactly the named type itself, not merely a subtype.
     *
     * @param typeElement the type to inspect
     * @param targetTypeName the fully qualified target type name
     * @return {@code true} if both names are identical
     */
    private boolean isExactType(TypeElement typeElement, String targetTypeName) {
        return typeElement.getQualifiedName().contentEquals(targetTypeName);
    }
    
    /**
     * Verifies that {@code @PluginContract} is only used on interfaces.
     *
     * <p>Although the annotation is intended for SPI interfaces, Java's annotation target model
     * cannot express "interfaces only". This processor therefore enforces the rule explicitly and
     * fails compilation when the annotation is placed on classes, enums, records, or other
     * non-interface types.</p>
     *
     * @param typeElement the type currently being inspected
     */
    private void validatePluginContractUsage(TypeElement typeElement) {
        if (findAnnotationMirror(typeElement, PLUGIN_CONTRACT_ANNOTATION) == null) {
            return;
        }
        
        if (typeElement.getKind() != ElementKind.INTERFACE) {
            error(typeElement, "@PluginContract may only be declared on interfaces");
            throw new ProcessorException();
        }
    }
    
    /**
     * Determines whether the given type is a plugin contract.
     *
     * <p>A type qualifies as a plugin contract only when it is annotated with
     * {@code @PluginContract} and is assignable to the common plugin super-interface.</p>
     *
     * @param typeElement the type to test
     * @return {@code true} if the type is a plugin contract
     */
    private boolean isPluginContract(TypeElement typeElement) {
        if (findAnnotationMirror(typeElement, PLUGIN_CONTRACT_ANNOTATION) == null) {
            return false;
        }
        
        TypeElement pluginType = elements.getTypeElement(PLUGIN_INTERFACE);
        if (pluginType == null) {
            return false;
        }
        
        return types.isAssignable(
            types.erasure(typeElement.asType()),
            types.erasure(pluginType.asType())
        );
    }
    
    /**
     * Reads and validates the metadata of one plugin contract interface.
     *
     * @param contract the contract interface
     * @return the extracted in-memory contract model
     */
    private PluginContractModel readPluginContractModel(TypeElement contract) {
        AnnotationMirror annotation = findAnnotationMirror(contract, PLUGIN_CONTRACT_ANNOTATION);
        if (annotation == null) {
            error(contract, "Missing @PluginContract");
            throw new ProcessorException();
        }
        
        validateApiLevelConstant(contract);
        
        PluginContract.Role role = readContractRole(annotation, contract);
        List<TypeElement> requiredContracts = readClassArrayAnnotationValue(annotation, "requires");
        List<TypeElement> providers = readRequiredProviders(annotation);
        
        return new PluginContractModel(role, List.copyOf(requiredContracts), List.copyOf(providers));
    }
    
    /**
     * Reads the {@code role} member of a {@code @PluginContract} annotation.
     *
     * @param annotation the contract annotation mirror
     * @param contract the annotated contract, used for diagnostics
     * @return the parsed contract role
     */
    private PluginContract.Role readContractRole(AnnotationMirror annotation, TypeElement contract) {
        AnnotationValue value = getAnnotationValue(annotation, "role");
        if (value == null) {
            error(contract, "@PluginContract.role is required");
            throw new ProcessorException();
        }
        
        Object raw = value.getValue();
        if (!(raw instanceof VariableElement enumConstant)) {
            error(contract, "@PluginContract.role must be an enum constant");
            throw new ProcessorException();
        }
        
        try {
            return PluginContract.Role.valueOf(enumConstant.getSimpleName().toString());
        } catch (IllegalArgumentException ex) {
            error(contract, "Unsupported @PluginContract.role: " + enumConstant.getSimpleName());
            throw new ProcessorException();
        }
    }
    
    /**
     * Verifies that the given type declares a valid compile-time constant {@code API_LEVEL} field.
     *
     * @param contract the contract or provider type to validate
     */
    private void validateApiLevelConstant(TypeElement contract) {
        readIntConstant(contract, API_LEVEL_FIELD);
    }
    
    /**
     * Reads a compile-time {@code int} constant from a type.
     *
     * @param type the owning type
     * @param fieldName the field to locate
     * @return the constant value
     */
    private int readIntConstant(TypeElement type, String fieldName) {
        for (Element enclosed : type.getEnclosedElements()) {
            if (enclosed instanceof VariableElement variable
                && variable.getSimpleName().contentEquals(fieldName)) {
                Object value = variable.getConstantValue();
                if (value instanceof Integer intValue) {
                    return intValue;
                }
                
                error(type, type.getQualifiedName() + "." + fieldName + " must be a compile-time int constant");
                throw new ProcessorException();
            }
        }
        
        error(type, type.getQualifiedName() + " must declare int " + fieldName);
        throw new ProcessorException();
    }
    
    /**
     * Validates that all contracts required by the current contract are also implemented.
     *
     * @param implementation the concrete plugin implementation
     * @param contract the contract currently being validated
     * @param allImplementedContracts all discovered contracts of the implementation
     * @param model the parsed model of the current contract
     */
    private void validateRequiredContracts(
        TypeElement implementation,
        TypeElement contract,
        Set<TypeElement> allImplementedContracts,
        PluginContractModel model
    ) {
        Set<String> implementedNames = new LinkedHashSet<>();
        for (TypeElement implemented : allImplementedContracts) {
            implementedNames.add(implemented.getQualifiedName().toString());
        }
        
        for (TypeElement requiredContract : model.requiredContracts()) {
            String requiredName = requiredContract.getQualifiedName().toString();
            if (!implementedNames.contains(requiredName)) {
                error(
                    implementation,
                    "Implementation of contract " + contract.getQualifiedName()
                        + " also requires contract " + requiredName
                );
                throw new ProcessorException();
            }
        }
    }
    
    /**
     * Resolves the API levels of all providers required by the current contract.
     *
     * @param providerTypes the provider interfaces referenced by the contract annotation
     * @param implementation the concrete implementation being processed, used for diagnostics
     * @return a map from provider class name to required API level
     */
    private Map<String, Integer> readProviderLevels(List<TypeElement> providerTypes, TypeElement implementation) {
        Map<String, Integer> result = new LinkedHashMap<>();
        TypeElement coreProviderType = elements.getTypeElement(CORE_PROVIDER_INTERFACE);
        if (coreProviderType == null) {
            error(implementation, "Cannot resolve " + CORE_PROVIDER_INTERFACE);
            throw new ProcessorException();
        }
        
        for (TypeElement providerType : providerTypes) {
            if (!types.isAssignable(
                types.erasure(providerType.asType()),
                types.erasure(coreProviderType.asType())
            )) {
                error(
                    implementation,
                    "Required provider " + providerType.getQualifiedName()
                        + " does not implement " + CORE_PROVIDER_INTERFACE
                );
                throw new ProcessorException();
            }
            
            int apiLevel = readIntConstant(providerType, API_LEVEL_FIELD);
            result.put(providerType.getQualifiedName().toString(), apiLevel);
        }
        
        return result;
    }
    
    /**
     * Merges provider API level requirements from one contract into the accumulated set.
     *
     * <p>If the same provider is required with different API levels by different contracts,
     * processing fails because the resulting runtime expectation would be ambiguous.</p>
     *
     * @param merged the accumulated provider requirements
     * @param additional the provider requirements from the current contract
     * @param implementation the concrete implementation, used for diagnostics
     */
    private void mergeProviderLevels(
        Map<String, Integer> merged,
        Map<String, Integer> additional,
        TypeElement implementation
    ) {
        additional.forEach((providerName, apiLevel) -> {
            Integer existing = merged.putIfAbsent(providerName, apiLevel);
            if (existing != null && existing.intValue() != apiLevel) {
                error(
                    implementation,
                    "Conflicting API levels for provider " + providerName
                        + ": " + existing + " vs " + apiLevel
                );
                throw new ProcessorException();
            }
        });
    }
    
    /**
     * Checks whether the implementation class uses {@code @AutoService}.
     *
     * @param implementation the implementation class
     * @return {@code true} if {@code @AutoService} is present
     */
    private boolean hasAutoServiceAnnotation(TypeElement implementation) {
        return findAnnotationMirror(implementation, AUTO_SERVICE_ANNOTATION) != null;
    }
    
    /**
     * Writes all accumulated generated resources after processing is complete.
     *
     * <p>Descriptors are always written. ServiceLoader files are written only for service types that
     * are not externally managed via {@code @AutoService}.</p>
     */
    private void writeAllGeneratedResources() {
        for (Descriptor descriptor : descriptors.values()) {
            writeDescriptor(descriptor);
        }
        
        for (Map.Entry<String, Set<String>> entry : serviceImplementationsByContract.entrySet()) {
            String serviceType = entry.getKey();
            if (serviceTypesManagedExternally.contains(serviceType)) {
                continue;
            }
            writeServiceFile(serviceType, entry.getValue());
        }
    }
    
    /**
     * Writes one generated plugin descriptor file.
     *
     * @param descriptor the plugin descriptor model to serialize
     */
    private void writeDescriptor(Descriptor descriptor) {
        String resourceName = DescriptorFormat.toPath(descriptor.klass());
        
        try {
            FileObject resource = processingEnv
                .getFiler()
                .createResource(StandardLocation.CLASS_OUTPUT, "", resourceName);
            
            try (Writer writer = resource.openWriter()) {
                DescriptorFormat.write(descriptor, writer);
            }
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(
                Diagnostic.Kind.ERROR,
                "Failed to write descriptor for " + descriptor.klass() + ": " + e.getMessage()
            );
        }
    }
    
    /**
     * Writes one ServiceLoader registration file for a base contract.
     * This is simply a re-implementation of what we did before with @AutoService and their processor
     *
     * @param serviceTypeName the fully qualified name of the service interface
     * @param implementations the implementation class names to register
     */
    private void writeServiceFile(String serviceTypeName, Set<String> implementations) {
        String resourceName = SERVICES_DIRECTORY + serviceTypeName;
        
        try {
            FileObject resource = processingEnv.getFiler()
                .createResource(StandardLocation.CLASS_OUTPUT, "", resourceName);
            
            try (Writer writer = resource.openWriter()) {
                for (String implementation : implementations) {
                    writer.write(implementation);
                    writer.write(System.lineSeparator());
                }
            }
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(
                Diagnostic.Kind.ERROR,
                "Failed to write service file for " + serviceTypeName + ": " + e.getMessage()
            );
        }
    }
    
    /**
     * Finds an annotation mirror on the given element by fully qualified annotation type name.
     *
     * @param element the annotated element
     * @param annotationTypeName the fully qualified annotation type name
     * @return the matching annotation mirror, or {@code null} if absent
     */
    private AnnotationMirror findAnnotationMirror(Element element, String annotationTypeName) {
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            Element annotationElement = mirror.getAnnotationType().asElement();
            if (annotationElement instanceof TypeElement annotationType
                && annotationType.getQualifiedName().contentEquals(annotationTypeName)) {
                return mirror;
            }
        }
        return null;
    }
    
    /**
     * Resolves one annotation member value, including defaults.
     *
     * @param annotation the annotation mirror
     * @param memberName the member to resolve
     * @return the resolved annotation value, or {@code null} if not found
     */
    private AnnotationValue getAnnotationValue(AnnotationMirror annotation, String memberName) {
        Map<? extends ExecutableElement, ? extends AnnotationValue> values =
            elements.getElementValuesWithDefaults(annotation);
        
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : values.entrySet()) {
            if (entry.getKey().getSimpleName().contentEquals(memberName)) {
                return entry.getValue();
            }
        }
        
        return null;
    }
    
    /**
     * Reads an annotation member containing an array of class literals.
     *
     * <p>During annotation processing, class-valued members are represented as {@link TypeMirror}s
     * within {@link AnnotationValue}s. This helper converts them into {@link TypeElement}s.</p>
     *
     * @param annotation the annotation mirror
     * @param memberName the member containing class literals
     * @return the referenced types, preserving declaration order
     */
    private List<TypeElement> readClassArrayAnnotationValue(AnnotationMirror annotation, String memberName) {
        AnnotationValue value = getAnnotationValue(annotation, memberName);
        if (value == null) {
            return List.of();
        }
        
        Object raw = value.getValue();
        if (!(raw instanceof List<?> values)) {
            return List.of();
        }
        
        List<TypeElement> result = new ArrayList<>();
        for (Object entry : values) {
            if (!(entry instanceof AnnotationValue annotationValue)) {
                continue;
            }
            
            Object classValue = annotationValue.getValue();
            if (!(classValue instanceof TypeMirror typeMirror)) {
                continue;
            }
            
            TypeElement typeElement = asTypeElement(typeMirror);
            if (typeElement != null) {
                result.add(typeElement);
            }
        }
        
        return List.copyOf(result);
    }
    
    /**
     * Reads the nested {@code providers()} member of a {@code @PluginContract} annotation.
     *
     * <p>The provider information is stored as nested {@code @RequiredProvider} annotations. This helper
     * unwraps those nested annotations and returns the referenced provider types.</p>
     *
     * @param pluginContractAnnotation the plugin contract annotation mirror
     * @return provider types referenced by the contract
     */
    private List<TypeElement> readRequiredProviders(AnnotationMirror pluginContractAnnotation) {
        AnnotationValue providersValue = getAnnotationValue(pluginContractAnnotation, "providers");
        if (providersValue == null) {
            return List.of();
        }
        
        Object raw = providersValue.getValue();
        if (!(raw instanceof List<?> values)) {
            return List.of();
        }
        
        List<TypeElement> result = new ArrayList<>();
        for (Object entry : values) {
            if (!(entry instanceof AnnotationValue annotationValue)) {
                continue;
            }
            
            Object nested = annotationValue.getValue();
            if (!(nested instanceof AnnotationMirror providerAnnotation)) {
                continue;
            }
            
            TypeElement providerAnnotationType = asTypeElement(providerAnnotation.getAnnotationType());
            if (providerAnnotationType == null
                || !providerAnnotationType.getQualifiedName().contentEquals(REQUIRED_PROVIDER_ANNOTATION)) {
                continue;
            }
            
            AnnotationValue providerClassValue = getAnnotationValue(providerAnnotation, "value");
            if (providerClassValue == null) {
                continue;
            }
            
            Object providerRaw = providerClassValue.getValue();
            if (!(providerRaw instanceof TypeMirror providerTypeMirror)) {
                continue;
            }
            
            TypeElement providerType = asTypeElement(providerTypeMirror);
            if (providerType != null) {
                result.add(providerType);
            }
        }
        
        return List.copyOf(result);
    }
    
    /**
     * Converts a declared type mirror into its corresponding type element.
     *
     * @param typeMirror the type mirror to convert
     * @return the type element, or {@code null} if the mirror is not a declared type
     */
    private TypeElement asTypeElement(TypeMirror typeMirror) {
        if (!(typeMirror instanceof DeclaredType declaredType)) {
            return null;
        }
        
        Element element = declaredType.asElement();
        return element instanceof TypeElement typeElement ? typeElement : null;
    }
    
    /**
     * Returns the given types sorted by fully qualified name for deterministic processing order.
     *
     * @param typesToSort the types to sort
     * @return a sorted list view
     */
    private List<TypeElement> sortByQualifiedName(Set<TypeElement> typesToSort) {
        return typesToSort.stream()
            .sorted(Comparator.comparing(type -> type.getQualifiedName().toString()))
            .toList();
    }
    
    /**
     * Emits a compiler error message associated with a source element.
     *
     * @param element the source element to associate with the diagnostic
     * @param message the diagnostic text
     */
    private void error(Element element, String message) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, element);
    }
    
    /**
     * Emits a compiler warning message associated with a source element.
     *
     * @param element the source element to associate with the diagnostic
     * @param message the diagnostic text
     */
    private void warning(Element element, String message) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, message, element);
    }
    
    /**
     * Internal in-memory representation of one contract interface.
     *
     * @param role whether the contract is a base contract or a capability
     * @param requiredContracts contracts that must also be implemented
     * @param providers providers required by this contract
     */
    private record PluginContractModel(
        PluginContract.Role role,
        List<TypeElement> requiredContracts,
        List<TypeElement> providers
    ) {
    }
    
    /**
     * Local control-flow exception used to abort processing of a single implementation after an error.
     *
     * <p>This avoids deeply nested conditional code while still allowing the processor to continue
     * with other plugin implementations in the same round.</p>
     */
    private static final class ProcessorException extends RuntimeException {
    }
}