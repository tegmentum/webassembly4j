package ai.tegmentum.webassembly4j.api.gc;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

class GcExtensionReleaseTest {

    @Test
    void defaultReleaseReturnsFalse() {
        GcExtension ext = new MinimalGcExtension();
        GcObject obj = new GcObject() {
            @Override
            public GcReferenceType referenceType() {
                return GcReferenceType.STRUCT_REF;
            }

            @Override
            public boolean isNull() {
                return false;
            }

            @Override
            public boolean refEquals(GcObject other) {
                return this == other;
            }
        };
        assertFalse(ext.release(obj));
    }

    private static class MinimalGcExtension implements GcExtension {
        @Override
        public GcStructInstance createStruct(GcStructType type, GcValue... values) {
            throw new UnsupportedOperationException();
        }

        @Override
        public GcStructInstance createStruct(GcStructType type, List<GcValue> values) {
            throw new UnsupportedOperationException();
        }

        @Override
        public GcArrayInstance createArray(GcArrayType type, GcValue... elements) {
            throw new UnsupportedOperationException();
        }

        @Override
        public GcArrayInstance createArray(GcArrayType type, List<GcValue> elements) {
            throw new UnsupportedOperationException();
        }

        @Override
        public GcArrayInstance createArray(GcArrayType type, int length) {
            throw new UnsupportedOperationException();
        }

        @Override
        public GcI31Instance createI31(int value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean refTest(GcObject object, GcReferenceType refType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public GcObject refCast(GcObject object, GcReferenceType refType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public GcStats collectGarbage() {
            throw new UnsupportedOperationException();
        }

        @Override
        public GcStats getStats() {
            throw new UnsupportedOperationException();
        }
    }
}
