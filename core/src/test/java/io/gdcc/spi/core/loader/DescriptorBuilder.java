package io.gdcc.spi.core.loader;

import io.gdcc.spi.core.test.basic.TestContract;
import io.gdcc.spi.core.test.basic.TestPlugin;
import io.gdcc.spi.meta.descriptor.Descriptor;
import io.gdcc.spi.meta.descriptor.DescriptorFormat;
import io.gdcc.spi.meta.descriptor.SourcedDescriptor;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import java.util.function.UnaryOperator;

final class DescriptorBuilder {
    private Path source;
    private String className;
    private String kind;
    private Map<String, Integer> contracts;
    private Map<String, Integer> requiredProviders;
    
    private DescriptorBuilder() {
    }
    
    static DescriptorBuilder aDescriptor() {
        DescriptorBuilder builder = new DescriptorBuilder();
        builder.source = Path.of("target", "test-classes");
        builder.className = DescriptorFormat.transformClassName(TestPlugin.class);
        builder.kind = DescriptorFormat.transformClassName(TestContract.class);
        builder.contracts = new LinkedHashMap<>(
            Map.of(
                DescriptorFormat.transformClassName(TestContract.class),
                TestContract.API_LEVEL
            ));
        builder.requiredProviders = new LinkedHashMap<>();
        return builder;
    }
    
    DescriptorBuilder but() {
        DescriptorBuilder copy = new DescriptorBuilder();
        copy.source = this.source;
        copy.className = this.className;
        copy.kind = this.kind;
        copy.contracts = new LinkedHashMap<>(this.contracts);
        copy.requiredProviders = new LinkedHashMap<>(this.requiredProviders);
        return copy;
    }
    
    DescriptorBuilder withSource(Path source) {
        this.source = source;
        return this;
    }
    
    DescriptorBuilder withSource(String first, String... more) {
        this.source = Path.of(first, more);
        return this;
    }
    
    DescriptorBuilder mapSource(UnaryOperator<Path> mapper) {
        this.source = mapper.apply(this.source);
        return this;
    }
    
    DescriptorBuilder withClassName(String className) {
        this.className = className;
        return this;
    }
    
    DescriptorBuilder withClassName(Class<?> implementationClass) {
        this.className = DescriptorFormat.transformClassName(implementationClass);
        return this;
    }
    
    DescriptorBuilder mapClassName(UnaryOperator<String> mapper) {
        this.className = mapper.apply(this.className);
        return this;
    }
    
    DescriptorBuilder withClassPackage(String packageName) {
        int lastDot = className.lastIndexOf('.');
        String simpleName = lastDot >= 0 ? className.substring(lastDot + 1) : className;
        this.className = packageName == null || packageName.isBlank()
            ? simpleName
            : packageName + "." + simpleName;
        return this;
    }
    
    DescriptorBuilder withKind(String kind) {
        this.kind = kind;
        return this;
    }
    
    DescriptorBuilder withKind(Class<?> kindClass) {
        this.kind = DescriptorFormat.transformClassName(kindClass);
        return this;
    }
    
    DescriptorBuilder mapKind(UnaryOperator<String> mapper) {
        this.kind = mapper.apply(this.kind);
        return this;
    }
    
    DescriptorBuilder withContracts(Map<String, Integer> contracts) {
        this.contracts = new LinkedHashMap<>(contracts);
        return this;
    }
    
    DescriptorBuilder withoutContracts() {
        this.contracts.clear();
        return this;
    }
    
    DescriptorBuilder withContract(String contract, int level) {
        this.contracts.put(contract, level);
        return this;
    }
    
    DescriptorBuilder withContract(Class<?> contractClass, int level) {
        return withContract(DescriptorFormat.transformClassName(contractClass), level);
    }
    
    DescriptorBuilder withoutContract(String contract) {
        this.contracts.remove(contract);
        return this;
    }
    
    DescriptorBuilder withoutContract(Class<?> contractClass) {
        return withoutContract(DescriptorFormat.transformClassName(contractClass));
    }
    
    DescriptorBuilder mapContract(String contract, IntUnaryOperator mapper) {
        Integer current = this.contracts.get(contract);
        if (current == null) {
            throw new IllegalArgumentException("Contract not present: " + contract);
        }
        this.contracts.put(contract, mapper.applyAsInt(current));
        return this;
    }
    
    DescriptorBuilder mapContract(Class<?> contractClass, IntUnaryOperator mapper) {
        return mapContract(DescriptorFormat.transformClassName(contractClass), mapper);
    }
    
    DescriptorBuilder withRequiredProviders(Map<String, Integer> requiredProviders) {
        this.requiredProviders = new LinkedHashMap<>(requiredProviders);
        return this;
    }
    
    DescriptorBuilder withRequiredProvider(String provider, int level) {
        this.requiredProviders.put(provider, level);
        return this;
    }
    
    DescriptorBuilder withRequiredProvider(Class<?> providerClass, int level) {
        return withRequiredProvider(DescriptorFormat.transformClassName(providerClass), level);
    }
    
    DescriptorBuilder withoutRequiredProvider(String provider) {
        this.requiredProviders.remove(provider);
        return this;
    }
    
    DescriptorBuilder withoutRequiredProvider(Class<?> providerClass) {
        return withoutRequiredProvider(DescriptorFormat.transformClassName(providerClass));
    }
    
    DescriptorBuilder mapRequiredProvider(String provider, IntUnaryOperator mapper) {
        Integer current = this.requiredProviders.get(provider);
        if (current == null) {
            throw new IllegalArgumentException("Required provider not present: " + provider);
        }
        this.requiredProviders.put(provider, mapper.applyAsInt(current));
        return this;
    }
    
    DescriptorBuilder mapRequiredProvider(Class<?> providerClass, IntUnaryOperator mapper) {
        return mapRequiredProvider(DescriptorFormat.transformClassName(providerClass), mapper);
    }
    
    SourcedDescriptor build() {
        return new SourcedDescriptor(
            source,
            new Descriptor(
                className,
                kind,
                Map.copyOf(contracts),
                Map.copyOf(requiredProviders)
            )
        );
    }
}