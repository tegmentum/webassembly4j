/*
 * Copyright 2026 Tegmentum AI
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Preprocesses a full WIT world source (package + optional {@code world} blocks +
 * multiple {@code interface} blocks + resource declarations with bodies) into a
 * flat list of {@link Interface} records the existing regex-based
 * {@link WitInterfaceParser} can consume.
 *
 * <p>Behaviour:
 *
 * <ul>
 *   <li>{@code package X@Y;} declarations are removed.
 *   <li>{@code world X { ... }} blocks are skipped entirely — their
 *       {@code import} / {@code export} clauses aren't part of any interface
 *       surface bindgen needs to project.
 *   <li>{@code interface X { ... }} blocks are split into individual
 *       {@link Interface} entries. The existing parser is single-interface;
 *       this splitter fans a world file out into a stream of what it expects.
 *   <li>{@code resource X { body }} declarations inside an interface body have
 *       their bodies stripped and returned in a side-map keyed by resource
 *       name. The interface body then contains a bare {@code resource X;}
 *       token so {@link WitInterfaceParser} still sees the resource type
 *       declaration but doesn't choke on the constructor / static / method
 *       syntax it doesn't understand.
 * </ul>
 */
public final class WitWorldPreprocessor {

  private WitWorldPreprocessor() {}

  /** A single interface extracted from a WIT world source. */
  public static final class Interface {
    private final String name;
    private final String body;
    private final Map<String, String> resourceBodies;

    Interface(final String name, final String body, final Map<String, String> resourceBodies) {
      this.name = name;
      this.body = body;
      this.resourceBodies = resourceBodies;
    }

    /** The interface's name as written in WIT (e.g. {@code embedder-api}). */
    public String getName() {
      return name;
    }

    /**
     * The interface body with resource bodies stripped — safe to hand to
     * {@link WitInterfaceParser#parseInterface(String, String)} after
     * wrapping in {@code interface name { ... }}.
     */
    public String getBody() {
      return body;
    }

    /**
     * Map from resource name to its unparsed body text. Callers that want
     * constructor / static / instance-method definitions parse these
     * themselves. Empty when the interface declares no resources with
     * bodies.
     */
    public Map<String, String> getResourceBodies() {
      return resourceBodies;
    }
  }

  /**
   * Split a full WIT source into per-interface entries.
   *
   * @param witText the raw WIT world source
   * @return the interfaces contained in the source, in declaration order
   * @throws BindgenException if brace matching fails
   */
  public static List<Interface> preprocess(final String witText) throws BindgenException {
    final String withoutComments = stripLineComments(witText);
    final String withoutPackage = stripPackage(withoutComments);
    final String withoutWorlds = stripTopLevelBlocks(withoutPackage, "world");
    return splitInterfaces(withoutWorlds);
  }

  /**
   * Remove {@code //…} comments (respecting string literals). Doc comments
   * ({@code ///…}) are removed by the same pass since they start with
   * {@code //} too.
   */
  private static String stripLineComments(final String source) {
    final StringBuilder out = new StringBuilder(source.length());
    for (final String line : source.split("\n", -1)) {
      int cut = -1;
      boolean inString = false;
      for (int i = 0; i < line.length() - 1; i++) {
        final char c = line.charAt(i);
        if (c == '"' && (i == 0 || line.charAt(i - 1) != '\\')) {
          inString = !inString;
        } else if (!inString && c == '/' && line.charAt(i + 1) == '/') {
          cut = i;
          break;
        }
      }
      if (cut < 0) {
        out.append(line).append('\n');
      } else {
        out.append(line, 0, cut).append('\n');
      }
    }
    return out.toString();
  }

  /** Strip {@code package name@version;} and {@code package name;} lines. */
  private static String stripPackage(final String source) {
    return source.replaceAll("(?m)^\\s*package\\s+[^;]+;\\s*$", "");
  }

  /**
   * Remove every top-level {@code keyword name { … }} block (used to skip
   * {@code world}). Brace-balanced so nested braces don't confuse the scan.
   */
  private static String stripTopLevelBlocks(final String source, final String keyword)
      throws BindgenException {
    final StringBuilder out = new StringBuilder(source.length());
    int i = 0;
    while (i < source.length()) {
      final int idx = source.indexOf(keyword, i);
      if (idx < 0) {
        out.append(source, i, source.length());
        break;
      }
      if (!isKeywordBoundary(source, idx, keyword.length())) {
        out.append(source, i, idx + 1);
        i = idx + 1;
        continue;
      }
      final int braceOpen = source.indexOf('{', idx);
      if (braceOpen < 0) {
        // No body — leave the fragment alone, we may be scanning garbage.
        out.append(source, i, source.length());
        break;
      }
      out.append(source, i, idx);
      final int braceClose = matchBrace(source, braceOpen);
      if (braceClose < 0) {
        throw new BindgenException(
            "Unbalanced braces in " + keyword + " block starting at offset " + braceOpen);
      }
      i = braceClose + 1;
    }
    return out.toString();
  }

