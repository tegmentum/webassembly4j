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
package ai.tegmentum.webassembly4j.bindgen.generator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.tegmentum.webassembly4j.bindgen.BindgenConfig;
import ai.tegmentum.webassembly4j.bindgen.CodeGenerator;
import ai.tegmentum.webassembly4j.bindgen.CodeStyle;
import ai.tegmentum.webassembly4j.bindgen.GeneratedSource;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regression: a record field with a deeply-nested container type — for
 * example {@code list<tuple<string, list<u8>>>}, the shape wasmos:runtime
 * uses for HTTP/WebSocket headers — must have every inner container mapped
 * through the type mapper. Before the fix, TUPLE was not handled by
 * {@code JavaCodeGenerator.mapType}: it fell through to the REFERENCE
 * default, which handed {@code mapGeneratedType} the raw WIT text
 * {@code "tuple<string, list<u8>>"} — JavaPoet embedded that verbatim as
 * {@code Tuple<string, list<u8>>}, and the generated code failed to
 * compile with {@code symbol: class u8} / {@code symbol: class string}.
 *
 * <p>The fix threads TUPLE through {@code TypeMappingRegistry.mapTuple},
 * which now emits parameterised {@code java.util.Map.Entry<K, V>} for
 * 2-tuples (JDK type, self-contained) and a parameterised {@code TupleN}
 * placeholder for larger tuples.
 */
@DisplayName("bindgen record fields with deeply-nested container types")
class NestedContainerFieldTest {

  private static final String PACKAGE = "com.example.fixture";

  private static Path tupleWit;

  @BeforeAll
  static void locateFixture() throws URISyntaxException {
    final URL resource =
        NestedContainerFieldTest.class.getClassLoader().getResource("tuple-fixture/tuple.wit");
    assertNotNull(resource, "tuple-fixture/tuple.wit missing from test resources");
    tupleWit = Path.of(URLDecoder.decode(resource.toURI().getPath(), StandardCharsets.UTF_8));
  }

  @Test
  @DisplayName(
      "record field list<tuple<string, list<u8>>> generates real Java, not raw WIT text")
  void nestedListTupleListRecordFieldGeneratesValidJava(@TempDir Path outputDir) throws Exception {
    final BindgenConfig config =
        BindgenConfig.builder()
            .packageName(PACKAGE)
            .outputDirectory(outputDir)
            .codeStyle(CodeStyle.MODERN)
            .addWitSource(tupleWit)
            .generateImplementations(false)
            .generateServiceLoader(false)
            .build();

    final CodeGenerator generator = new CodeGenerator(config);
    final List<GeneratedSource> sources = generator.generate();

    final Map<String, String> byClass = new HashMap<>();
    for (final GeneratedSource source : sources) {
      byClass.put(source.getClassName(), source.getContent());
    }

    assertTrue(byClass.containsKey("Request"), "Request record must be generated");
    final String request = byClass.get("Request");

    // Regression pins: none of these raw WIT tokens should leak into Java
    // source. Before the fix `Tuple<string, list<u8>>` appeared verbatim
    // as the field type on the emitted record.
    assertFalse(
        request.contains("Tuple<string"),
        "raw WIT type text leaked into generated Java (Tuple<string, ...>):\n" + request);
    assertFalse(
        request.contains("list<u8>"),
        "raw WIT container text leaked into generated Java (list<u8>):\n" + request);
    assertFalse(
        request.contains(" string "),
        "lowercase primitive `string` leaked as an identifier — should be java.lang.String:\n"
            + request);
    assertFalse(
        request.contains(" u8 "),
        "primitive `u8` leaked as an identifier — should be byte / java.lang.Byte:\n" + request);

    // Positive pin: the field's Java type must be a real parameterised
    // List<Map.Entry<String, List<Byte>>>.  We match on the boxed form
    // because record fields box their generic params.
    assertTrue(
        request.contains("List<Entry<String, List<Byte>>>")
            || request.contains("List<Map.Entry<String, List<Byte>>>"),
        "expected List<Map.Entry<String, List<Byte>>> field type, got:\n" + request);
  }
}
