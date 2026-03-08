/*
 * Copyright 2024 Tegmentum AI. All rights reserved.
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

import com.squareup.javapoet.JavaFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Represents a generated Java source file.
 *
 * <p>This class encapsulates a generated Java source file and provides methods to write it to disk
 * or access its content.
 */
public final class GeneratedSource {

  private final JavaFile javaFile;
  private final String packageName;
  private final String className;

  /**
   * Creates a new GeneratedSource from a JavaPoet JavaFile.
   *
   * @param javaFile the generated JavaFile
   */
  public GeneratedSource(final JavaFile javaFile) {
    this.javaFile = Objects.requireNonNull(javaFile, "javaFile");
    this.packageName = javaFile.packageName;
    this.className = javaFile.typeSpec.name;
  }

  /**
   * Returns the package name of this generated source.
   *
   * @return the package name
   */
  public String getPackageName() {
    return packageName;
  }

  /**
   * Returns the class name of this generated source.
   *
   * @return the class name
   */
  public String getClassName() {
    return className;
  }

  /**
   * Returns the fully qualified name of this generated source.
   *
   * @return the fully qualified class name
   */
  public String getQualifiedName() {
    if (packageName.isEmpty()) {
      return className;
    }
    return packageName + "." + className;
  }

  /**
   * Returns the relative path where this source file should be written.
   *
   * @return the relative file path
   */
  public Path getRelativePath() {
    String packagePath = packageName.replace('.', '/');
    if (packagePath.isEmpty()) {
      return Path.of(className + ".java");
    }
    return Path.of(packagePath, className + ".java");
  }

  /**
   * Returns the source code content as a string.
   *
   * @return the generated Java source code
   */
  public String getContent() {
    return javaFile.toString();
  }

  /**
   * Returns the underlying JavaPoet JavaFile.
   *
   * @return the JavaFile
   */
  public JavaFile getJavaFile() {
    return javaFile;
  }

  /**
   * Writes this generated source to the specified output directory.
   *
   * <p>The file will be written to the appropriate subdirectory based on the package name. Parent
   * directories are created if they don't exist.
   *
   * @param outputDirectory the base output directory
   * @throws BindgenException if writing fails
   */
  public void writeTo(final Path outputDirectory) throws BindgenException {
    Objects.requireNonNull(outputDirectory, "outputDirectory");
    try {
      javaFile.writeTo(outputDirectory);
    } catch (IOException e) {
      throw BindgenException.ioError("writing " + getQualifiedName() + " to " + outputDirectory, e);
    }
  }

  /**
   * Writes this generated source to a specific file path.
   *
   * <p>Parent directories are created if they don't exist.
   *
   * @param filePath the target file path
   * @throws BindgenException if writing fails
   */
  public void writeToFile(final Path filePath) throws BindgenException {
    Objects.requireNonNull(filePath, "filePath");
    try {
      Path parent = filePath.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.writeString(filePath, getContent(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw BindgenException.ioError("writing to " + filePath, e);
    }
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    GeneratedSource that = (GeneratedSource) obj;
    return Objects.equals(packageName, that.packageName)
        && Objects.equals(className, that.className);
  }

  @Override
  public int hashCode() {
    return Objects.hash(packageName, className);
  }

  @Override
  public String toString() {
    return "GeneratedSource{" + getQualifiedName() + "}";
  }
}
