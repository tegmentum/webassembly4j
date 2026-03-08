/*
 * Copyright 2025 Tegmentum AI
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
package ai.tegmentum.webassembly4j.bindgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import javax.lang.model.element.Modifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link GeneratedSource}. */
@DisplayName("GeneratedSource Tests")
class GeneratedSourceTest {

  private static final Logger LOGGER = Logger.getLogger(GeneratedSourceTest.class.getName());
  private static final String TEST_PACKAGE = "com.example.test";
  private static final String TEST_CLASS = "TestClass";

  /**
   * Creates a simple JavaFile for testing.
   *
   * @param packageName the package name
   * @param className the class name
   * @return a JavaFile instance
   */
  private static JavaFile createTestJavaFile(final String packageName, final String className) {
    TypeSpec typeSpec = TypeSpec.classBuilder(className).addModifiers(Modifier.PUBLIC).build();
    return JavaFile.builder(packageName, typeSpec).build();
  }

  @Nested
  @DisplayName("Constructor Tests")
  class ConstructorTests {

    @Test
    @DisplayName("should create GeneratedSource from valid JavaFile")
    void shouldCreateGeneratedSourceFromValidJavaFile() {
      LOGGER.info("Testing constructor with valid JavaFile");

      JavaFile javaFile = createTestJavaFile(TEST_PACKAGE, TEST_CLASS);
      GeneratedSource source = new GeneratedSource(javaFile);

      assertEquals(TEST_PACKAGE, source.getPackageName());
      assertEquals(TEST_CLASS, source.getClassName());
    }

    @Test
    @DisplayName("should throw NullPointerException when JavaFile is null")
    void shouldThrowWhenJavaFileIsNull() {
      LOGGER.info("Testing constructor with null JavaFile");

      NullPointerException exception =
          assertThrows(NullPointerException.class, () -> new GeneratedSource(null));
      assertTrue(
          exception.getMessage().contains("javaFile"), "Expected message to contain: javaFile");
    }

    @Test
    @DisplayName("should handle empty package name")
    void shouldHandleEmptyPackageName() {
      LOGGER.info("Testing constructor with empty package name");

      JavaFile javaFile = createTestJavaFile("", TEST_CLASS);
      GeneratedSource source = new GeneratedSource(javaFile);

      assertTrue(source.getPackageName().isEmpty());
      assertEquals(TEST_CLASS, source.getClassName());
    }
  }

  @Nested
  @DisplayName("Getter Tests")
  class GetterTests {

    @Test
    @DisplayName("getPackageName() should return the package name")
    void getPackageNameShouldReturnPackageName() {
      JavaFile javaFile = createTestJavaFile(TEST_PACKAGE, TEST_CLASS);
      GeneratedSource source = new GeneratedSource(javaFile);

      assertEquals(TEST_PACKAGE, source.getPackageName());
    }

    @Test
    @DisplayName("getClassName() should return the class name")
    void getClassNameShouldReturnClassName() {
      JavaFile javaFile = createTestJavaFile(TEST_PACKAGE, TEST_CLASS);
      GeneratedSource source = new GeneratedSource(javaFile);

      assertEquals(TEST_CLASS, source.getClassName());
    }

    @Test
    @DisplayName("getJavaFile() should return the underlying JavaFile")
    void getJavaFileShouldReturnUnderlyingJavaFile() {
      JavaFile javaFile = createTestJavaFile(TEST_PACKAGE, TEST_CLASS);
      GeneratedSource source = new GeneratedSource(javaFile);

      assertSame(javaFile, source.getJavaFile());
    }
  }

  @Nested
  @DisplayName("Qualified Name Tests")
  class QualifiedNameTests {

    @Test
    @DisplayName("should return fully qualified name with package")
    void shouldReturnFullyQualifiedNameWithPackage() {
      LOGGER.info("Testing getQualifiedName() with package");

      JavaFile javaFile = createTestJavaFile(TEST_PACKAGE, TEST_CLASS);
      GeneratedSource source = new GeneratedSource(javaFile);

      assertEquals("com.example.test.TestClass", source.getQualifiedName());
    }

    @Test
    @DisplayName("should return class name only when package is empty")
    void shouldReturnClassNameOnlyWhenPackageIsEmpty() {
      LOGGER.info("Testing getQualifiedName() without package");

      JavaFile javaFile = createTestJavaFile("", TEST_CLASS);
      GeneratedSource source = new GeneratedSource(javaFile);

      assertEquals(TEST_CLASS, source.getQualifiedName());
    }
  }

  @Nested
  @DisplayName("Relative Path Tests")
  class RelativePathTests {

    @Test
    @DisplayName("should return correct relative path with package")
    void shouldReturnCorrectRelativePathWithPackage() {
      LOGGER.info("Testing getRelativePath() with package");

      JavaFile javaFile = createTestJavaFile(TEST_PACKAGE, TEST_CLASS);
      GeneratedSource source = new GeneratedSource(javaFile);

      Path expectedPath = Path.of("com/example/test/TestClass.java");
      assertEquals(expectedPath, source.getRelativePath());
    }