  private static boolean isKeywordBoundary(final String source, final int at, final int keywordLen) {
    if (at > 0) {
      final char before = source.charAt(at - 1);
      if (Character.isLetterOrDigit(before) || before == '-' || before == '_') {
        return false;
      }
    }
    final int after = at + keywordLen;
    if (after < source.length()) {
      final char afterCh = source.charAt(after);
      if (Character.isLetterOrDigit(afterCh) || afterCh == '-' || afterCh == '_') {
        return false;
      }
    }
    return true;
  }

  private static int matchBrace(final String source, final int open) {
    int depth = 0;
    boolean inString = false;
    for (int i = open; i < source.length(); i++) {
      final char c = source.charAt(i);
      if (c == '"' && (i == 0 || source.charAt(i - 1) != '\\')) {
        inString = !inString;
        continue;
      }
      if (inString) {
        continue;
      }
      if (c == '{') {
        depth++;
      } else if (c == '}') {
        depth--;
        if (depth == 0) {
          return i;
        }
      }
    }
    return -1;
  }

  /**
   * Walk the (comment-and-world-free) source and split it into
   * {@code interface X { body }} blocks. Resource declarations inside each
   * body are extracted and returned separately.
   */
  private static List<Interface> splitInterfaces(final String source) throws BindgenException {
    final List<Interface> result = new ArrayList<>();
    int i = 0;
    while (i < source.length()) {
      final int idx = source.indexOf("interface", i);
      if (idx < 0) {
        break;
      }
      if (!isKeywordBoundary(source, idx, "interface".length())) {
        i = idx + 1;
        continue;
      }
      // Read the identifier that follows.
      int cursor = idx + "interface".length();
      while (cursor < source.length() && Character.isWhitespace(source.charAt(cursor))) {
        cursor++;
      }
      final int nameStart = cursor;
      while (cursor < source.length() && isIdentChar(source.charAt(cursor))) {
        cursor++;
      }
      final String name = source.substring(nameStart, cursor);
      // Skip whitespace to the opening brace.
      while (cursor < source.length() && Character.isWhitespace(source.charAt(cursor))) {
        cursor++;
      }
      if (cursor >= source.length() || source.charAt(cursor) != '{') {
        throw new BindgenException("Expected '{' after interface " + name);
      }
      final int braceOpen = cursor;
      final int braceClose = matchBrace(source, braceOpen);
      if (braceClose < 0) {
        throw new BindgenException("Unbalanced braces in interface " + name);
      }
      final String body = source.substring(braceOpen + 1, braceClose);
      final Map<String, String> resources = new LinkedHashMap<>();
      final String bodyStripped = extractResourceBodies(body, resources);
      result.add(new Interface(name, bodyStripped, resources));
      i = braceClose + 1;
    }
    return result;
  }

  private static boolean isIdentChar(final char c) {
    return Character.isLetterOrDigit(c) || c == '-' || c == '_';
  }

  /**
   * Find each {@code resource name { body }} declaration in the interface body
   * and replace it with a bare {@code resource name;} so the downstream
   * regex-based parser still sees the type. Emit the extracted bodies into
   * {@code sink} keyed by resource name.
   */
  private static String extractResourceBodies(final String body, final Map<String, String> sink)
      throws BindgenException {
    final StringBuilder out = new StringBuilder(body.length());
    int i = 0;
    while (i < body.length()) {
      final int idx = body.indexOf("resource", i);
      if (idx < 0) {
        out.append(body, i, body.length());
        break;
      }
      if (!isKeywordBoundary(body, idx, "resource".length())) {
        out.append(body, i, idx + 1);
        i = idx + 1;
        continue;
      }
      // Copy everything up to the keyword.
      out.append(body, i, idx);
      int cursor = idx + "resource".length();
      while (cursor < body.length() && Character.isWhitespace(body.charAt(cursor))) {
        cursor++;
      }
      final int nameStart = cursor;
      while (cursor < body.length() && isIdentChar(body.charAt(cursor))) {
        cursor++;
      }
      final String name = body.substring(nameStart, cursor);
      // Look ahead for `{` (declaration-with-body) or `;`
      // (declaration-without-body, e.g. `type X = resource;`).
      int lookAhead = cursor;
      while (lookAhead < body.length() && Character.isWhitespace(body.charAt(lookAhead))) {
        lookAhead++;
      }
      if (lookAhead < body.length() && body.charAt(lookAhead) == '{') {
        final int braceClose = matchBrace(body, lookAhead);
        if (braceClose < 0) {
          throw new BindgenException("Unbalanced braces in resource " + name);
        }
        sink.put(name, body.substring(lookAhead + 1, braceClose));
        // Emit a `type` alias so the downstream parser records the
        // resource type but doesn't try to interpret the method body.
        // The parser only understands standalone RECORD/VARIANT/ENUM/
        // FLAGS declarations plus `type X = ...` aliases; resources
        // hitch a ride on the alias form.
        out.append("type ").append(name).append(" = resource;\n");
        i = braceClose + 1;
      } else {
        // Not a body-bearing declaration — keep original text.
        out.append(body, idx, cursor);
        i = cursor;
      }
    }
    return out.toString();
  }
}
