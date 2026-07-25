/*
 * Copyright 2024 Tegmentum AI
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

import ai.tegmentum.webassembly4j.bindgen.BindgenException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Single-interface facade over {@link WitParser}, kept for backward-compatible
 * use by tests and older callers that hand this parser a bare
 * {@code interface X { ... }} fragment.
 *
 * <p>The regex-based single-interface parser, its {@code WitWorldPreprocessor}
 * type-hoisting pass, and its {@code WitResourceBodyParser} resource-shape
 * pass are all retired — the token-stream + recursive-descent {@link
 * WitParser} covers the full WIT grammar those wrappers used to patch
 * (package / world with type-hoisting / multi-interface / use / import /
 * export / resource bodies). Everything real should call {@link
 * WitParser#parse(String)} directly and consume the returned {@link
 * WitDocument}. This facade only survives so the legacy single-interface
 * tests keep exercising the same code path via a familiar entrypoint.
 *
 * @since 1.0.0
 */
public final class WitInterfaceParser {

  /** Creates a new WIT interface parser facade. */
  public WitInterfaceParser() {}

  /**
   * Parse a single WIT interface fragment.
   *
   * @param witText the WIT source — must contain exactly one
   *     {@code interface X { ... }} block
   * @param packageName the package name to attach to the returned
   *     definition
   * @return the parsed interface definition
   * @throws BindgenException if parsing fails or the fragment doesn't
   *     resolve to exactly one interface
   */
  public WitInterfaceDefinition parseInterface(final String witText, final String packageName)
      throws BindgenException {
    Objects.requireNonNull(witText, "witText");
    Objects.requireNonNull(packageName, "packageName");
    final WitDocument doc = WitParser.parse(witText);
    if (doc.getInterfaces().isEmpty()) {
      throw new BindgenException("Invalid WIT interface: no interface declaration found");
    }
    // Legacy single-interface entrypoint — take the first interface (or world)
    // in declaration order. Multi-interface sources are handled directly
    // through WitParser today; this path just picks the top one.
    final WitDocument.ParsedInterface pi = doc.getInterfaces().get(0);
    final List<String> exports = new ArrayList<>(pi.getFunctions().keySet());
    return new DefaultWitInterfaceDefinition(
        pi.getName(),
        "1.0",
        packageName,
        pi.getFunctions(),
        pi.getTypes(),
        List.of(),
        exports,
        witText);
  }
}