    @Test
    @DisplayName("should return correct relative path without package")
    void shouldReturnCorrectRelativePathWithoutPackage() {
      LOGGER.info("Testing getRelativePath() without package");

      JavaFile javaFile = createTestJavaFile("", TEST_CLASS);
      GeneratedSource source = new GeneratedSource(javaFile);

      Path expectedPath = Path.of("TestClass.java");
      assertEquals(expectedPath, source.getRelativePath());
    }

    @Test
    @DisplayName("should handle nested package names")
    void shouldHandleNestedPackageNames() {
      JavaFile javaFile = createTestJavaFile("org.example.deeply.nested.pkg", "MyClass");
      GeneratedSource source = new GeneratedSource(javaFile);

      Path expectedPath = Path.of("org/example/deeply/nested/pkg/MyClass.java");
      assertEquals(expectedPath, source.getRelativePath());
    }
  }

  @Nested
  @DisplayName("Content Tests")
  class ContentTests {

    @Test
    @DisplayName("getContent() should return valid Java source code")
    void getContentShouldReturnValidJavaSourceCode() {
      LOGGER.info("Testing getContent()");

      JavaFile javaFile = createTestJavaFile(TEST_PACKAGE, TEST_CLASS);
      GeneratedSource source = new GeneratedSource(javaFile);

      String content = source.getContent();

      assertTrue(
          content.contains("package " + TEST_PACKAGE),
          "Expected content to contain: package " + TEST_PACKAGE);
      assertTrue(
          content.contains("public class " + TEST_CLASS),
          "Expected content to contain: public class " + TEST_CLASS);
    }
  }

  @Nested
  @DisplayName("Write Tests")
  class WriteTests {

    @Test
    @DisplayName("writeTo() should create file in correct directory structure")
    void writeToShouldCreateFileInCorrectDirectoryStructure(@TempDir Path tempDir)
        throws BindgenException {
      LOGGER.info("Testing writeTo() directory structure");

      JavaFile javaFile = createTestJavaFile(TEST_PACKAGE, TEST_CLASS);
      GeneratedSource source = new GeneratedSource(javaFile);

      source.writeTo(tempDir);

      Path expectedFile = tempDir.resolve("com/example/test/TestClass.java");
      assertTrue(Files.exists(expectedFile), "Expected file to exist: " + expectedFile);
      assertTrue(Files.isRegularFile(expectedFile), "Expected regular file: " + expectedFile);
    }

    @Test
    @DisplayName("writeTo() should write correct content")
    void writeToShouldWriteCorrectContent(@TempDir Path tempDir) throws Exception {
      LOGGER.info("Testing writeTo() content");

      JavaFile javaFile = createTestJavaFile(TEST_PACKAGE, TEST_CLASS);
      GeneratedSource source = new GeneratedSource(javaFile);

      source.writeTo(tempDir);

      Path expectedFile = tempDir.resolve("com/example/test/TestClass.java");
      String content = Files.readString(expectedFile);

      assertTrue(
          content.contains("package " + TEST_PACKAGE),
          "Expected content to contain: package " + TEST_PACKAGE);
      assertTrue(
          content.contains("public class " + TEST_CLASS),
          "Expected content to contain: public class " + TEST_CLASS);
    }

    @Test
    @DisplayName("writeTo() should throw NullPointerException when directory is null")
    void writeToShouldThrowWhenDirectoryIsNull() {
      JavaFile javaFile = createTestJavaFile(TEST_PACKAGE, TEST_CLASS);
      GeneratedSource source = new GeneratedSource(javaFile);

      NullPointerException exception =
          assertThrows(NullPointerException.class, () -> source.writeTo(null));
      assertTrue(
          exception.getMessage().contains("outputDirectory"),
          "Expected message to contain: outputDirectory");
    }

    @Test
    @DisplayName("writeToFile() should write to specific file path")
    void writeToFileShouldWriteToSpecificFilePath(@TempDir Path tempDir) throws Exception {
      LOGGER.info("Testing writeToFile()");

      JavaFile javaFile = createTestJavaFile(TEST_PACKAGE, TEST_CLASS);
      GeneratedSource source = new GeneratedSource(javaFile);

      Path targetFile = tempDir.resolve("custom/path/MySource.java");
      source.writeToFile(targetFile);

      assertTrue(Files.exists(targetFile), "Expected file to exist: " + targetFile);
      String content = Files.readString(targetFile);
      assertTrue(
          content.contains("public class " + TEST_CLASS),
          "Expected content to contain: public class " + TEST_CLASS);
    }

