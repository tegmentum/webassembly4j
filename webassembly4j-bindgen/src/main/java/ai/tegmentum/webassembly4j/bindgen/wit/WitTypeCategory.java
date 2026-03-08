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
package ai.tegmentum.webassembly4j.bindgen.wit;

/**
 * Categories of WebAssembly Interface Types (WIT) for validation and marshalling.
 *
 * <p>Type categories group similar types together for common processing and validation rules.
 *
 * @since 1.0.0
 */
public enum WitTypeCategory {
  /** Primitive scalar types (bool, integers, floats, char, string). */
  PRIMITIVE,

  /** Record types with named fields. */
  RECORD,

  /** Variant types with discriminated union cases. */
  VARIANT,

  /** Enumeration types with named values. */
  ENUM,

  /** Flag types with named boolean flags. */
  FLAGS,

  /** List types with homogeneous elements. */
  LIST,

  /** Optional types that may or may not have a value. */
  OPTION,

  /** Result types representing success or error. */
  RESULT,

  /** Tuple types with heterogeneous elements. */
  TUPLE,

  /** Resource types with opaque handles. */
  RESOURCE
}
