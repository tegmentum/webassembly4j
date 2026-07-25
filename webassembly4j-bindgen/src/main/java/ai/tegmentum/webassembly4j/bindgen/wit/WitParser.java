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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Recursive-descent parser for the WebAssembly Interface Type (WIT) grammar.
 *
 * <p>Replaces the previous regex parser + {@code WitWorldPreprocessor} +
 * {@code WitResourceBodyParser} wrapper chain with a single walk over a
 * {@link WitLexer}-produced token stream. Consumes:
 *
 * <ul>
 *   <li>{@code package name[:sub][@version];} declarations
 *   <li>{@code world <name> { ... }} blocks with type items directly in the
 *       body (post-hoist WIT shape per ADR-006). {@code import} / {@code
 *       export} / {@code use} clauses inside a world are accepted and
 *       discarded — they reference already-declared interfaces and don't
 *       contribute new type surface.
 *   <li>{@code interface <name> { ... }} blocks
 *   <li>{@code use <package>[:<sub>][/<name>][.{<items>}];} statements
 *   <li>{@code import <name>: func(...) -> ...;} / {@code import <name>:
 *       <type>;} / {@code import <interface-name>[;]}
 *   <li>The matching {@code export} shapes
 *   <li>Resource bodies: {@code resource <name> { constructor(...);
 *       <method>: func(...); [static] <static>: func(...); }}. Methods
 *       land on the same {@link WitFunction} type as top-level functions
 *       with {@link WitFunction#isConstructor()} / {@link
 *       WitFunction#isStatic()} flags set — one translation path serves
 *       both.
 *   <li>Type aliases: {@code type <name> = <expr>;}
 *   <li>Standalone type declarations: {@code record}, {@code variant},
 *       {@code enum}, {@code flags}
 *   <li>Type expressions: primitives, {@code list<T>}, {@code option<T>},
 *       {@code result<T, E>} (both operands optional), {@code tuple<T,
 *       U, ...>}, {@code borrow<T>}, {@code own<T>}
 * </ul>
 *
 * <p>Types are emitted in WIT declaration order — the {@code
 * LinkedHashMap} backing on {@link WitDocument.ParsedInterface#getTypes()}
 * preserves this for byte-identical cross-JVM generated output.
 */
public final class WitParser {

  private static final int MAX_WIT_TEXT_LENGTH = 1024 * 1024; // 1 MB

  private final List<WitToken> tokens;
  private int pos;

  private WitParser(final List<WitToken> tokens) {
    this.tokens = tokens;
  }

  /**
   * Parse a full WIT source document.
   *
   * @param source the WIT source text
   * @return the parsed document
   * @throws BindgenException if parsing fails
   */
  public static WitDocument parse(final String source) throws BindgenException {
    if (source == null) {
      throw new BindgenException("WIT source is null");
    }
    if (source.length() > MAX_WIT_TEXT_LENGTH) {
      throw new BindgenException(
          "WIT source exceeds maximum length of " + MAX_WIT_TEXT_LENGTH + " characters");
    }
    final List<WitToken> tokens = new WitLexer(source).tokenize();
    final WitParser parser = new WitParser(tokens);
    return parser.parseDocument();
  }

  // ---------------------------------------------------------------------------
  // Top-level document
  // ---------------------------------------------------------------------------

  private WitDocument parseDocument() throws BindgenException {
    Optional<String> packageName = Optional.empty();
    Optional<String> packageVersion = Optional.empty();
    final List<WitDocument.ParsedInterface> interfaces = new ArrayList<>();

    while (!atEnd()) {
      if (matchesIdent("package")) {
        final String[] pkg = parsePackageDecl();
        packageName = Optional.ofNullable(pkg[0]);
        packageVersion = pkg[1] == null ? Optional.empty() : Optional.of(pkg[1]);
      } else if (matchesIdent("interface")) {
        interfaces.add(parseInterfaceOrWorld(/* world= */ false));
      } else if (matchesIdent("world")) {
        interfaces.add(parseInterfaceOrWorld(/* world= */ true));
      } else {
        throw error("unexpected token at document top-level: " + peek().text);
      }
    }
    return new WitDocument(packageName, packageVersion, interfaces);
  }

  private String[] parsePackageDecl() throws BindgenException {
    expectIdent("package");
    final StringBuilder name = new StringBuilder(expectIdentToken().text);
    // Optional `:sub` (e.g. `wasmos:host`).
    while (peek().kind == WitToken.Kind.COLON) {
      advance();
      name.append(':').append(expectIdentToken().text);
    }
    // Optional `/path` fragment path (rare in package decls but permitted).
    while (peek().kind == WitToken.Kind.SLASH) {
      advance();
      name.append('/').append(expectIdentToken().text);
    }
    String version = null;
    if (peek().kind == WitToken.Kind.AT) {
      advance();
      version = expect(WitToken.Kind.VERSION).text;
    }
    expect(WitToken.Kind.SEMI);
    return new String[] {name.toString(), version};
  }

  // ---------------------------------------------------------------------------
  // Interface / world body
  // ---------------------------------------------------------------------------

  private WitDocument.ParsedInterface parseInterfaceOrWorld(final boolean world)
      throws BindgenException {
    expectIdent(world ? "world" : "interface");
    final String name = expectIdentToken().text;
    expect(WitToken.Kind.LBRACE);

    final Map<String, WitType> types = new LinkedHashMap<>();
    final Map<String, WitFunction> functions = new LinkedHashMap<>();
    final Map<String, List<WitFunction>> resourceMethods = new LinkedHashMap<>();

    while (peek().kind != WitToken.Kind.RBRACE && !atEnd()) {
      parseInterfaceItem(types, functions, resourceMethods);
    }
    expect(WitToken.Kind.RBRACE);
    return new WitDocument.ParsedInterface(name, world, types, functions, resourceMethods);
  }

  private void parseInterfaceItem(
      final Map<String, WitType> types,
      final Map<String, WitFunction> functions,
      final Map<String, List<WitFunction>> resourceMethods)
      throws BindgenException {
    final WitToken t = peek();
    if (t.kind != WitToken.Kind.IDENT) {
      throw error("expected declaration, got " + t.kind);
    }
    final String kw = t.text;
    switch (kw) {
      case "use":
        skipUseClause();
        return;
      case "import":
      case "export":
        // Import/export clauses inside a world (or interface, though rare)
        // don't declare new types on their own — they reference already-
        // declared interfaces or functions. We consume-and-discard the
        // clause and, when it's an inline `import name: func(...) -> ...`,
        // register the function so a downstream generator sees it.
        parseImportExportClause(functions);
        return;
      case "type":
        parseTypeAlias(types);
        return;
      case "record":
        parseRecordDecl(types);
        return;
      case "variant":
        parseVariantDecl(types);
        return;
      case "enum":
        parseEnumDecl(types);
        return;
      case "flags":
        parseFlagsDecl(types);
        return;
      case "resource":
        parseResourceDecl(types, resourceMethods);
        return;
      default:
        // Anything else must be `name: func(...)` or `name: <type>` —
        // a plain function declaration.
        parseFunctionDecl(functions);
    }
  }

  // ---- use / import / export -----------------------------------------------

  private void skipUseClause() throws BindgenException {
    expectIdent("use");
    // Consume until the terminating semicolon at depth 0. `use` clauses
    // may contain `{a, b, c}` item lists so we track brace depth.
    int depth = 0;
    while (!atEnd()) {
      final WitToken t = peek();
      if (t.kind == WitToken.Kind.LBRACE) {
        depth++;
        advance();
      } else if (t.kind == WitToken.Kind.RBRACE) {
        depth--;
        advance();
      } else if (t.kind == WitToken.Kind.SEMI && depth == 0) {
        advance();
        return;
      } else {
        advance();
      }
    }
    throw error("unterminated 'use' clause");
  }

  private void parseImportExportClause(final Map<String, WitFunction> functions)
      throws BindgenException {
    // Consume `import` / `export`.
    advance();
    // Two shapes:
    //   1. `import <ident>;`                          — bare interface reference
    //   2. `import <ident>: <func-or-type>;`          — inline binding
    //   3. `import <ident>: interface { ... }`        — inline interface (dropped)
    // (import / export take the same grammar.)
    if (peek().kind != WitToken.Kind.IDENT) {
      // Anything else — swallow the rest of the clause.
      skipToSemicolon();
      return;
    }
    final String name = expectIdentToken().text;
    if (peek().kind == WitToken.Kind.SEMI) {
      advance();
      return;
    }
    if (peek().kind != WitToken.Kind.COLON) {
      // Something we don't understand — best-effort skip.
      skipToSemicolon();
      return;
    }
    expect(WitToken.Kind.COLON);
    // If it's `func(...)`, register it as a function (matches how the
    // regex parser used to walk `name: func(...)` inside a world body
    // before the wrappers stripped it).
    if (matchesIdent("func")) {
      final WitFunction fn = parseFuncSignature(name);
      functions.put(name, fn);
      expect(WitToken.Kind.SEMI);
      return;
    }
    if (matchesIdent("interface")) {
      // `import name: interface { ... }` — drop the inline body wholesale.
      advance(); // consume `interface`
      expect(WitToken.Kind.LBRACE);
      int depth = 1;
      while (!atEnd() && depth > 0) {
        if (peek().kind == WitToken.Kind.LBRACE) {
          depth++;
        } else if (peek().kind == WitToken.Kind.RBRACE) {
          depth--;
        }
        advance();
      }
      // Optional trailing semicolon.
      if (peek().kind == WitToken.Kind.SEMI) {
        advance();
      }
      return;
    }
    // `import name: <type-expr>;` — swallow the type expression, we
    // don't need it downstream.
    skipToSemicolon();
  }

  private void skipToSemicolon() {
    int paren = 0;
    int angle = 0;
    int brace = 0;
    while (!atEnd()) {
      final WitToken t = peek();
      if (t.kind == WitToken.Kind.LPAREN) {
        paren++;
      } else if (t.kind == WitToken.Kind.RPAREN) {
        paren = Math.max(0, paren - 1);
      } else if (t.kind == WitToken.Kind.LT) {
        angle++;
      } else if (t.kind == WitToken.Kind.GT) {
        angle = Math.max(0, angle - 1);
      } else if (t.kind == WitToken.Kind.LBRACE) {
        brace++;
      } else if (t.kind == WitToken.Kind.RBRACE) {
        if (brace == 0) {
          return;
        }
        brace--;
      } else if (t.kind == WitToken.Kind.SEMI && paren == 0 && angle == 0 && brace == 0) {
        advance();
        return;
      }
      advance();
    }
  }

  // ---- type declarations ---------------------------------------------------

  private void parseTypeAlias(final Map<String, WitType> types) throws BindgenException {
    expectIdent("type");
    final String name = expectIdentToken().text;
    expect(WitToken.Kind.EQ);
    // Peek to see if it's a compound: `type X = record { ... }` /
    // `type X = variant { ... }` etc. Otherwise it's a type expression.
    if (matchesIdent("record")) {
      advance();
      types.put(name, parseRecordBody(name));
    } else if (matchesIdent("variant")) {
      advance();
      types.put(name, parseVariantBody(name));
    } else if (matchesIdent("enum")) {
      advance();
      types.put(name, parseEnumBody(name));
    } else if (matchesIdent("flags")) {
      advance();
      types.put(name, parseFlagsBody(name));
    } else if (matchesIdent("resource")) {
      advance();
      // `type X = resource;` — bare-resource alias (rare but legal).
      types.put(name, WitType.resource(name, name));
    } else {
      final WitType expr = parseTypeExpression(types);
      // If the returned type is a primitive/list/option/result/tuple, name
      // it with the alias name so the generator emits it under that name.
      // For record/variant/enum/flags/resource references we keep the
      // referent type — the alias name isn't the same as the referent.
      types.put(name, renameIfPrimitiveLike(name, expr));
    }
    // Old regex parser accepted `type X = Y` without trailing `;` at
    // end of body — keep that tolerance.
    consumeOptionalSemi();
  }

  private WitType renameIfPrimitiveLike(final String aliasName, final WitType src) {
    final WitTypeCategory cat = src.getKind().getCategory();
    switch (cat) {
      case PRIMITIVE:
      case LIST:
      case OPTION:
      case RESULT:
      case TUPLE:
      case RESOURCE:
        // Container / primitive aliases keep the alias name where possible.
        // We rebuild only for primitives (WitType.primitive() carries its
        // own textual name); list/option/result/tuple were built with
        // container names by parseTypeExpression already.
        if (cat == WitTypeCategory.PRIMITIVE) {
          // Represent an alias-to-primitive as-is; the alias name is what
          // matters for downstream deduplication.
          return src;
        }
        return src;
      default:
        return src;
    }
  }

  private void parseRecordDecl(final Map<String, WitType> types) throws BindgenException {
    expectIdent("record");
    final String name = expectIdentToken().text;
    types.put(name, parseRecordBody(name));
  }

  private WitType parseRecordBody(final String name) throws BindgenException {
    expect(WitToken.Kind.LBRACE);
    final Map<String, WitType> fields = new LinkedHashMap<>();
    while (peek().kind != WitToken.Kind.RBRACE && !atEnd()) {
      final String fieldName = expectIdentToken().text;
      expect(WitToken.Kind.COLON);
      final WitType fieldType = parseTypeExpression(null);
      fields.put(fieldName, fieldType);
      // Trailing comma is optional; both `,` and end-of-body terminate.
      if (peek().kind == WitToken.Kind.COMMA) {
        advance();
      }
    }
    expect(WitToken.Kind.RBRACE);
    return WitType.record(name, fields);
  }

  private void parseVariantDecl(final Map<String, WitType> types) throws BindgenException {
    expectIdent("variant");
    final String name = expectIdentToken().text;
    types.put(name, parseVariantBody(name));
  }

  private WitType parseVariantBody(final String name) throws BindgenException {
    expect(WitToken.Kind.LBRACE);
    final Map<String, Optional<WitType>> cases = new LinkedHashMap<>();
    while (peek().kind != WitToken.Kind.RBRACE && !atEnd()) {
      final String caseName = expectIdentToken().text;
      Optional<WitType> payload = Optional.empty();
      if (peek().kind == WitToken.Kind.LPAREN) {
        advance();
        payload = Optional.of(parseTypeExpression(null));
        expect(WitToken.Kind.RPAREN);
      }
      cases.put(caseName, payload);
      if (peek().kind == WitToken.Kind.COMMA) {
        advance();
      }
    }
    expect(WitToken.Kind.RBRACE);
    return WitType.variant(name, cases);
  }

  private void parseEnumDecl(final Map<String, WitType> types) throws BindgenException {
    expectIdent("enum");
    final String name = expectIdentToken().text;
    types.put(name, parseEnumBody(name));
  }

  private WitType parseEnumBody(final String name) throws BindgenException {
    expect(WitToken.Kind.LBRACE);
    final List<String> values = new ArrayList<>();
    while (peek().kind != WitToken.Kind.RBRACE && !atEnd()) {
      values.add(expectIdentToken().text);
      if (peek().kind == WitToken.Kind.COMMA) {
        advance();
      }
    }
    expect(WitToken.Kind.RBRACE);
    return WitType.enumType(name, values);
  }

  private void parseFlagsDecl(final Map<String, WitType> types) throws BindgenException {
    expectIdent("flags");
    final String name = expectIdentToken().text;
    types.put(name, parseFlagsBody(name));
  }

  private WitType parseFlagsBody(final String name) throws BindgenException {
    expect(WitToken.Kind.LBRACE);
    final List<String> flags = new ArrayList<>();
    while (peek().kind != WitToken.Kind.RBRACE && !atEnd()) {
      flags.add(expectIdentToken().text);
      if (peek().kind == WitToken.Kind.COMMA) {
        advance();
      }
    }
    expect(WitToken.Kind.RBRACE);
    return WitType.flags(name, flags);
  }

  // ---- resource declarations -----------------------------------------------

  private void parseResourceDecl(
      final Map<String, WitType> types, final Map<String, List<WitFunction>> resourceMethods)
      throws BindgenException {
    expectIdent("resource");
    final String name = expectIdentToken().text;
    if (peek().kind == WitToken.Kind.SEMI) {
      // `resource X;` — no body.
      advance();
      types.put(name, WitType.resource(name, name));
      return;
    }
    expect(WitToken.Kind.LBRACE);
    final List<WitFunction> methods = new ArrayList<>();
    while (peek().kind != WitToken.Kind.RBRACE && !atEnd()) {
      methods.add(parseResourceMethod(name));
      // WIT tolerates both `;` and `,` between resource-body items, plus
      // a trailing separator before `}`.
      if (peek().kind == WitToken.Kind.SEMI || peek().kind == WitToken.Kind.COMMA) {
        advance();
      }
    }
    expect(WitToken.Kind.RBRACE);
    types.put(name, WitType.resource(name, name));
    resourceMethods.put(name, methods);
  }

  private WitFunction parseResourceMethod(final String resourceName) throws BindgenException {
    if (matchesIdent("constructor")) {
      advance();
      expect(WitToken.Kind.LPAREN);
      final List<WitParameter> params = parseFuncParameters();
      expect(WitToken.Kind.RPAREN);
      return WitFunction.resourceMethod(
          "constructor",
          params,
          List.of(),
          /* isConstructor= */ true,
          /* isStatic= */ false,
          /* isAsync= */ false,
          Optional.empty());
    }
    final String methodName = expectIdentToken().text;
    expect(WitToken.Kind.COLON);
    boolean isStatic = false;
    if (matchesIdent("static")) {
      advance();
      isStatic = true;
    }
    expectIdent("func");
    expect(WitToken.Kind.LPAREN);
    final List<WitParameter> params = parseFuncParameters();
    expect(WitToken.Kind.RPAREN);
    final List<WitType> returnTypes = parseOptionalReturnTypes();
    return WitFunction.resourceMethod(
        methodName, params, returnTypes, false, isStatic, false, Optional.empty());
  }

  // ---- function declarations ------------------------------------------------

  private void parseFunctionDecl(final Map<String, WitFunction> functions) throws BindgenException {
    final String name = expectIdentToken().text;
    expect(WitToken.Kind.COLON);
    // Optional `async` keyword before `func`.
    boolean isAsync = false;
    if (matchesIdent("async")) {
      advance();
      isAsync = true;
    }
    if (!matchesIdent("func")) {
      // `name: <type>;` — not a function, but a typed binding. We
      // don't have a first-class binding-type slot; skip it so the
      // caller doesn't crash. This matches the tolerance of the old
      // wrapper chain.
      skipToSemicolon();
      return;
    }
    final WitFunction fn = parseFuncSignature(name);
    functions.put(name, fn.getName().equals(name) ? withAsync(fn, isAsync) : fn);
    // Trailing `;` is required by strict WIT but the old regex parser
    // tolerated its absence at end-of-body. Preserve that laxness so
    // legacy fixtures (`add: func(...) -> s32\n}`) keep parsing.
    consumeOptionalSemi();
  }

  private void consumeOptionalSemi() {
    if (peek().kind == WitToken.Kind.SEMI) {
      advance();
    }
  }

  private WitFunction withAsync(final WitFunction fn, final boolean async) {
    if (!async) {
      return fn;
    }
    return new WitFunction(fn.getName(), fn.getParameters(), fn.getReturnTypes(), true, fn.getDocumentation());
  }

  private WitFunction parseFuncSignature(final String name) throws BindgenException {
    expectIdent("func");
    expect(WitToken.Kind.LPAREN);
    final List<WitParameter> params = parseFuncParameters();
    expect(WitToken.Kind.RPAREN);
    final List<WitType> returnTypes = parseOptionalReturnTypes();
    return new WitFunction(name, params, returnTypes, false, Optional.empty());
  }

  private List<WitParameter> parseFuncParameters() throws BindgenException {
    final List<WitParameter> params = new ArrayList<>();
    while (peek().kind != WitToken.Kind.RPAREN && !atEnd()) {
      final String pname = expectIdentToken().text;
      expect(WitToken.Kind.COLON);
      final WitType ptype = parseTypeExpression(null);
      params.add(new WitParameter(pname, ptype, false, Optional.empty()));
      if (peek().kind == WitToken.Kind.COMMA) {
        advance();
      }
    }
    return params;
  }

  private List<WitType> parseOptionalReturnTypes() throws BindgenException {
    if (peek().kind != WitToken.Kind.ARROW) {
      return List.of();
    }
    advance();
    final WitType retType = parseTypeExpression(null);
    return List.of(retType);
  }

  // ---------------------------------------------------------------------------
  // Type expressions
  // ---------------------------------------------------------------------------

  /**
   * Parse a type expression: primitive, container ({@code list<T>},
   * {@code option<T>}, {@code result<T, E>}, {@code tuple<T, ...>}),
   * lifetime-qualified ({@code borrow<T>}, {@code own<T>}), or named
   * reference (record / variant / enum / flags / resource / alias).
   *
   * <p>Named references are emitted as {@code WitType.resource(name, name)}
   * placeholders when they don't match a primitive — downstream
   * translation ({@code CodeGenerator.resolveOrConvertType}) reroutes them
   * to a {@code BindgenType.reference} once the type table is materialised.
   * This mirrors what the old regex parser did for unknown names and
   * keeps the byte-identical generator output.
   *
   * @param scope currently unused; reserved for future scope-aware lookup
   *     (records-referencing-records etc.). Pass {@code null} at call sites.
   */
  private WitType parseTypeExpression(final Map<String, WitType> scope) throws BindgenException {
    final WitToken t = peek();
    if (t.kind != WitToken.Kind.IDENT) {
      throw error("expected type expression, got " + t.kind);
    }
    final String head = t.text;
    switch (head) {
      case "list":
        advance();
        expect(WitToken.Kind.LT);
        final WitType listInner = parseTypeExpression(scope);
        expect(WitToken.Kind.GT);
        return WitType.list(listInner);
      case "option":
        advance();
        expect(WitToken.Kind.LT);
        final WitType optionInner = parseTypeExpression(scope);
        expect(WitToken.Kind.GT);
        return WitType.option(optionInner);
      case "result":
        advance();
        return parseResultTail(scope);
      case "tuple":
        advance();
        expect(WitToken.Kind.LT);
        final List<WitType> elements = new ArrayList<>();
        while (peek().kind != WitToken.Kind.GT && !atEnd()) {
          elements.add(parseTypeExpression(scope));
          if (peek().kind == WitToken.Kind.COMMA) {
            advance();
          }
        }
        expect(WitToken.Kind.GT);
        return WitType.tuple(elements);
      case "borrow":
      case "own":
        advance();
        expect(WitToken.Kind.LT);
        final WitType inner = parseTypeExpression(scope);
        expect(WitToken.Kind.GT);
        // Lifetime qualifiers unwrap at the generator level.
        return inner;
      case "record":
        // Inline anonymous record — legal in a type-expression position
        // (uncommon but permitted by the WIT grammar). Use a synthetic
        // name; downstream generation names it from context.
        advance();
        return parseRecordBody("<inline-record>");
      case "variant":
        advance();
        return parseVariantBody("<inline-variant>");
      case "enum":
        advance();
        return parseEnumBody("<inline-enum>");
      case "flags":
        advance();
        return parseFlagsBody("<inline-flags>");
      default:
        break;
    }
    // Primitive lookup.
    final WitPrimitiveType prim = tryPrimitive(head);
    if (prim != null) {
      advance();
      return WitType.primitive(prim);
    }
    // Named reference — emit a resource placeholder so downstream lookup
    // can convert it against the interface's type table.
    advance();
    return WitType.resource(head, head);
  }

  private WitType parseResultTail(final Map<String, WitType> scope) throws BindgenException {
    if (peek().kind != WitToken.Kind.LT) {
      // `result` with no type parameters — both slots empty.
      return WitType.result(Optional.empty(), Optional.empty());
    }
    advance(); // consume `<`
    Optional<WitType> okType = Optional.empty();
    Optional<WitType> errType = Optional.empty();
    // First slot: either `_` (unit) or a type expression.
    if (peek().kind == WitToken.Kind.UNDERSCORE) {
      advance();
    } else if (peek().kind == WitToken.Kind.GT) {
      // `result<>` — empty tuple, both slots empty.
    } else {
      okType = Optional.of(parseTypeExpression(scope));
    }
    if (peek().kind == WitToken.Kind.COMMA) {
      advance();
      if (peek().kind == WitToken.Kind.UNDERSCORE) {
        advance();
      } else if (peek().kind != WitToken.Kind.GT) {
        errType = Optional.of(parseTypeExpression(scope));
      }
    }
    expect(WitToken.Kind.GT);
    return WitType.result(okType, errType);
  }

  private static WitPrimitiveType tryPrimitive(final String name) {
    try {
      return WitPrimitiveType.fromString(name.toLowerCase(Locale.ROOT));
    } catch (final IllegalArgumentException e) {
      return null;
    }
  }

  // ---------------------------------------------------------------------------
  // Token stream helpers
  // ---------------------------------------------------------------------------

  private WitToken peek() {
    return tokens.get(pos);
  }

  private WitToken advance() {
    final WitToken t = tokens.get(pos);
    if (pos < tokens.size() - 1) {
      pos++;
    }
    return t;
  }

  private boolean atEnd() {
    return peek().kind == WitToken.Kind.EOF;
  }

  private boolean matchesIdent(final String text) {
    final WitToken t = peek();
    return t.kind == WitToken.Kind.IDENT && t.text.equals(text);
  }

  private WitToken expect(final WitToken.Kind kind) throws BindgenException {
    final WitToken t = peek();
    if (t.kind != kind) {
      throw error("expected " + kind + " but got " + t.kind + " (\"" + t.text + "\")");
    }
    return advance();
  }

  private WitToken expectIdentToken() throws BindgenException {
    final WitToken t = peek();
    if (t.kind != WitToken.Kind.IDENT) {
      throw error("expected identifier but got " + t.kind + " (\"" + t.text + "\")");
    }
    return advance();
  }

  private void expectIdent(final String text) throws BindgenException {
    final WitToken t = peek();
    if (t.kind != WitToken.Kind.IDENT || !t.text.equals(text)) {
      throw error("expected keyword '" + text + "' but got \"" + t.text + "\"");
    }
    advance();
  }

  private BindgenException error(final String message) {
    final WitToken t = peek();
    return new BindgenException("WIT parse error at " + t.line + ":" + t.column + ": " + message);
  }
}
