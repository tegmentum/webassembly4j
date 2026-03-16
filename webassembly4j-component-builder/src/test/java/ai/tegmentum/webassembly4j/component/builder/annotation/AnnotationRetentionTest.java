/*
 * Copyright 2025 Tegmentum AI. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ai.tegmentum.webassembly4j.component.builder.annotation;

import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AnnotationRetentionTest {

    @Test
    void allAnnotationsHaveRuntimeRetention() {
        assertRuntimeRetention(WitComponent.class);
        assertRuntimeRetention(WitWorld.class);
        assertRuntimeRetention(WitExport.class);
        assertRuntimeRetention(WitImport.class);
        assertRuntimeRetention(WitRecord.class);
        assertRuntimeRetention(WitEnum.class);
        assertRuntimeRetention(WitVariant.class);
        assertRuntimeRetention(WitFlags.class);
        assertRuntimeRetention(WitResource.class);
    }

    @Test
    void witComponentFieldsAccessible() {
        @WitComponent(packageName = "test:pkg", version = "1.0.0")
        class TestClass {}

        WitComponent annotation = TestClass.class.getAnnotation(WitComponent.class);
        assertNotNull(annotation);
        assertEquals("test:pkg", annotation.packageName());
        assertEquals("1.0.0", annotation.version());
    }

    @Test
    void witWorldFieldAccessible() {
        @WitWorld(name = "test-world")
        class TestClass {}

        WitWorld annotation = TestClass.class.getAnnotation(WitWorld.class);
        assertNotNull(annotation);
        assertEquals("test-world", annotation.name());
    }

    @Test
    void witComponentDefaults() {
        @WitComponent
        class TestClass {}

        WitComponent annotation = TestClass.class.getAnnotation(WitComponent.class);
        assertNotNull(annotation);
        assertEquals("", annotation.packageName());
        assertEquals("", annotation.version());
    }

    private void assertRuntimeRetention(Class<?> annotationClass) {
        Retention retention = annotationClass.getAnnotation(Retention.class);
        assertNotNull(retention, annotationClass.getSimpleName() + " must have @Retention");
        assertEquals(RetentionPolicy.RUNTIME, retention.value(),
                annotationClass.getSimpleName() + " must have RUNTIME retention");
    }
}
