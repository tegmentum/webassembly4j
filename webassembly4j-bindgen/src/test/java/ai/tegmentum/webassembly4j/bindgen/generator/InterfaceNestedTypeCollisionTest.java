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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.tegmentum.webassembly4j.bindgen.BindgenConfig;
import ai.tegmentum.webassembly4j.bindgen.BindgenException;
import ai.tegmentum.webassembly4j.bindgen.CodeStyle;
import ai.tegmentum.webassembly4j.bindgen.GeneratedSource;
import ai.tegmentum.webassembly4j.bindgen.model.BindgenField;
import ai.tegmentum.webassembly4j.bindgen.model.BindgenFunction;
import ai.tegmentum.webassembly4j.bindgen.model.BindgenInterface;
import ai.tegmentum.webassembly4j.bindgen.model.BindgenModel;
import ai.tegmentum.webassembly4j.bindgen.model.BindgenType;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regression coverage for the bindgen bug where WIT permits (but Java
 * forbids) an interface and a nested type sharing the same identifier.
 *
 * <p>Live example: {@code wasmos:runtime/provider-manifest.wit} declares
 * {@code interface provider-manifest { record provider-manifest {...} }}.
 * Both compile to {@code ProviderManifest.java} in the same target Java
 * package. The generator wrote the interface, then the record overwrote
 * it, and the emitted {@code ProviderManifestImpl implements
 * ProviderManifest} failed to compile with "interface expected here"
 * because {@code ProviderManifest} was now a class, not an interface.
 *
 * <p>Resolution — {@link JavaCodeGenerator#shouldSkipInterface}:
 *
 * <ul>
 *   <li>Interface has zero functions: emit the nested types, drop the
 *       empty interface + its Impl + its BindingProvider. The interface
 *       was effectively a namespace; nothing user-visible is lost.
 *   <li>Interface has functions: throw {@link BindgenException}. Silently
 *       dropping methods would delete API. The message directs the user
 *       to rename either identifier in the WIT source.
 * </ul>
 */
@DisplayName("Interface / nested-type name-collision handling")
class InterfaceNestedTypeCollisionTest {

  private static final String PACKAGE = "ai.tegmentum.bindgen.generated.collision";

  private static BindgenConfig config() {
    return BindgenConfig.builder()
        .packageName(PACKAGE)
        .outputDirectory(Path.of("target/test-output"))
        .codeStyle(CodeStyle.MODERN)
        .addWitSource(Path.of("test.wit"))
        .generateImplementations(true)
        .generateServiceLoader(true)
        .build();
  }

  /** Build the {@code interface provider-manifest { record provider-manifest {...} }} shape. */
  private static BindgenInterface collidingInterface(boolean withFunction) {
    final BindgenType record =
        BindgenType.builder()
            .name("provider-manifest")
            .kind(BindgenType.Kind.RECORD)
            .addField(new BindgenField("priority", BindgenType.primitive("u32")))
            .build();
    final BindgenInterface.Builder builder =
        BindgenInterface.builder().name("provider-manifest").addType(record);
    if (withFunction) {
      builder.addFunction(
          BindgenFunction.builder()
              .name("dummy")
              .returnType(BindgenType.primitive("u32"))
              .build());
    }
    return builder.build();
  }

  @Test
  @DisplayName(
      "namespace-shaped interface (no functions) with a colliding nested record: emit the record,"
          + " drop the interface + Impl + BindingProvider")
  void collidingNamespaceInterfaceIsSkipped() throws Exception {
    final BindgenModel model =
        BindgenModel.builder().addInterface(collidingInterface(/* withFunction= */ false)).build();

    final ModernCodeGenerator generator = new ModernCodeGenerator(config());
    final List<GeneratedSource> sources = generator.generate(model);

    // Bucket by class name so we can assert presence/absence.
    final Map<String, GeneratedSource> byName = new HashMap<>();
    for (final GeneratedSource source : sources) {
      // Class-name collisions between separate GeneratedSources land in the
      // same bucket; overwrites here mirror the on-disk overwrite pattern
      // that the pre-fix generator produced.
      byName.put(source.getClassName(), source);
    }

    // Positive pin — the record is still emitted.
    assertTrue(
        byName.containsKey("ProviderManifest"),
        "the nested record must still be generated: " + byName.keySet());
    assertTrue(
        byName.get("ProviderManifest").getContent().contains("private final int priority"),
        "expected the record class body (priority field), got:\n"
            + byName.get("ProviderManifest").getContent());

    // Negative pins — Impl + BindingProvider both drop because the parent
    // interface was skipped (nothing to implement).
    assertFalse(
        byName.containsKey("ProviderManifestImpl"),
        "the empty-namespace interface must not emit an Impl: " + byName.keySet());
    assertFalse(
        byName.containsKey("ProviderManifestBindingProvider"),
        "the empty-namespace interface must not emit a BindingProvider: " + byName.keySet());

    // Only ONE source with class name ProviderManifest — the record wins
    // the slot and the interface is dropped entirely (no overwrite race).
    int count = 0;
    for (final GeneratedSource source : sources) {
      if ("ProviderManifest".equals(source.getClassName())) {
        count++;
      }
    }
    assertEquals(
        1,
        count,
        "expected exactly one ProviderManifest source (the record), got: " + count);
  }

  @Test
  @DisplayName(
      "interface with functions and a colliding nested record: hard-fail (rename required)")
  void collidingInterfaceWithFunctionsThrows() {
    final BindgenModel model =
        BindgenModel.builder().addInterface(collidingInterface(/* withFunction= */ true)).build();

    final ModernCodeGenerator generator;
    try {
      generator = new ModernCodeGenerator(config());
    } catch (final Exception e) {
      throw new AssertionError("generator construction should not throw", e);
    }
    final BindgenException e =
        assertThrows(BindgenException.class, () -> generator.generate(model));
    final String msg = e.getMessage();
    assertNotNull(msg, "the exception must carry a message");
    // The message must name the interface, the collision, and steer the
    // user to a fix (rename either identifier in WIT).
    assertTrue(
        msg.contains("provider-manifest"),
        "expected the WIT identifier in the error message, got: " + msg);
    assertTrue(
        msg.contains("Rename") || msg.contains("rename"),
        "expected the error to direct the user to rename, got: " + msg);
  }

  @Test
  @DisplayName("non-colliding interface + nested record: both emit normally (baseline)")
  void nonCollidingInterfaceStillGenerates() throws Exception {
    // Distinct identifiers — same shape as the colliding case but the
    // record's name differs. Confirms the skip logic only fires on real
    // collisions and doesn't regress the common case.
    final BindgenType record =
        BindgenType.builder()
            .name("manifest-body")
            .kind(BindgenType.Kind.RECORD)
            .addField(new BindgenField("priority", BindgenType.primitive("u32")))
            .build();
    final BindgenInterface iface =
        BindgenInterface.builder().name("provider-manifest").addType(record).build();
    final BindgenModel model = BindgenModel.builder().addInterface(iface).build();

    final ModernCodeGenerator generator = new ModernCodeGenerator(config());
    final List<GeneratedSource> sources = generator.generate(model);

    final Map<String, GeneratedSource> byName = new HashMap<>();
    for (final GeneratedSource source : sources) {
      byName.put(source.getClassName(), source);
    }
    assertTrue(byName.containsKey("ProviderManifest"), "interface still emits");
    assertTrue(byName.containsKey("ManifestBody"), "record still emits");
    assertTrue(byName.containsKey("ProviderManifestBindingProvider"), "provider still emits");
  }
}
