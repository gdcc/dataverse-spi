package io.gdcc.spi.core.loader;

import io.gdcc.spi.core.test.basic.TestContract;
import io.gdcc.spi.meta.annotations.PluginContract;
import io.gdcc.spi.meta.descriptor.SourcedDescriptor;
import io.gdcc.spi.meta.plugin.CoreProvider;
import io.gdcc.spi.meta.plugin.Plugin;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import static io.gdcc.spi.meta.descriptor.DescriptorFormat.transformClassName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoaderHelperTest {
    
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    
    @Nested
    class DetermineApiLevel {
        
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        
        @Test
        void determineCoreApiLevel_validClass() {
            assertEquals(TestContract.API_LEVEL, LoaderHelper.determineCoreApiLevel(TestContract.class));
        }
        
        @Test
        void determineCoreApiLevel_validFQCN() {
            assertEquals(
                TestContract.API_LEVEL,
                LoaderHelper.determineCoreApiLevel(transformClassName(TestContract.class), classLoader)
            );
        }
        
        @Test
        void determineCoreApiLevel_invalidFQCN() {
            assertThrows(
                IllegalArgumentException.class,
                () -> LoaderHelper.determineCoreApiLevel("foo.Bar", classLoader)
            );
        }
    }
    
    @Nested
    class NoClassCollisions {
        
        @Test
        void noCollisions_happyPath() {
            // given
            List<SourcedDescriptor> descriptors = List.of(
                DescriptorBuilder.aDescriptor().withClassPackage("com.example").build(),
                DescriptorBuilder.aDescriptor().withClassPackage("com.foobar").build()
            );
            
            // when
            var results = LoaderHelper.verifyNoClassCollisions(descriptors, classLoader);
            
            System.out.println(results);
            
            // then
            assertEquals(2, results.accepted().size());
            assertEquals(0, results.warning().size());
            assertEquals(0, results.rejected().size());
        }
        
        @Test
        void noCollisions_pluginClassesCollide() {
            // given
            // Same FQCN (com.foobar.TestPlugin), but from different sources.
            List<SourcedDescriptor> descriptors = List.of(
                DescriptorBuilder.aDescriptor()
                    .withSource("foobar.jar")
                    .withClassPackage("com.foobar")
                    .build(),
                DescriptorBuilder.aDescriptor()
                    .withSource("example.jar")
                    .withClassPackage("com.foobar")
                    .build()
            );
            
            // when
            var results = LoaderHelper.verifyNoClassCollisions(descriptors, classLoader);
            
            // then
            assertEquals(1, results.accepted().size());
            assertEquals(0, results.warning().size());
            assertEquals(1, results.rejected().size());
            for (Map.Entry<SourcedDescriptor, List<LoaderProblem>> result : results.rejected().entrySet()) {
                List<LoaderProblem> problems = result.getValue();
                assertTrue(problems.stream().allMatch(
                    problem -> problem.getClass().equals(LoaderProblem.PluginClassNameCollision.class)
                ));
            }
        }
        
        @Test
        void noCollisions_pluginClassesCollideWithCore() {
            // given
            // Same FQCN as TestPlugin, but from different source
            List<SourcedDescriptor> descriptors = List.of(
                DescriptorBuilder.aDescriptor().withSource("foobar.jar").build()
            );
            
            // when
            var results = LoaderHelper.verifyNoClassCollisions(descriptors, classLoader);
            
            // then
            assertEquals(0, results.accepted().size());
            assertEquals(0, results.warning().size());
            assertEquals(1, results.rejected().size());
            for (Map.Entry<SourcedDescriptor, List<LoaderProblem>> result : results.rejected().entrySet()) {
                List<LoaderProblem> problems = result.getValue();
                assertTrue(problems.stream().allMatch(
                    problem -> problem.getClass().equals(LoaderProblem.PluginClassNameCollisionWithCore.class)
                ));
            }
        }
    }
    
    @Nested
    class IdentifyNonImplementations {
        
        @Test
        void identifyNonImplementations_matchingDescriptorIsAccepted() {
            // given
            List<SourcedDescriptor> descriptors = List.of(
                DescriptorBuilder.aDescriptor().build()
            );
            
            // when
            var results = LoaderHelper.identifyNonImplementations(
                descriptors,
                TestContract.class,
                enforcingConfiguration()
            );
            
            // then
            assertEquals(1, results.accepted().size());
            assertEquals(0, results.warning().size());
            assertEquals(0, results.rejected().size());
        }
        
        @Test
        void identifyNonImplementations_nonMatchingDescriptorIsRejectedWhenEnforced() {
            // given
            SourcedDescriptor descriptor = DescriptorBuilder.aDescriptor()
                .withKind("com.example.OtherContract")
                .build();
            
            // when
            var results = LoaderHelper.identifyNonImplementations(
                List.of(descriptor),
                TestContract.class,
                enforcingConfiguration()
            );
            
            // then
            assertEquals(0, results.accepted().size());
            assertEquals(0, results.warning().size());
            assertEquals(1, results.rejected().size());
            
            List<LoaderProblem> problems = results.rejected().get(descriptor);
            assertEquals(1, problems.size());
            assertInstanceOf(LoaderProblem.PluginClassMismatch.class, problems.get(0));
        }
        
        @Test
        void identifyNonImplementations_nonMatchingDescriptorIsWarningWhenNotEnforced() {
            // given
            SourcedDescriptor descriptor = DescriptorBuilder.aDescriptor()
                .withKind("com.example.OtherContract")
                .build();
            
            // when
            var results = LoaderHelper.identifyNonImplementations(
                List.of(descriptor),
                TestContract.class,
                permissiveConfiguration()
            );
            
            // then
            assertEquals(0, results.accepted().size());
            assertEquals(1, results.warning().size());
            assertEquals(0, results.rejected().size());
            
            List<LoaderProblem> problems = results.warning().get(descriptor);
            assertEquals(1, problems.size());
            assertInstanceOf(LoaderProblem.PluginClassMismatch.class, problems.get(0));
        }
        
        @Test
        void identifyNonImplementations_mixedDescriptorsSeparatesAcceptedAndRejected() {
            // given
            SourcedDescriptor matching = DescriptorBuilder.aDescriptor()
                .withSource("matching.jar")
                .build();
            SourcedDescriptor nonMatching = DescriptorBuilder.aDescriptor()
                .withSource("non-matching.jar")
                .withKind("com.example.OtherContract")
                .build();
            
            // when
            var results = LoaderHelper.identifyNonImplementations(
                List.of(matching, nonMatching),
                TestContract.class,
                enforcingConfiguration()
            );
            
            // then
            assertEquals(1, results.accepted().size());
            assertTrue(results.accepted().contains(matching));
            assertEquals(0, results.warning().size());
            assertEquals(1, results.rejected().size());
            assertTrue(results.rejected().containsKey(nonMatching));
        }
        
        @Test
        void identifyNonImplementations_mixedDescriptorsSeparatesAcceptedAndWarnings() {
            // given
            SourcedDescriptor matching = DescriptorBuilder.aDescriptor()
                .withSource("matching.jar")
                .build();
            SourcedDescriptor nonMatching = DescriptorBuilder.aDescriptor()
                .withSource("non-matching.jar")
                .withKind("com.example.OtherContract")
                .build();
            
            // when
            var results = LoaderHelper.identifyNonImplementations(
                List.of(matching, nonMatching),
                TestContract.class,
                permissiveConfiguration()
            );
            
            // then
            assertEquals(1, results.accepted().size());
            assertTrue(results.accepted().contains(matching));
            assertEquals(1, results.warning().size());
            assertTrue(results.warning().containsKey(nonMatching));
            assertEquals(0, results.rejected().size());
        }
    }
    
    @Nested
    class VerifyServiceProviderRecords {
        
        @TempDir
        Path tempDir;
        
        @Test
        void verifyServiceProviderRecords_directorySourceWithMatchingRecordIsAccepted() throws Exception {
            // given
            SourcedDescriptor descriptor = DescriptorBuilder.aDescriptor()
                .withSource(tempDir.toString())
                .build();
            
            Path spiFile = createSpiFile(
                tempDir,
                descriptor.plugin().kind(),
                descriptor.plugin().klass()
            );
            
            // when
            var results = LoaderHelper.verifyServiceProviderRecords(List.of(descriptor));
            
            // then
            assertTrue(Files.isRegularFile(spiFile));
            assertEquals(1, results.accepted().size());
            assertTrue(results.accepted().contains(descriptor));
            assertEquals(0, results.warning().size());
            assertEquals(0, results.rejected().size());
        }
        
        @Test
        void verifyServiceProviderRecords_jarSourceWithMatchingRecordIsAccepted() throws Exception {
            // given
            Path jarPath = tempDir.resolve("plugin.jar");
            SourcedDescriptor descriptor = DescriptorBuilder.aDescriptor()
                .withSource(jarPath.toString())
                .build();
            
            createJarWithSpiRecord(
                jarPath,
                descriptor.plugin().kind(),
                descriptor.plugin().klass()
            );
            
            // when
            var results = LoaderHelper.verifyServiceProviderRecords(List.of(descriptor));
            
            // then
            assertEquals(1, results.accepted().size());
            assertTrue(results.accepted().contains(descriptor));
            assertEquals(0, results.warning().size());
            assertEquals(0, results.rejected().size());
        }
        
        @Test
        void verifyServiceProviderRecords_missingRecordIsRejected() {
            // given
            SourcedDescriptor descriptor = DescriptorBuilder.aDescriptor()
                .withSource(tempDir.toString())
                .build();
            
            // when
            var results = LoaderHelper.verifyServiceProviderRecords(List.of(descriptor));
            
            // then
            assertEquals(0, results.accepted().size());
            assertEquals(0, results.warning().size());
            assertEquals(1, results.rejected().size());
            
            List<LoaderProblem> problems = results.rejected().get(descriptor);
            assertEquals(1, problems.size());
            assertInstanceOf(LoaderProblem.MissingServiceProviderRecord.class, problems.get(0));
        }
        
        @Test
        void verifyServiceProviderRecords_missingSourceIsRejectedAsLocationFailure() {
            // given
            SourcedDescriptor descriptor = DescriptorBuilder.aDescriptor()
                .withSource(tempDir.resolve("missing-plugin.jar").toString())
                .build();
            
            // when
            var results = LoaderHelper.verifyServiceProviderRecords(List.of(descriptor));
            
            // then
            assertEquals(0, results.accepted().size());
            assertEquals(0, results.warning().size());
            assertEquals(1, results.rejected().size());
            
            List<LoaderProblem> problems = results.rejected().get(descriptor);
            assertEquals(1, problems.size());
            assertInstanceOf(LoaderProblem.LocationFailure.class, problems.get(0));
        }
        
        @Test
        void verifyServiceProviderRecords_recordWithWhitespaceAndCommentsIsAccepted() throws Exception {
            // given
            SourcedDescriptor descriptor = DescriptorBuilder.aDescriptor()
                .withSource(tempDir.toString())
                .build();
            
            createSpiFile(
                tempDir,
                descriptor.plugin().kind(),
                """
                # service registrations
                   %s    # primary implementation

                com.example.OtherImplementation
                """.formatted(descriptor.plugin().klass())
            );
            
            // when
            var results = LoaderHelper.verifyServiceProviderRecords(List.of(descriptor));
            
            // then
            assertEquals(1, results.accepted().size());
            assertTrue(results.accepted().contains(descriptor));
            assertEquals(0, results.warning().size());
            assertEquals(0, results.rejected().size());
        }
        
        private Path createSpiFile(Path root, String kind, String content) throws Exception {
            Path serviceFile = root.resolve("META-INF").resolve("services").resolve(kind);
            Files.createDirectories(serviceFile.getParent());
            Files.writeString(serviceFile, content, StandardCharsets.UTF_8);
            return serviceFile;
        }
        
        private void createJarWithSpiRecord(Path jarPath, String kind, String content) throws Exception {
            Path parent = jarPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            
            try (
                OutputStream fileOut = Files.newOutputStream(jarPath);
                JarOutputStream jarOut = new JarOutputStream(fileOut)
            ) {
                String entryName = "META-INF/services/" + kind;
                jarOut.putNextEntry(new ZipEntry(entryName));
                jarOut.write(content.getBytes(StandardCharsets.UTF_8));
                jarOut.closeEntry();
            }
        }
    }
    
    @Nested
    class VerifyApiLevels {
        
        @Test
        void verifyApiLevels_happyPath() {
            // given
            SourcedDescriptor descriptor = DescriptorBuilder.aDescriptor().build();
            
            // when
            var results = LoaderHelper.verifyPluginApiLevels(List.of(descriptor), TestContract.class, classLoader);
            
            // then
            assertEquals(1, results.accepted().size());
            assertTrue(results.accepted().contains(descriptor));
            assertEquals(0, results.warning().size());
            assertEquals(0, results.rejected().size());
        }
        
        @Test
        void verifyApiLevels_baseContractLevelMismatch() {
            // given
            SourcedDescriptor descriptor = DescriptorBuilder.aDescriptor()
                .mapContract(TestContract.class, level -> level + 1)
                .build();
            
            // when
            var results = LoaderHelper.verifyPluginApiLevels(List.of(descriptor), TestContract.class, classLoader);
            
            // then
            assertEquals(0, results.accepted().size());
            assertEquals(0, results.warning().size());
            assertEquals(1, results.rejected().size());
            assertTrue(results.rejected().containsKey(descriptor));
            assertInstanceOf(LoaderProblem.PluginClassApiLevelMismatch.class, results.rejected().get(descriptor).get(0));
        }
        
        @Test
        void verifyApiLevels_missingBaseContract() {
            // given
            SourcedDescriptor descriptor = DescriptorBuilder.aDescriptor()
                .withoutContract(TestContract.class)
                .build();
            
            // when
            var results = LoaderHelper.verifyPluginApiLevels(List.of(descriptor), TestContract.class, classLoader);
            
            // then
            assertEquals(0, results.accepted().size());
            assertEquals(0, results.warning().size());
            assertEquals(1, results.rejected().size());
            assertTrue(results.rejected().containsKey(descriptor));
            assertInstanceOf(LoaderProblem.PluginClassApiLevelMissing.class, results.rejected().get(descriptor).get(0));
        }
        
        @Test
        void verifyApiLevels_unsupportedContract() {
            // given
            SourcedDescriptor descriptor = DescriptorBuilder.aDescriptor()
                .withContract("com.example.UnsupportedContract", 7)
                .build();
            
            // when
            var results = LoaderHelper.verifyPluginApiLevels(List.of(descriptor), TestContract.class, classLoader);
            
            // then
            assertEquals(0, results.accepted().size());
            assertEquals(0, results.warning().size());
            assertEquals(1, results.rejected().size());
            assertTrue(results.rejected().containsKey(descriptor));
            assertInstanceOf(LoaderProblem.PluginClassUnsupported.class, results.rejected().get(descriptor).get(0));
        }
        
        @Test
        void verifyApiLevels_reportsMultipleProblemsForSingleDescriptor() {
            // given
            SourcedDescriptor descriptor = DescriptorBuilder.aDescriptor()
                .withoutContract(TestContract.class)
                .withContract("com.example.UnsupportedContract", 7)
                .build();
            
            // when
            var results = LoaderHelper.verifyPluginApiLevels(List.of(descriptor), TestContract.class, classLoader);
            
            // then
            assertEquals(0, results.accepted().size());
            assertEquals(0, results.warning().size());
            assertEquals(1, results.rejected().size());
            assertTrue(results.rejected().containsKey(descriptor));
            
            List<LoaderProblem> problems = results.rejected().get(descriptor);
            assertEquals(2, problems.size());
            assertTrue(problems.stream().anyMatch(
                problem -> problem.getClass().equals(LoaderProblem.PluginClassUnsupported.class)
            ));
            assertTrue(problems.stream().anyMatch(
                problem -> problem.getClass().equals(LoaderProblem.PluginClassApiLevelMissing.class)
            ));
        }
    }
    
    @Nested
    class ToPluginDescriptor {
        
        interface TestProvider extends CoreProvider {
            int API_LEVEL = 7;
        }
        
        @PluginContract(role = PluginContract.Role.BASE)
        interface TestBasePlugin extends Plugin {
            int API_LEVEL = 3;
        }
        
        @PluginContract(
            role = PluginContract.Role.CAPABILITY,
            requires = { TestBasePlugin.class }
        )
        interface TestCapabilityPlugin extends Plugin {
            int API_LEVEL = 5;
        }
        
        static class GoodPlugin implements TestBasePlugin, TestCapabilityPlugin {
            @Override
            public String identity() {
                return "good-plugin";
            }
        }
        
        static class NullIdentityPlugin implements TestBasePlugin {
            @Override
            public String identity() {
                return null;
            }
        }
        
        static class BlankIdentityPlugin implements TestBasePlugin {
            @Override
            public String identity() {
                return "   ";
            }
        }
        
        @Test
        void toPluginDescriptor_happyPath() {
            // given
            SourcedDescriptor sourceDescriptor = DescriptorBuilder.aDescriptor()
                .withSource("plugins", "good-plugin.jar")
                .withClassName(GoodPlugin.class)
                .withKind(TestBasePlugin.class)
                .withContracts(Map.of(
                    transformClassName(TestBasePlugin.class), TestBasePlugin.API_LEVEL,
                    transformClassName(TestCapabilityPlugin.class), TestCapabilityPlugin.API_LEVEL
                ))
                .withRequiredProviders(Map.of(
                    transformClassName(TestProvider.class), TestProvider.API_LEVEL
                ))
                .build();
            GoodPlugin plugin = new GoodPlugin();
            
            // when
            var result = LoaderHelper.toPluginDescriptor(sourceDescriptor, plugin, classLoader);
            
            // then
            assertEquals(Path.of("plugins", "good-plugin.jar"), result.sourceLocation());
            assertEquals("good-plugin", result.identity());
            assertEquals(GoodPlugin.class, result.pluginClass());
            assertEquals(TestBasePlugin.class, result.kindClass());
            assertEquals(2, result.contracts().size());
            assertEquals(TestBasePlugin.API_LEVEL, result.contracts().get(TestBasePlugin.class));
            assertEquals(TestCapabilityPlugin.API_LEVEL, result.contracts().get(TestCapabilityPlugin.class));
            assertEquals(1, result.requiredProviders().size());
            assertEquals(TestProvider.API_LEVEL, result.requiredProviders().get(TestProvider.class));
        }
        
        @Test
        void toPluginDescriptor_rejectsNullIdentity() {
            // given
            SourcedDescriptor sourceDescriptor = DescriptorBuilder.aDescriptor()
                .withSource("plugins", "null-identity.jar")
                .withClassName(NullIdentityPlugin.class)
                .withKind(TestBasePlugin.class)
                .withContracts(Map.of(
                    transformClassName(TestBasePlugin.class), TestBasePlugin.API_LEVEL
                ))
                .build();
            NullIdentityPlugin plugin = new NullIdentityPlugin();
            
            // when + then
            var exception = assertThrows(
                IllegalArgumentException.class,
                () -> LoaderHelper.toPluginDescriptor(sourceDescriptor, plugin, classLoader)
            );
            assertTrue(exception.getMessage().contains("Plugin identity may not be"));
        }
        
        @Test
        void toPluginDescriptor_rejectsBlankIdentity() {
            // given
            SourcedDescriptor sourceDescriptor = DescriptorBuilder.aDescriptor()
                .withSource("plugins", "blank-identity.jar")
                .withClassName(BlankIdentityPlugin.class)
                .withKind(TestBasePlugin.class)
                .withContracts(Map.of(
                    transformClassName(TestBasePlugin.class), TestBasePlugin.API_LEVEL
                ))
                .build();
            BlankIdentityPlugin plugin = new BlankIdentityPlugin();
            
            // when + then
            var exception = assertThrows(
                IllegalArgumentException.class,
                () -> LoaderHelper.toPluginDescriptor(sourceDescriptor, plugin, classLoader)
            );
            assertTrue(exception.getMessage().contains("Plugin identity may not be"));
        }
        
        @Test
        void toPluginDescriptor_failsWhenContractClassCannotBeResolved() {
            // given
            SourcedDescriptor sourceDescriptor = DescriptorBuilder.aDescriptor()
                .withClassName(GoodPlugin.class)
                .withKind(TestBasePlugin.class)
                .withContracts(Map.of(
                    "com.example.DoesNotExist", 99
                ))
                .build();
            GoodPlugin plugin = new GoodPlugin();
            
            // when + then
            assertThrows(
                IllegalArgumentException.class,
                () -> LoaderHelper.toPluginDescriptor(sourceDescriptor, plugin, classLoader)
            );
        }
        
        @Test
        void toPluginDescriptor_failsWhenContractClassEmpty() {
            // given
            SourcedDescriptor sourceDescriptor = DescriptorBuilder.aDescriptor()
                .withClassName(GoodPlugin.class)
                .withKind(TestBasePlugin.class)
                .withContracts(Map.of())
                .withRequiredProviders(Map.of())
                .build();
            GoodPlugin plugin = new GoodPlugin();
            
            // when + then
            assertThrows(
                IllegalArgumentException.class,
                () -> LoaderHelper.toPluginDescriptor(sourceDescriptor, plugin, classLoader)
            );
        }
        
        @Test
        void toPluginDescriptor_failsWhenProviderClassCannotBeResolved() {
            // given
            SourcedDescriptor sourceDescriptor = DescriptorBuilder.aDescriptor()
                .withClassName(GoodPlugin.class)
                .withKind(TestBasePlugin.class)
                .withContracts(Map.of(
                    transformClassName(TestBasePlugin.class), TestBasePlugin.API_LEVEL
                ))
                .withRequiredProviders(Map.of(
                    "com.example.MissingProvider", 42
                ))
                .build();
            GoodPlugin plugin = new GoodPlugin();
            
            // when + then
            assertThrows(
                IllegalArgumentException.class,
                () -> LoaderHelper.toPluginDescriptor(sourceDescriptor, plugin, classLoader)
            );
        }
    }
    
    static LoaderConfiguration enforcingConfiguration() {
        return new LoaderConfiguration(
            true,
            false,
            true,
            true,
            true
        );
    }
    
    static LoaderConfiguration permissiveConfiguration() {
        return new LoaderConfiguration(
            false,
            false,
            true,
            true,
            false
        );
    }
}
