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

/**
 * Exception thrown when binding generation fails.
 *
 * <p>This exception is used for all errors that occur during the binding generation process,
 * including:
 *
 * <ul>
 *   <li>WIT parsing errors
 *   <li>WASM introspection errors
 *   <li>Code generation errors
 *   <li>File I/O errors
 *   <li>Configuration validation errors
 * </ul>
 */
public class BindgenException extends Exception {

  private static final long serialVersionUID = 1L;

  /**
   * Constructs a new BindgenException with the specified detail message.
   *
   * @param message the detail message
   */
  public BindgenException(final String message) {
    super(message);
  }

  /**
   * Constructs a new BindgenException with the specified detail message and cause.
   *
   * @param message the detail message
   * @param cause the cause of this exception
   */
  public BindgenException(final String message, final Throwable cause) {
    super(message, cause);
  }

  /**
   * Constructs a new BindgenException with the specified cause.
   *
   * @param cause the cause of this exception
   */
  public BindgenException(final Throwable cause) {
    super(cause);
  }

  /**
   * Creates a BindgenException for WIT parsing errors.
   *
   * @param witFile the WIT file that failed to parse
   * @param cause the underlying parsing error
   * @return a new BindgenException
   */
  public static BindgenException witParseError(final String witFile, final Throwable cause) {
    return new BindgenException("Failed to parse WIT file: " + witFile, cause);
  }

  /**
   * Creates a BindgenException for WASM introspection errors.
   *
   * @param wasmFile the WASM file that failed to introspect
   * @param cause the underlying introspection error
   * @return a new BindgenException
   */
  public static BindgenException wasmIntrospectionError(
      final String wasmFile, final Throwable cause) {
    return new BindgenException("Failed to introspect WASM module: " + wasmFile, cause);
  }

  /**
   * Creates a BindgenException for code generation errors.
   *
   * @param typeName the type that failed to generate
   * @param cause the underlying generation error
   * @return a new BindgenException
   */
  public static BindgenException codeGenerationError(final String typeName, final Throwable cause) {
    return new BindgenException("Failed to generate code for type: " + typeName, cause);
  }

  /**
   * Creates a BindgenException for configuration validation errors.
   *
   * @param message the validation error message
   * @return a new BindgenException
   */
  public static BindgenException configurationError(final String message) {
    return new BindgenException("Configuration error: " + message);
  }

  /**
   * Creates a BindgenException for I/O errors.
   *
   * @param operation the operation that failed
   * @param cause the underlying I/O error
   * @return a new BindgenException
   */
  public static BindgenException ioError(final String operation, final Throwable cause) {
    return new BindgenException("I/O error during " + operation, cause);
  }
}