    @Test
    @DisplayName("writeToFile() should create parent directories")
    void writeToFileShouldCreateParentDirectories(@TempDir Path tempDir) throws Exception {
      LOGGER.info("Testing writeToFile() creates parent directories");

      JavaFile javaFile = createTestJavaFile(TEST_PACKAGE, TEST_CLASS);
      GeneratedSource source = new GeneratedSource(javaFile);

      Path targetFile = tempDir.resolve("a/b/c/d/e/Source.java");
      source.writeToFile(targetFile);

      assertTrue(Files.exists(targetFile), "Expected file to exist: " + targetFile);
      assertTrue(
          Files.isDirectory(targetFile.getParent()),
          "Expected parent to be a directory: " + targetFile.getParent());
    }

    @Test
    @DisplayName("writeToFile() should throw NullPointerException when path is null")
    void writeToFileShouldThrowWhenPathIsNull() {
      JavaFile javaFile = createTestJavaFile(TEST_PACKAGE, TEST_CLASS);
      GeneratedSource source = new GeneratedSource(javaFile);

      NullPointerException exception =
          assertThrows(NullPointerException.class, () -> source.writeToFile(null));
      assertTrue(
          exception.getMessage().contains("filePath"), "Expected message to contain: filePath");
    }
  }

  @Nested
  @DisplayName("Equals and HashCode Tests")
  class EqualsAndHashCodeTests {

    @Test
    @DisplayName("should be equal when packageName and className match")
    void shouldBeEqualWhenPackageNameAndClassNameMatch() {
      LOGGER.info("Testing equals() for matching sources");

      JavaFile javaFile1 = createTestJavaFile(TEST_PACKAGE, TEST_CLASS);
      JavaFile javaFile2 = createTestJavaFile(TEST_PACKAGE, TEST_CLASS);

      GeneratedSource source1 = new GeneratedSource(javaFile1);
      GeneratedSource source2 = new GeneratedSource(javaFile2);

      assertEquals(source2, source1);
      assertEquals(source2.hashCode(), source1.hashCode());
    }

    @Test
    @DisplayName("should not be equal when packageNames differ")
    void shouldNotBeEqualWhenPackageNamesDiffer() {
      LOGGER.info("Testing equals() for different package names");

      GeneratedSource source1 = new GeneratedSource(createTestJavaFile("pkg1", TEST_CLASS));
      GeneratedSource source2 = new GeneratedSource(createTestJavaFile("pkg2", TEST_CLASS));

      assertNotEquals(source2, source1);
    }

    @Test
    @DisplayName("should not be equal when classNames differ")
    void shouldNotBeEqualWhenClassNamesDiffer() {
      LOGGER.info("Testing equals() for different class names");

      GeneratedSource source1 = new GeneratedSource(createTestJavaFile(TEST_PACKAGE, "Class1"));
      GeneratedSource source2 = new GeneratedSource(createTestJavaFile(TEST_PACKAGE, "Class2"));

      assertNotEquals(source2, source1);
    }

    @Test
    @DisplayName("should not be equal to null")
    void shouldNotBeEqualToNull() {
      GeneratedSource source = new GeneratedSource(createTestJavaFile(TEST_PACKAGE, TEST_CLASS));

      assertNotEquals(null, source);
    }

    @Test
    @DisplayName("should not be equal to different class")
    void shouldNotBeEqualToDifferentClass() {
      GeneratedSource source = new GeneratedSource(createTestJavaFile(TEST_PACKAGE, TEST_CLASS));

      assertNotEquals(TEST_CLASS, source);
    }

    @Test
    @DisplayName("should be equal to itself")
    void shouldBeEqualToItself() {
      GeneratedSource source = new GeneratedSource(createTestJavaFile(TEST_PACKAGE, TEST_CLASS));

      assertEquals(source, source);
    }
  }

  @Nested
  @DisplayName("ToString Tests")
  class ToStringTests {

    @Test
    @DisplayName("should include qualified name in toString()")
    void shouldIncludeQualifiedNameInToString() {
      LOGGER.info("Testing toString() output");

      GeneratedSource source = new GeneratedSource(createTestJavaFile(TEST_PACKAGE, TEST_CLASS));

      String toString = source.toString();

      assertTrue(
          toString.contains("com.example.test.TestClass"),
          "Expected toString to contain: com.example.test.TestClass");
      assertTrue(
          toString.startsWith("GeneratedSource{"),
          "Expected toString to start with: GeneratedSource{");
      assertTrue(toString.endsWith("}"), "Expected toString to end with: }");
    }

    @Test
    @DisplayName("should handle class without package in toString()")
    void shouldHandleClassWithoutPackageInToString() {
      GeneratedSource source = new GeneratedSource(createTestJavaFile("", TEST_CLASS));

      String toString = source.toString();

      assertTrue(toString.contains(TEST_CLASS), "Expected toString to contain: " + TEST_CLASS);
      assertFalse(toString.contains("."), "Expected toString not to contain: .");
    }
  }
}
