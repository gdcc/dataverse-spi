package io.gdcc.spi.core.metadata.description;

import io.gdcc.spi.core.metadata.Namespace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

class FieldTest {
    
    private static final Namespace NS = Namespace.of("https://example.org/", "ex");
    
    @Nested
    class EqualityTests {
        
        @Test
        void equals_sameId_areEqual() {
            Field field1 = Field.builder()
                .withId(NS, "title")
                .withType(FieldType.STRING)
                .build();
            
            Field field2 = Field.builder()
                .withId(NS, "title")
                .withType(FieldType.STRING)
                .build();
            
            assertEquals(field1, field2);
            assertEquals(field1.hashCode(), field2.hashCode());
        }
        
        @Test
        void equals_sameId_differentGeneration_areEqual() {
            // Root field
            Field root = Field.builder()
                .withId(NS, "name")
                .withType(FieldType.STRING)
                .build();
            
            // Same ID but as child (generation 1)
            Field parent = Field.builder()
                .withId(NS, "parent")
                .withType(FieldType.COMPOUND)
                .addChild(Field.builder()
                    .withId(NS, "name")
                    .withType(FieldType.STRING))
                .build();
            
            Field child = parent.getChildren().iterator().next();
            
            // Different generations but same ID
            assertEquals(0, root.getGeneration());
            assertEquals(1, child.getGeneration());
            assertEquals(root, child);  // Still equal!
        }
        
        @Test
        void equals_sameId_differentType_areEqual() {
            Field field1 = Field.builder()
                .withId(NS, "value")
                .withType(FieldType.STRING)
                .build();
            
            Field field2 = Field.builder()
                .withId(NS, "value")
                .withType(FieldType.TEXT)
                .build();
            
            assertEquals(field1, field2);  // Only ID matters
        }
        
        @Test
        void equals_differentId_notEqual() {
            Field field1 = Field.builder()
                .withId(NS, "title")
                .withType(FieldType.STRING)
                .build();
            
            Field field2 = Field.builder()
                .withId(NS, "name")
                .withType(FieldType.STRING)
                .build();
            
            assertNotEquals(field1, field2);
        }
        
        @Test
        void equals_differentNamespace_notEqual() {
            Namespace ns1 = Namespace.of("https://example.org/", "ex1");
            Namespace ns2 = Namespace.of("https://other.org/", "ex2");
            
            Field field1 = Field.builder()
                .withId(ns1, "title")
                .withType(FieldType.STRING)
                .build();
            
            Field field2 = Field.builder()
                .withId(ns2, "title")
                .withType(FieldType.STRING)
                .build();
            
            assertNotEquals(field1, field2);
        }
        
        @Test
        void equals_reflexive() {
            Field field = Field.builder()
                .withId(NS, "test")
                .withType(FieldType.STRING)
                .build();
            
            assertEquals(field, field);
        }
        
        @Test
        void equals_null_returnsFalse() {
            Field field = Field.builder()
                .withId(NS, "test")
                .withType(FieldType.STRING)
                .build();
            
            assertNotEquals(field, null);
        }
        
        @Test
        void equals_differentClass_returnsFalse() {
            Field field = Field.builder()
                .withId(NS, "test")
                .withType(FieldType.STRING)
                .build();
            
            assertNotEquals(field, "not a field");
        }
    }
    
    @Nested
    class ChildrenTests {
        
        @Test
        void hasChildren_withChildren_returnsTrue() {
            Field parent = Field.builder()
                .withId(NS, "parent")
                .withType(FieldType.COMPOUND)
                .addChild(Field.builder()
                    .withId(NS, "child")
                    .withType(FieldType.STRING))
                .build();
            
            assertTrue(parent.hasChildren());
        }
        
        @Test
        void hasChildren_withoutChildren_returnsFalse() {
            Field field = Field.builder()
                .withId(NS, "simple")
                .withType(FieldType.STRING)
                .build();
            
            assertFalse(field.hasChildren());
        }
        
        @Test
        void getChildren_returnsImmutableSet() {
            Field parent = Field.builder()
                .withId(NS, "parent")
                .withType(FieldType.COMPOUND)
                .addChild(Field.builder()
                    .withId(NS, "child")
                    .withType(FieldType.STRING))
                .build();
            
            assertThrows(UnsupportedOperationException.class, () ->
                parent.getChildren().clear()
            );
        }
        
        @Test
        void getChildren_emptyForNonCompound_isImmutable() {
            Field field = Field.builder()
                .withId(NS, "simple")
                .withType(FieldType.STRING)
                .build();
            
            assertTrue(field.getChildren().isEmpty());
            assertThrows(UnsupportedOperationException.class, () ->
                field.getChildren().add(null)
            );
        }
    }
}