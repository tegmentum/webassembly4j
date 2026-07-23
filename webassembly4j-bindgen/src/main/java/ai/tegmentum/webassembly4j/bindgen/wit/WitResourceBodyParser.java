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
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Parses the body of a WIT {@code resource} declaration into a list of
 * {@link WitResourceMethod}s.
 *
 * <p>Understands the three declaration shapes:
 *
 * <ul>
 *   <li>{@code constructor(params)}
 *   <li>{@code name: static func(params) -> ret}
 *   <li>{@code name: func(params) -> ret}
 * </ul>
 *
 * <p>Each declaration is terminated by a comma or semicolon (both are
 * accepted). Doc comments and line comments are stripped before parsing.
 *
 * <p>This lives alongside {@link WitInterfaceParser} rather than inside it
 * because the interface parser's regex machinery can't cleanly express
 * the {@code constructor}/{@code static func} shapes; keeping it separate
 * makes the split cheap to grow.
 */
public final class WitResourceBodyParser {

  private WitResourceBodyParser() {}

  /**
   * A single method declaration on a resource, retaining enough shape for
   * downstream Java code generation to decide static vs instance vs
   * constructor emission.
   */
  public static final class WitResourceMethod {
    private final String name;
    private final Kind kind;
    private final List<WitParameter> parameters;
    private final Optional<String> returnTypeExpression;

    WitResourceMethod(
        final String name,
        final Kind kind,
        final List<WitParameter> parameters,
        final Optional<String> returnTypeExpression) {
      this.name = Objects.requireNonNull(name, "name");
      this.kind = Objects.requireNonNull(kind, "kind");
      this.parameters = List.copyOf(Objects.requireNonNull(parameters, "parameters"));
      this.returnTypeExpression = Objects.requireNonNull(returnTypeExpression, "returnTypeExpression");
    }

    public String getName() {
      return name;
    }

    public Kind getKind() {
      return kind;
    }

    public List<WitParameter> getParameters() {
      return parameters;
    }

    /**
     * The raw return-type expression as written in the WIT source, or empty
     * if the method returned no value. Kept as text so the downstream
     * pipeline can resolve it against the interface's already-parsed type
     * table.
     */
    public Optional<String> getReturnTypeExpression() {
      return returnTypeExpression;
    }

    public enum Kind {
      CONSTRUCTOR,
      STATIC,
      INSTANCE
    }
  }

  /**
   * Parse the body text of a WIT resource declaration.
   *
   * @param resourceName the name of the resource (used for diagnostics)
   * @param body the body between the resource's outer braces
   * @return the parsed methods, in declaration order
   * @throws BindgenException if the body cannot be parsed
   */
  public static List<WitResourceMethod> parse(final String resourceName, final String body)
      throws BindgenException {
    final String stripped = stripLineComments(body);
    final List<String> decls = splitDeclarations(stripped);
    final List<WitResourceMethod> methods = new ArrayList<>();
    for (final String decl : decls) {
      final String trimmed = decl.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      methods.add(parseDeclaration(resourceName, trimmed));
    }
    return methods;
  }

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

  /**
   * Split a resource body on top-level {@code ,} or {@code ;} terminators.
   * Angle-bracket and paren nesting are respected so {@code list<u8, u8>}
   * and {@code func(a: u32, b: u32)} don't split mid-signature.
   */
  private static List<String> splitDeclarations(final String body) {
    final List<String> out = new ArrayList<>();
    final StringBuilder current = new StringBuilder();
    int angle = 0;
    int paren = 0;
    boolean inString = false;
    for (int i = 0; i < body.length(); i++) {
      final char c = body.charAt(i);
      if (c == '"' && (i == 0 || body.charAt(i - 1) != '\\')) {
        inString = !inString;
        current.append(c);
        continue;
      }
      if (!inString) {
        if (c == '<') {
          angle++;
        } else if (c == '>') {
          angle = Math.max(0, angle - 1);
        } else if (c == '(') {
          paren++;
        } else if (c == ')') {
          paren = Math.max(0, paren - 1);
        }
      }
      if (!inString && angle == 0 && paren == 0 && (c == ',' || c == ';')) {
        out.add(current.toString());
        current.setLength(0);
      } else {
        current.append(c);
      }
    }
    if (current.length() > 0) {
      out.add(current.toString());
    }
    return out;
  }

