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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.tegmentum.webassembly4j.bindgen.BindgenException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Native-parser suite for {@link WitParser}.
 *
 * <p>Covers every WIT construct the retired
 * {@code WitInterfaceParser} + {@code WitWorldPreprocessor} +
 * {@code WitResourceBodyParser} wrapper chain used to patch (bug 4 on the
 * bindgen bug board):
 *
 * <ul>
 *   <li>{@code package foo:bar@1.2.3;}
 *   <li>{@code world <name> { ... }} with type items directly in body
 *   <li>{@code interface <name> { ... }} inside packages or worlds
 *   <li>{@code use} clauses (skipped, don't confuse the item scanner)
 *   <li>{@code import} / {@code export} clauses (bare + inline func)
 *   <li>Resource bodies: constructor + static + instance methods, with
 *       flags carried on the emitted {@link WitFunction}
 *   <li>Type aliases and standalone record / variant / enum / flags
 *   <li>Container types: list / option / result (both operands
 *       optional) / tuple / borrow / own
 * </ul>
 *
 * <p>The suite drives {@link WitParser#parse(String)} directly and asserts
 * on the {@link WitDocument} shape so regressions on any of these
 * constructs surface here first.
 */
@DisplayName("WitParser — native WIT grammar")
class WitParserTest {

  // ---------------------------------------------------------------------------
  // package + world + interface top-level shape
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("package declaration with @version parses and is exposed")
  void packageWithVersion() throws BindgenException {
    final String src = "package wasmos:host@0.1.0;\ninterface x { }";
    final WitDocument doc = WitParser.parse(src);
    assertTrue(doc.getPackageName().isPresent(), "package name captured");
    assertEquals("wasmos:host", doc.getPackageName().get());
    assertTrue(doc.getPackageVersion().isPresent(), "package version captured");
    assertEquals("0.1.0", doc.getPackageVersion().get());
    assertEquals(1, doc.getInterfaces().size());
  }

  @Test
  @DisplayName("package without version parses")
  void packageWithoutVersion() throws BindgenException {
    final String src = "package foo:bar;\ninterface x { }";
    final WitDocument doc = WitParser.parse(src);
    assertEquals("foo:bar", doc.getPackageName().orElse(""));
    assertFalse(doc.getPackageVersion().isPresent(), "no version");
  }

  @Test
  @DisplayName("multiple interfaces in one file are surfaced in declaration order")
  void multipleInterfaces() throws BindgenException {
    final String src =
        "package p:q;\n"
            + "interface a { foo: func() -> u32; }\n"
            + "interface b { bar: func() -> u32; }\n"
            + "interface c { baz: func() -> u32; }\n";
    final WitDocument doc = WitParser.parse(src);
    assertEquals(3, doc.getInterfaces().size());
    assertEquals("a", doc.getInterfaces().get(0).getName());
    assertEquals("b", doc.getInterfaces().get(1).getName());
    assertEquals("c", doc.getInterfaces().get(2).getName());
  }

  @Test
  @DisplayName("world with type-hoisted body parses as an isWorld() entry")
  void worldWithHoistedTypes() throws BindgenException {
    final String src =
        "package p:q;\n"
            + "world root {\n"
            + "  enum color { red, green, blue }\n"
            + "  record point { x: u32, y: u32 }\n"
            + "}\n";
    final WitDocument doc = WitParser.parse(src);
    assertEquals(1, doc.getInterfaces().size());
    final WitDocument.ParsedInterface w = doc.getInterfaces().get(0);
    assertTrue(w.isWorld(), "world flag must be set");
    assertEquals(2, w.getTypes().size());
    assertTrue(w.getTypes().containsKey("color"));
    assertTrue(w.getTypes().containsKey("point"));
  }

  // ---------------------------------------------------------------------------
  // use / import / export
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("use clause is skipped without confusing subsequent items")
  void useClauseSkipped() throws BindgenException {
    final String src =
        "interface i {\n"
            + "  use foo:bar/baz.{alpha, beta};\n"
            + "  hello: func() -> string;\n"
            + "}\n";
    final WitDocument doc = WitParser.parse(src);
    final WitDocument.ParsedInterface pi = doc.getInterfaces().get(0);
    assertEquals(1, pi.getFunctions().size());
    assertTrue(pi.getFunctions().containsKey("hello"));
  }

  @Test
  @DisplayName("bare import/export interface reference is accepted")
  void bareImportExport() throws BindgenException {
    final String src =
        "world w {\n"
            + "  import some-callbacks;\n"
            + "  export some-api;\n"
            + "  record r { a: u32 }\n"
            + "}\n";
    final WitDocument doc = WitParser.parse(src);
    final WitDocument.ParsedInterface w = doc.getInterfaces().get(0);
    assertTrue(w.isWorld());
    // Bare imports/exports don't add types; the record does.
    assertEquals(1, w.getTypes().size());
    assertTrue(w.getTypes().containsKey("r"));
  }

  @Test
  @DisplayName("import name: func(...) -> ... registers the function")
  void inlineImportFunc() throws BindgenException {
    final String src =
        "world w {\n" + "  import ping: func(msg: string) -> u32;\n" + "}\n";
    final WitDocument doc = WitParser.parse(src);
    final WitDocument.ParsedInterface w = doc.getInterfaces().get(0);
    assertTrue(w.getFunctions().containsKey("ping"));
    final WitFunction fn = w.getFunctions().get("ping");
    assertEquals(1, fn.getParameters().size());
    assertEquals(1, fn.getReturnTypes().size());
  }

  // ---------------------------------------------------------------------------
  // resource bodies (constructor / static / instance)
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("resource with constructor + static + instance methods parses with flags")
  void resourceMethodsCarryFlags() throws BindgenException {
    final String src =
        "interface i {\n"
            + "  resource host-provider {\n"
            + "    constructor(name: string, count: u32);\n"
            + "    make: static func(seed: u32) -> host-provider;\n"
            + "    call: func(idx: u32) -> u32;\n"
            + "  }\n"
            + "}\n";
    final WitDocument doc = WitParser.parse(src);
    final WitDocument.ParsedInterface pi = doc.getInterfaces().get(0);
    final List<WitFunction> methods = pi.getResourceMethods().get("host-provider");
    assertNotNull(methods, "resource methods captured");
    assertEquals(3, methods.size());
    assertTrue(methods.get(0).isConstructor(), "constructor flag");
    assertFalse(methods.get(0).isStatic());
    assertFalse(methods.get(1).isConstructor());
    assertTrue(methods.get(1).isStatic(), "static flag on second method");
    assertFalse(methods.get(2).isConstructor());
    assertFalse(methods.get(2).isStatic(), "third is a plain instance method");
    // Resource itself lands in the types map with RESOURCE kind.
    assertTrue(pi.getTypes().containsKey("host-provider"));
    assertEquals(
        WitTypeCategory.RESOURCE, pi.getTypes().get("host-provider").getKind().getCategory());
  }

  @Test
  @DisplayName("empty resource {} produces an empty method list")
  void emptyResourceBody() throws BindgenException {
    final String src = "interface i { resource opaque { } }";
    final WitDocument doc = WitParser.parse(src);
    final WitDocument.ParsedInterface pi = doc.getInterfaces().get(0);
    assertTrue(pi.getResourceMethods().containsKey("opaque"));
    assertTrue(pi.getResourceMethods().get("opaque").isEmpty());
  }

  @Test
  @DisplayName("resource with no body (`resource X;`) still declares the type")
  void resourceWithoutBody() throws BindgenException {
    final String src = "interface i { resource nothing; }";
    final WitDocument doc = WitParser.parse(src);
    final WitDocument.ParsedInterface pi = doc.getInterfaces().get(0);
    assertTrue(pi.getTypes().containsKey("nothing"));
    assertEquals(
        WitTypeCategory.RESOURCE, pi.getTypes().get("nothing").getKind().getCategory());
  }

  // ---------------------------------------------------------------------------
  // type aliases + standalone declarations
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("standalone record + type-alias record both parse")
  void recordShapes() throws BindgenException {
    final String src =
        "interface i {\n"
            + "  record standalone { a: u32, b: string }\n"
            + "  type aliased = record { x: f64, y: f64 };\n"
            + "}\n";
    final WitDocument doc = WitParser.parse(src);
    final Map<String, WitType> types = doc.getInterfaces().get(0).getTypes();
    assertEquals(2, types.size());
    assertEquals(WitTypeCategory.RECORD, types.get("standalone").getKind().getCategory());
    assertEquals(WitTypeCategory.RECORD, types.get("aliased").getKind().getCategory());
  }

  @Test
  @DisplayName("variant cases with and without payload parse")
  void variantWithMixedCases() throws BindgenException {
    final String src =
        "interface i {\n"
            + "  variant msg { empty, text(string), bytes(list<u8>) }\n"
            + "}\n";
    final WitDocument doc = WitParser.parse(src);
    final WitType v = doc.getInterfaces().get(0).getTypes().get("msg");
    assertEquals(WitTypeCategory.VARIANT, v.getKind().getCategory());
    assertEquals(3, v.getKind().getVariantCases().size());
    assertFalse(v.getKind().getVariantCases().get("empty").isPresent());
    assertTrue(v.getKind().getVariantCases().get("text").isPresent());
    assertTrue(v.getKind().getVariantCases().get("bytes").isPresent());
  }

  @Test
  @DisplayName("enum and flags parse into ordered value lists")
  void enumAndFlags() throws BindgenException {
    final String src =
        "interface i {\n"
            + "  enum color { red, green, blue }\n"
            + "  flags perm { read, write, execute }\n"
            + "}\n";
    final WitDocument doc = WitParser.parse(src);
    final Map<String, WitType> types = doc.getInterfaces().get(0).getTypes();
    assertEquals(List.of("red", "green", "blue"), types.get("color").getKind().getEnumValues());
    assertEquals(
        List.of("read", "write", "execute"), types.get("perm").getKind().getFlags());
  }

  // ---------------------------------------------------------------------------
  // container types
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("list / option / result / tuple / borrow / own parse in signatures")
  void containerTypesInFunc() throws BindgenException {
    final String src =
        "interface i {\n"
            + "  resource handle { }\n"
            + "  f: func(a: list<u8>, b: option<string>, c: borrow<handle>, d: own<handle>) "
            + "-> result<tuple<u32, u32>, string>;\n"
            + "}\n";
    final WitDocument doc = WitParser.parse(src);
    final WitFunction fn = doc.getInterfaces().get(0).getFunctions().get("f");
    assertNotNull(fn, "function parsed");
    assertEquals(4, fn.getParameters().size());
    assertEquals(1, fn.getReturnTypes().size());
    final WitType retType = fn.getReturnTypes().get(0);
    assertEquals(WitTypeCategory.RESULT, retType.getKind().getCategory());
  }

  @Test
  @DisplayName("result<_, err> and result<t, _> both parse")
  void resultWithPlaceholders() throws BindgenException {
    final String src =
        "interface i {\n"
            + "  a: func() -> result<_, string>;\n"
            + "  b: func() -> result<u32, _>;\n"
            + "  c: func() -> result;\n"
            + "}\n";
    final WitDocument doc = WitParser.parse(src);
    final WitDocument.ParsedInterface pi = doc.getInterfaces().get(0);
    assertEquals(3, pi.getFunctions().size());
    for (final String name : List.of("a", "b", "c")) {
      final WitFunction fn = pi.getFunctions().get(name);
      assertEquals(WitTypeCategory.RESULT, fn.getReturnTypes().get(0).getKind().getCategory());
    }
  }

  @Test
  @DisplayName("nested list<list<u8>> and list<option<string>> parse")
  void nestedContainers() throws BindgenException {
    final String src =
        "interface i {\n"
            + "  a: func() -> list<list<u8>>;\n"
            + "  b: func() -> list<option<string>>;\n"
            + "}\n";
    final WitDocument doc = WitParser.parse(src);
    assertEquals(2, doc.getInterfaces().get(0).getFunctions().size());
  }

  // ---------------------------------------------------------------------------
  // resource + type-expression interplay
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("record field can reference a previously declared record without forward-ref")
  void recordFieldReferencesRecord() throws BindgenException {
    final String src =
        "interface i {\n"
            + "  record inner { v: u32 }\n"
            + "  record outer { part: inner }\n"
            + "}\n";
    final WitDocument doc = WitParser.parse(src);
    final WitType outer = doc.getInterfaces().get(0).getTypes().get("outer");
    assertEquals(WitTypeCategory.RECORD, outer.getKind().getCategory());
    final WitType partType = outer.getKind().getRecordFields().get("part");
    assertEquals("inner", partType.getName());
  }

  @Test
  @DisplayName("world with import embedder-callbacks; and resource + record body works end-to-end")
  void hoistedWorldWithResource() throws BindgenException {
    final String src =
        "package wasmos:host@0.1.0;\n"
            + "interface embedder-callbacks {\n"
            + "  ping: func() -> u32;\n"
            + "}\n"
            + "world embedder {\n"
            + "  enum code { ok, err }\n"
            + "  record error { code: code, message: string }\n"
            + "  resource runtime-instance {\n"
            + "    instantiate: static func(bytes: list<u8>) -> result<runtime-instance, error>;\n"
            + "    call: func(name: string, args: list<u8>) -> result<list<u8>, error>;\n"
            + "  }\n"
            + "  import embedder-callbacks;\n"
            + "}\n";
    final WitDocument doc = WitParser.parse(src);
    assertEquals(2, doc.getInterfaces().size());
    final WitDocument.ParsedInterface world = doc.getInterfaces().get(1);
    assertTrue(world.isWorld());
    assertTrue(world.getTypes().containsKey("code"));
    assertTrue(world.getTypes().containsKey("error"));
    assertTrue(world.getTypes().containsKey("runtime-instance"));
    final List<WitFunction> methods = world.getResourceMethods().get("runtime-instance");
    assertNotNull(methods);
    assertEquals(2, methods.size());
    assertTrue(methods.get(0).isStatic(), "instantiate is static");
    assertFalse(methods.get(1).isStatic());
  }

  // ---------------------------------------------------------------------------
  // determinism + errors
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("same input produces the same interface + type order across parses")
  void deterministicOutput() throws BindgenException {
    final String src =
        "interface i {\n"
            + "  enum e { a, b }\n"
            + "  record r { x: u32 }\n"
            + "  resource h { }\n"
            + "  first: func();\n"
            + "  second: func() -> u32;\n"
            + "}\n";
    final WitDocument a = WitParser.parse(src);
    final WitDocument b = WitParser.parse(src);
    assertEquals(
        List.copyOf(a.getInterfaces().get(0).getTypes().keySet()),
        List.copyOf(b.getInterfaces().get(0).getTypes().keySet()));
    assertEquals(
        List.copyOf(a.getInterfaces().get(0).getFunctions().keySet()),
        List.copyOf(b.getInterfaces().get(0).getFunctions().keySet()));
  }

  @Test
  @DisplayName("garbage input throws BindgenException")
  void garbageThrows() {
    assertThrows(
        BindgenException.class, () -> WitParser.parse("this is not valid wit source"));
  }

  @Test
  @DisplayName("line comments and doc comments are stripped without affecting parse")
  void commentsIgnored() throws BindgenException {
    final String src =
        "// leading line comment\n"
            + "package p:q;\n"
            + "/// doc comment on interface\n"
            + "interface i {\n"
            + "  // between-item comment\n"
            + "  foo: func() -> u32;\n"
            + "  /// doc on next item\n"
            + "  bar: func() -> u32;\n"
            + "}\n";
    final WitDocument doc = WitParser.parse(src);
    assertEquals(2, doc.getInterfaces().get(0).getFunctions().size());
  }

  @Test
  @DisplayName("block comments are stripped without affecting parse")
  void blockCommentsIgnored() throws BindgenException {
    final String src =
        "interface i {\n"
            + "  /* multi-line\n"
            + "     block comment */\n"
            + "  foo: func() -> u32;\n"
            + "}\n";
    final WitDocument doc = WitParser.parse(src);
    assertEquals(1, doc.getInterfaces().get(0).getFunctions().size());
  }
}
