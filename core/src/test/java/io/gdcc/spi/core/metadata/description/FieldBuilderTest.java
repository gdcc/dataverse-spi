package io.gdcc.spi.core.metadata.description;

import io.gdcc.spi.core.metadata.Namespace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FieldBuilderTest {
    
    private static final Namespace TEST_NS = Namespace.of("https://example.org/test/", "test");
    private static final Namespace OTHER_NS = Namespace.of("https://example.org/other/", "other");
    
    @Nested
    class SimpleFieldTests {
        
        @Test
        void buildSimpleField_withAllProperties() {
            Field field = Field.builder()
                .withId(TEST_NS, "title")
                .withType(FieldType.STRING)
                .withDisplayName("Title")
                .withWatermark("Enter a title...")
                .build();
            
            assertEquals(FieldId.of(TEST_NS, "title"), field.getId());
            assertEquals(FieldType.STRING, field.getType());
            assertEquals(0, field.getGeneration());
            assertEquals("Title", field.getDisplayName().orElseThrow());
            assertEquals("Enter a title...", field.getWatermark().orElseThrow());
            assertFalse(field.isCompound());
            assertFalse(field.hasChildren());
            assertTrue(field.isRoot());
            assertTrue(field.getChildren().isEmpty());
        }
        
        @Test
        void buildSimpleField_withMinimalProperties() {
            Field field = Field.builder()
                .withId(TEST_NS, "name")
                .withType(FieldType.TEXT)
                .build();
            
            assertEquals(FieldId.of(TEST_NS, "name"), field.getId());
            assertEquals(FieldType.TEXT, field.getType());
            assertTrue(field.getDisplayName().isEmpty());
            assertTrue(field.getWatermark().isEmpty());
            assertFalse(field.hasChildren());
        }
        
        @Test
        void buildSimpleField_usingFieldIdDirectly() {
            FieldId id = FieldId.of(TEST_NS, "email");
            Field field = Field.builder()
                .withId(id)
                .withType(FieldType.EMAIL)
                .build();
            
            assertEquals(id, field.getId());
            assertEquals(FieldType.EMAIL, field.getType());
        }
    }
    
    @Nested
    class CompoundFieldTests {
        
        @Test
        void buildCompoundField_withSingleChild() {
            var nameBuilder = Field.builder()
                .withId(TEST_NS, "name")
                .withType(FieldType.STRING);
            
            Field author = Field.builder()
                .withId(TEST_NS, "author")
                .withType(FieldType.COMPOUND)
                .addChild(nameBuilder)
                .build();
            
            assertTrue(author.isCompound());
            assertTrue(author.hasChildren());
            assertEquals(1, author.getChildren().size());
            assertEquals(0, author.getGeneration());
            
            Field nameField = author.getChildren().iterator().next();
            assertEquals(FieldId.of(TEST_NS, "name"), nameField.getId());
            assertEquals(1, nameField.getGeneration());
        }
        
        @Test
        void buildCompoundField_withMultipleChildren_usingVarargs() {
            var nameBuilder = Field.builder()
                .withId(TEST_NS, "name")
                .withType(FieldType.STRING);
            
            var emailBuilder = Field.builder()
                .withId(TEST_NS, "email")
                .withType(FieldType.EMAIL);
            
            Field author = Field.builder()
                .withId(TEST_NS, "author")
                .withType(FieldType.COMPOUND)
                .withChildren(nameBuilder, emailBuilder)
                .build();
            
            assertEquals(2, author.getChildren().size());
            assertTrue(author.getChildren().stream()
                .allMatch(child -> child.getGeneration() == 1));
        }
        
        @Test
        void buildCompoundField_withMultipleChildren_usingSet() {
            var nameBuilder = Field.builder()
                .withId(TEST_NS, "name")
                .withType(FieldType.STRING);
            
            var emailBuilder = Field.builder()
                .withId(TEST_NS, "email")
                .withType(FieldType.EMAIL);
            
            Field author = Field.builder()
                .withId(TEST_NS, "author")
                .withType(FieldType.COMPOUND)
                .withChildren(Set.of(nameBuilder, emailBuilder))
                .build();
            
            assertEquals(2, author.getChildren().size());
        }
        
        @Test
        void buildNestedCompound_generationsIncrement() {
            var cityBuilder = Field.builder()
                .withId(TEST_NS, "city")
                .withType(FieldType.STRING);
            
            var countryBuilder = Field.builder()
                .withId(TEST_NS, "country")
                .withType(FieldType.STRING);
            
            var addressBuilder = Field.builder()
                .withId(TEST_NS, "address")
                .withType(FieldType.COMPOUND)
                .withChildren(cityBuilder, countryBuilder);
            
            var nameBuilder = Field.builder()
                .withId(TEST_NS, "name")
                .withType(FieldType.STRING);
            
            Field author = Field.builder()
                .withId(TEST_NS, "author")
                .withType(FieldType.COMPOUND)
                .withChildren(nameBuilder, addressBuilder)
                .build();
            
            // Root level
            assertEquals(0, author.getGeneration());
            
            // First level children
            Field nameField = author.getChildren().stream()
                .filter(f -> f.getId().getLocalName().equals("name"))
                .findFirst().orElseThrow();
            assertEquals(1, nameField.getGeneration());
            
            Field addressField = author.getChildren().stream()
                .filter(f -> f.getId().getLocalName().equals("address"))
                .findFirst().orElseThrow();
            assertEquals(1, addressField.getGeneration());
            
            // Second level children (nested in address)
            assertTrue(addressField.hasChildren());
            assertEquals(2, addressField.getChildren().size());
            assertTrue(addressField.getChildren().stream()
                .allMatch(child -> child.getGeneration() == 2));
        }
        
        @Test
        void buildCompoundField_withMixedNamespaces() {
            var testField = Field.builder()
                .withId(TEST_NS, "localField")
                .withType(FieldType.STRING);
            
            var otherField = Field.builder()
                .withId(OTHER_NS, "externalField")
                .withType(FieldType.STRING);
            
            Field compound = Field.builder()
                .withId(TEST_NS, "mixed")
                .withType(FieldType.COMPOUND)
                .withChildren(testField, otherField)
                .build();
            
            assertEquals(2, compound.getChildren().size());
            assertTrue(compound.getChildren().stream()
                .anyMatch(f -> f.getId().getNamespace().equals(TEST_NS)));
            assertTrue(compound.getChildren().stream()
                .anyMatch(f -> f.getId().getNamespace().equals(OTHER_NS)));
        }
    }
    
    @Nested
    class BuilderStateTests {
        
        @Test
        void gettersReturnCorrectState() {
            Field.Builder builder = Field.builder()
                .withId(TEST_NS, "test")
                .withType(FieldType.STRING)
                .withDisplayName("Test")
                .withWatermark("test watermark");
            
            assertTrue(builder.getId().isPresent());
            assertEquals(FieldId.of(TEST_NS, "test"), builder.getId().get());
            assertTrue(builder.getType().isPresent());
            assertEquals(FieldType.STRING, builder.getType().get());
            assertTrue(builder.getDisplayName().isPresent());
            assertTrue(builder.getWatermark().isPresent());
            assertFalse(builder.isConsumed());
        }
        
        @Test
        void gettersReturnEmptyForUnsetProperties() {
            Field.Builder builder = Field.builder();
            
            assertTrue(builder.getId().isEmpty());
            assertTrue(builder.getType().isEmpty());
            assertTrue(builder.getDisplayName().isEmpty());
            assertTrue(builder.getWatermark().isEmpty());
            assertTrue(builder.getChildren().isEmpty());
            assertFalse(builder.isConsumed());
        }
        
        @Test
        void getChildren_returnsDefensiveCopy() {
            var child = Field.builder()
                .withId(TEST_NS, "child")
                .withType(FieldType.STRING);
            
            Field.Builder builder = Field.builder()
                .addChild(child);
            
            Set<Field.Builder> children1 = builder.getChildren();
            Set<Field.Builder> children2 = builder.getChildren();
            
            assertNotSame(children1, children2);
            assertEquals(children1, children2);
        }
    }
    
    @Nested
    class ChildManagementTests {
        
        @Test
        void addChild_buildsSet() {
            var child1 = Field.builder()
                .withId(TEST_NS, "child1")
                .withType(FieldType.STRING);
            
            var child2 = Field.builder()
                .withId(TEST_NS, "child2")
                .withType(FieldType.STRING);
            
            Field.Builder builder = Field.builder()
                .withId(TEST_NS, "parent")
                .withType(FieldType.COMPOUND)
                .addChild(child1)
                .addChild(child2);
            
            assertEquals(2, builder.getChildren().size());
        }
        
        @Test
        void withChildren_replacesExistingChildren() {
            var oldChild = Field.builder()
                .withId(TEST_NS, "old")
                .withType(FieldType.STRING);
            
            var newChild = Field.builder()
                .withId(TEST_NS, "new")
                .withType(FieldType.STRING);
            
            Field.Builder builder = Field.builder()
                .addChild(oldChild)
                .withChildren(newChild);
            
            assertEquals(1, builder.getChildren().size());
            assertEquals(newChild, builder.getChildren().iterator().next());
        }
        
        @Test
        void withChildren_null_clearsChildren() {
            var child = Field.builder()
                .withId(TEST_NS, "child")
                .withType(FieldType.STRING);
            
            Field.Builder builder = Field.builder()
                .addChild(child)
                .withChildren((Set<Field.Builder>) null);
            
            assertTrue(builder.getChildren().isEmpty());
        }
    }
    
    @Nested
    class ValidationTests {
        
        @Test
        void build_failsWithoutId() {
            Field.Builder builder = Field.builder()
                .withType(FieldType.STRING);
            
            assertThrows(NullPointerException.class, builder::build);
        }
        
        @Test
        void build_failsWithoutType() {
            Field.Builder builder = Field.builder()
                .withId(TEST_NS, "test");
            
            assertThrows(NullPointerException.class, builder::build);
        }
        
        @Test
        void build_compoundWithoutChildren_fails() {
            Field.Builder builder = Field.builder()
                .withId(TEST_NS, "compound")
                .withType(FieldType.COMPOUND);
            
            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                builder::build
            );
            assertTrue(ex.getMessage().contains("must have children"));
        }
        
        @Test
        void build_nonCompoundWithChildren_fails() {
            var child = Field.builder()
                .withId(TEST_NS, "child")
                .withType(FieldType.STRING);
            
            Field.Builder builder = Field.builder()
                .withId(TEST_NS, "text")
                .withType(FieldType.TEXT)
                .addChild(child);
            
            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                builder::build
            );
            assertTrue(ex.getMessage().contains("cannot have children"));
        }
    }
    
    @Nested
    class ConsumptionTests {
        
        @Test
        void build_marksBuilderAsConsumed() {
            Field.Builder builder = Field.builder()
                .withId(TEST_NS, "test")
                .withType(FieldType.STRING);
            
            assertFalse(builder.isConsumed());
            builder.build();
            assertTrue(builder.isConsumed());
        }
        
        @Test
        void build_cannotBuildTwice() {
            Field.Builder builder = Field.builder()
                .withId(TEST_NS, "test")
                .withType(FieldType.STRING);
            
            builder.build();
            
            IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                builder::build
            );
            assertTrue(ex.getMessage().contains("already been used"));
        }
        
        @Test
        void build_childConsumption_preventsReuseInAnotherParent() {
            var child = Field.builder()
                .withId(TEST_NS, "shared")
                .withType(FieldType.STRING);
            
            Field.builder()
                .withId(TEST_NS, "parent1")
                .withType(FieldType.COMPOUND)
                .addChild(child)
                .build();
            
            // Child is now consumed
            Field.Builder parent2 = Field.builder()
                .withId(TEST_NS, "parent2")
                .withType(FieldType.COMPOUND)
                .addChild(child);
            
            assertThrows(IllegalStateException.class, parent2::build);
        }
        
        @Test
        void build_childConsumption_preventsDirectBuild() {
            var child = Field.builder()
                .withId(TEST_NS, "child")
                .withType(FieldType.STRING);
            
            Field.builder()
                .withId(TEST_NS, "parent")
                .withType(FieldType.COMPOUND)
                .addChild(child)
                .build();
            
            // Child was consumed by parent
            assertThrows(IllegalStateException.class, child::build);
        }
    }
    
    @Nested
    class ImmutabilityTests {
        
        @Test
        void builtField_childrenAreImmutable() {
            var child = Field.builder()
                .withId(TEST_NS, "child")
                .withType(FieldType.STRING);
            
            Field parent = Field.builder()
                .withId(TEST_NS, "parent")
                .withType(FieldType.COMPOUND)
                .addChild(child)
                .build();
            
            assertThrows(UnsupportedOperationException.class, () ->
                parent.getChildren().clear()
            );
        }
        
        @Test
        void modifyingBuilderAfterBuild_doesNotAffectField() {
            Field.Builder builder = Field.builder()
                .withId(TEST_NS, "test")
                .withType(FieldType.STRING)
                .withDisplayName("Original");
            
            Field field = builder.build();
            
            // Can't modify after consumption anyway, but test the concept
            assertEquals("Original", field.getDisplayName().orElseThrow());
        }
    }
    
    @Nested
    class EdgeCaseTests {
        
        @Test
        void build_withNullDisplayName_isEmpty() {
            Field field = Field.builder()
                .withId(TEST_NS, "test")
                .withType(FieldType.STRING)
                .withDisplayName(null)
                .build();
            
            assertTrue(field.getDisplayName().isEmpty());
        }
        
        @Test
        void build_withNullWatermark_isEmpty() {
            Field field = Field.builder()
                .withId(TEST_NS, "test")
                .withType(FieldType.STRING)
                .withWatermark(null)
                .build();
            
            assertTrue(field.getWatermark().isEmpty());
        }
        
        @Test
        void build_allFieldTypes() {
            for (FieldType type : FieldType.values()) {
                if (type == FieldType.COMPOUND) {
                    continue; // Skip compound, tested separately
                }
                
                Field field = Field.builder()
                    .withId(TEST_NS, type.name().toLowerCase())
                    .withType(type)
                    .build();
                
                assertEquals(type, field.getType());
                assertFalse(field.isCompound());
            }
        }
    }
}