  private static WitResourceMethod parseDeclaration(final String resourceName, final String decl)
      throws BindgenException {
    if (decl.startsWith("constructor")) {
      final int open = decl.indexOf('(');
      final int close = findMatchingParen(decl, open);
      if (open < 0 || close < 0) {
        throw new BindgenException(
            "Malformed constructor on resource " + resourceName + ": " + decl);
      }
      final String params = decl.substring(open + 1, close);
      return new WitResourceMethod(
          "constructor",
          WitResourceMethod.Kind.CONSTRUCTOR,
          parseParameters(params),
          Optional.empty());
    }
    final int colon = decl.indexOf(':');
    if (colon < 0) {
      throw new BindgenException(
          "Resource method missing ':' on resource " + resourceName + ": " + decl);
    }
    final String name = decl.substring(0, colon).trim();
    final String rhs = decl.substring(colon + 1).trim();
    final boolean isStatic = rhs.startsWith("static ");
    final String afterStatic = isStatic ? rhs.substring("static ".length()).trim() : rhs;
    if (!afterStatic.startsWith("func")) {
      throw new BindgenException(
          "Expected 'func' after ':' on resource " + resourceName + "." + name + ": " + rhs);
    }
    final int open = afterStatic.indexOf('(');
    final int close = findMatchingParen(afterStatic, open);
    if (open < 0 || close < 0) {
      throw new BindgenException(
          "Malformed func signature on resource " + resourceName + "." + name);
    }
    final String params = afterStatic.substring(open + 1, close);
    final String tail = afterStatic.substring(close + 1).trim();
    Optional<String> returnExpr = Optional.empty();
    if (tail.startsWith("->")) {
      returnExpr = Optional.of(tail.substring(2).trim());
    }
    return new WitResourceMethod(
        name,
        isStatic ? WitResourceMethod.Kind.STATIC : WitResourceMethod.Kind.INSTANCE,
        parseParameters(params),
        returnExpr);
  }

  private static int findMatchingParen(final String s, final int open) {
    if (open < 0) {
      return -1;
    }
    int depth = 0;
    boolean inString = false;
    for (int i = open; i < s.length(); i++) {
      final char c = s.charAt(i);
      if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\')) {
        inString = !inString;
        continue;
      }
      if (inString) {
        continue;
      }
      if (c == '(') {
        depth++;
      } else if (c == ')') {
        depth--;
        if (depth == 0) {
          return i;
        }
      }
    }
    return -1;
  }

  private static List<WitParameter> parseParameters(final String params) throws BindgenException {
    final List<WitParameter> out = new ArrayList<>();
    if (params == null || params.trim().isEmpty()) {
      return out;
    }
    for (final String piece : splitTopLevelCommas(params)) {
      final String trimmed = piece.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      final int colon = trimmed.indexOf(':');
      if (colon < 0) {
        throw new BindgenException("Invalid parameter (no ':'): " + piece);
      }
      final String name = trimmed.substring(0, colon).trim();
      final String typeExpr = trimmed.substring(colon + 1).trim();
      // Store the raw type expression as the WitType name; the downstream
      // code generator resolves it against the interface's type table.
      out.add(new WitParameter(name, WitType.resource(typeExpr, typeExpr), false, Optional.empty()));
    }
    return out;
  }

  private static List<String> splitTopLevelCommas(final String s) {
    final List<String> out = new ArrayList<>();
    int angle = 0;
    int paren = 0;
    boolean inString = false;
    final StringBuilder current = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      final char c = s.charAt(i);
      if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\')) {
        inString = !inString;
        current.append(c);
        continue;
      }
      if (!inString) {
        if (c == '<') {
          angle++;
        } else if (c == '>') {
          angle = Math.max(0, angle - 1);
        } else if (c == '(') {
          paren++;
        } else if (c == ')') {
          paren = Math.max(0, paren - 1);
        }
      }
      if (!inString && angle == 0 && paren == 0 && c == ',') {
        out.add(current.toString());
        current.setLength(0);
      } else {
        current.append(c);
      }
    }
    if (current.length() > 0) {
      out.add(current.toString());
    }
    return out;
  }
}
