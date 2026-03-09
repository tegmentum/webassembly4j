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

package ai.tegmentum.webassembly4j.bindgen.generator;

import ai.tegmentum.webassembly4j.bindgen.BindgenConfig;
import ai.tegmentum.webassembly4j.bindgen.BindgenException;
import ai.tegmentum.webassembly4j.bindgen.GeneratedSource;
import ai.tegmentum.webassembly4j.bindgen.model.BindgenFunction;
import ai.tegmentum.webassembly4j.bindgen.model.BindgenInterface;
import ai.tegmentum.webassembly4j.bindgen.model.BindgenType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImplementationCodeGeneratorTest {

  private BindgenConfig config;
  private ImplementationCodeGenerator generator;

  @BeforeEach
  void setUp() {
    config = BindgenConfig.builder()
        .packageName("com.example.generated")
        .outputDirectory(Path.of("target/test-output"))
        .addWitSource(Path.of("test.wit"))
        .generateImplementations(true)
        .generateServiceLoader(true)
        .build();
    generator = new ImplementationCodeGenerator(config);
  }

  @Test
  void generatePrimitiveOnlyInterface() throws BindgenException {
    BindgenInterface iface = BindgenInterface.builder()
        .name("calculator")
        .addFunction(BindgenFunction.builder()
            .name("add")
            .addParameter("a", BindgenType.primitive("i32"))
            .addParameter("b", BindgenType.primitive("i32"))
            .returnType(BindgenType.primitive("i32"))
            .build())
        .build();

    List<GeneratedSource> sources = generator.generate(Collections.singletonList(iface));

    assertEquals(2, sources.size()); // Impl + BindingProvider

    GeneratedSource implSource = sources.get(0);
    assertEquals("CalculatorImpl", implSource.getClassName());
    String content = implSource.getContent();
    assertTrue(content.contains("class CalculatorImpl"));
    assertTrue(content.contains("implements Calculator, AutoCloseable"));
    assertTrue(content.contains("addFn"));
    assertTrue(content.contains("public int add(int a, int b)"));
    assertTrue(content.contains(".intValue()"));
    // No marshal context needed for primitive-only
    assertFalse(content.contains("MarshalContext"));
  }

  @Test
  void generateBindingProvider() throws BindgenException {
    BindgenInterface iface = BindgenInterface.builder()
        .name("calculator")
        .addFunction(BindgenFunction.builder()
            .name("add")
            .addParameter("a", BindgenType.primitive("i32"))
            .addParameter("b", BindgenType.primitive("i32"))
            .returnType(BindgenType.primitive("i32"))
            .build())
        .build();

    List<GeneratedSource> sources = generator.generate(Collections.singletonList(iface));

    GeneratedSource providerSource = sources.get(1);
    assertEquals("CalculatorBindingProvider", providerSource.getClassName());
    String content = providerSource.getContent();
    assertTrue(content.contains("implements WasmBindingProvider"));
    assertTrue(content.contains("iface == Calculator.class"));
    assertTrue(content.contains("new CalculatorImpl(instance, module, engine)"));
    assertTrue(content.contains("@SuppressWarnings(\"unchecked\")"));
    assertTrue(content.contains("<T> T create"));
  }

  @Test
  void generateStringInterface() throws BindgenException {
    BindgenInterface iface = BindgenInterface.builder()
        .name("greeter")
        .addFunction(BindgenFunction.builder()
            .name("greet")
            .addParameter("name", BindgenType.primitive("string"))
            .returnType(BindgenType.primitive("string"))
            .build())
        .build();

    List<GeneratedSource> sources = generator.generate(Collections.singletonList(iface));

    GeneratedSource implSource = sources.get(0);
    String content = implSource.getContent();
    assertTrue(content.contains("MarshalContext"));
    assertTrue(content.contains("StringCodec.encode"));
    assertTrue(content.contains("marshal.reader().readString"));
    assertTrue(content.contains("retptr"));
  }

  @Test
  void generateVoidFunction() throws BindgenException {
    BindgenInterface iface = BindgenInterface.builder()
        .name("logger")
        .addFunction(BindgenFunction.builder()
            .name("log")
            .addParameter("message", BindgenType.primitive("string"))
            .build())
        .build();

    List<GeneratedSource> sources = generator.generate(Collections.singletonList(iface));

    GeneratedSource implSource = sources.get(0);
    String content = implSource.getContent();
    assertTrue(content.contains("public void log("));
    assertTrue(content.contains("MarshalContext"));
    // Void return, no retptr needed
    assertFalse(content.contains("retptr"));
  }

  @Test
  void generateMixedParametersInterface() throws BindgenException {
    BindgenInterface iface = BindgenInterface.builder()
        .name("store")
        .addFunction(BindgenFunction.builder()
            .name("set")
            .addParameter("key", BindgenType.primitive("string"))
            .addParameter("value", BindgenType.primitive("i32"))
            .build())
        .build();

    List<GeneratedSource> sources = generator.generate(Collections.singletonList(iface));

    GeneratedSource implSource = sources.get(0);
    String content = implSource.getContent();
    assertTrue(content.contains("MarshalContext"));
    assertTrue(content.contains("StringCodec.encode"));
    assertTrue(content.contains("args.add(value)"));
  }

  @Test
  void generateBooleanReturnType() throws BindgenException {
    BindgenInterface iface = BindgenInterface.builder()
        .name("validator")
        .addFunction(BindgenFunction.builder()
            .name("is-valid")
            .addParameter("input", BindgenType.primitive("i32"))
            .returnType(BindgenType.primitive("bool"))
            .build())
        .build();

    List<GeneratedSource> sources = generator.generate(Collections.singletonList(iface));

    GeneratedSource implSource = sources.get(0);
    String content = implSource.getContent();
    assertTrue(content.contains("public boolean isValid(int input)"));
    assertTrue(content.contains("!= 0"));
  }

  @Test
  void generateMultipleFunctions() throws BindgenException {
    BindgenInterface iface = BindgenInterface.builder()
        .name("math")
        .addFunction(BindgenFunction.builder()
            .name("add")
            .addParameter("a", BindgenType.primitive("i32"))
            .addParameter("b", BindgenType.primitive("i32"))
            .returnType(BindgenType.primitive("i32"))
            .build())
        .addFunction(BindgenFunction.builder()
            .name("multiply")
            .addParameter("a", BindgenType.primitive("i64"))
            .addParameter("b", BindgenType.primitive("i64"))
            .returnType(BindgenType.primitive("i64"))
            .build())
        .build();

    List<GeneratedSource> sources = generator.generate(Collections.singletonList(iface));

    GeneratedSource implSource = sources.get(0);
    String content = implSource.getContent();
    assertTrue(content.contains("addFn"));
    assertTrue(content.contains("multiplyFn"));
    assertTrue(content.contains("public int add(int a, int b)"));
    assertTrue(content.contains("public long multiply(long a, long b)"));
  }

  @Test
  void generateWithoutServiceLoader() throws BindgenException {
    BindgenConfig noServiceLoader = BindgenConfig.builder()
        .packageName("com.example.generated")
        .outputDirectory(Path.of("target/test-output"))
        .addWitSource(Path.of("test.wit"))
        .generateImplementations(true)
        .generateServiceLoader(false)
        .build();

    ImplementationCodeGenerator gen = new ImplementationCodeGenerator(noServiceLoader);
    BindgenInterface iface = BindgenInterface.builder()
        .name("calculator")
        .addFunction(BindgenFunction.builder()
            .name("add")
            .addParameter("a", BindgenType.primitive("i32"))
            .addParameter("b", BindgenType.primitive("i32"))
            .returnType(BindgenType.primitive("i32"))
            .build())
        .build();

    List<GeneratedSource> sources = gen.generate(Collections.singletonList(iface));
    assertEquals(1, sources.size()); // Only Impl, no BindingProvider
    assertEquals("CalculatorImpl", sources.get(0).getClassName());
  }

  @Test
  void generateMultipleInterfaces() throws BindgenException {
    BindgenInterface calc = BindgenInterface.builder()
        .name("calculator")
        .addFunction(BindgenFunction.builder()
            .name("add")
            .addParameter("a", BindgenType.primitive("i32"))
            .addParameter("b", BindgenType.primitive("i32"))
            .returnType(BindgenType.primitive("i32"))
            .build())
        .build();

    BindgenInterface greeter = BindgenInterface.builder()
        .name("greeter")
        .addFunction(BindgenFunction.builder()
            .name("greet")
            .addParameter("name", BindgenType.primitive("string"))
            .returnType(BindgenType.primitive("string"))
            .build())
        .build();

    List<GeneratedSource> sources = generator.generate(Arrays.asList(calc, greeter));
    assertEquals(4, sources.size()); // 2 Impls + 2 BindingProviders

    List<String> classNames = new java.util.ArrayList<>();
    for (GeneratedSource src : sources) {
      classNames.add(src.getClassName());
    }
    assertTrue(classNames.contains("CalculatorImpl"));
    assertTrue(classNames.contains("CalculatorBindingProvider"));
    assertTrue(classNames.contains("GreeterImpl"));
    assertTrue(classNames.contains("GreeterBindingProvider"));
  }

  @Test
  void implHasCloseMethod() throws BindgenException {
    BindgenInterface iface = BindgenInterface.builder()
        .name("calculator")
        .addFunction(BindgenFunction.builder()
            .name("add")
            .addParameter("a", BindgenType.primitive("i32"))
            .addParameter("b", BindgenType.primitive("i32"))
            .returnType(BindgenType.primitive("i32"))
            .build())
        .build();

    GeneratedSource implSource = generator.generateImpl(iface);
    String content = implSource.getContent();
    assertTrue(content.contains("public void close()"));
    assertTrue(content.contains("module.close()"));
    assertTrue(content.contains("engine.close()"));
  }

  @Test
  void constructorLooksFunctionsUp() throws BindgenException {
    BindgenInterface iface = BindgenInterface.builder()
        .name("calculator")
        .addFunction(BindgenFunction.builder()
            .name("add")
            .addParameter("a", BindgenType.primitive("i32"))
            .addParameter("b", BindgenType.primitive("i32"))
            .returnType(BindgenType.primitive("i32"))
            .build())
        .build();

    GeneratedSource implSource = generator.generateImpl(iface);
    String content = implSource.getContent();
    assertTrue(content.contains("instance.function(\"add\")"));
    assertTrue(content.contains("orElseThrow"));
  }

  @Test
  void marshallingStrategyIdentifiesPrimitives() {
    assertFalse(MarshallingStrategy.requiresMarshalling(BindgenType.primitive("i32")));
    assertFalse(MarshallingStrategy.requiresMarshalling(BindgenType.primitive("i64")));
    assertFalse(MarshallingStrategy.requiresMarshalling(BindgenType.primitive("f32")));
    assertFalse(MarshallingStrategy.requiresMarshalling(BindgenType.primitive("bool")));
    assertFalse(MarshallingStrategy.requiresMarshalling(null));
  }

  @Test
  void marshallingStrategyIdentifiesComplexTypes() {
    assertTrue(MarshallingStrategy.requiresMarshalling(BindgenType.primitive("string")));
    assertTrue(MarshallingStrategy.requiresMarshalling(
        BindgenType.list(BindgenType.primitive("u8"))));
  }

  @Test
  void getProviderClassNames() {
    BindgenInterface calc = BindgenInterface.builder()
        .name("calculator")
        .addFunction(BindgenFunction.builder()
            .name("add")
            .addParameter("a", BindgenType.primitive("i32"))
            .addParameter("b", BindgenType.primitive("i32"))
            .returnType(BindgenType.primitive("i32"))
            .build())
        .build();

    BindgenInterface greeter = BindgenInterface.builder()
        .name("greeter")
        .addFunction(BindgenFunction.builder()
            .name("greet")
            .addParameter("name", BindgenType.primitive("string"))
            .returnType(BindgenType.primitive("string"))
            .build())
        .build();

    List<String> names = generator.getProviderClassNames(Arrays.asList(calc, greeter));
    assertEquals(2, names.size());
    assertEquals("com.example.generated.CalculatorBindingProvider", names.get(0));
    assertEquals("com.example.generated.GreeterBindingProvider", names.get(1));
  }

  @Test
  void writeServiceLoaderFile() throws BindgenException, IOException {
    Path tempDir = Files.createTempDirectory("bindgen-test");
    try {
      BindgenInterface calc = BindgenInterface.builder()
          .name("calculator")
          .addFunction(BindgenFunction.builder()
              .name("add")
              .addParameter("a", BindgenType.primitive("i32"))
              .addParameter("b", BindgenType.primitive("i32"))
              .returnType(BindgenType.primitive("i32"))
              .build())
          .build();

      BindgenInterface greeter = BindgenInterface.builder()
          .name("greeter")
          .addFunction(BindgenFunction.builder()
              .name("greet")
              .addParameter("name", BindgenType.primitive("string"))
              .returnType(BindgenType.primitive("string"))
              .build())
          .build();

      generator.writeServiceLoaderFile(tempDir, Arrays.asList(calc, greeter));

      Path servicesFile = tempDir
          .resolve("META-INF")
          .resolve("services")
          .resolve("ai.tegmentum.webassembly4j.runtime.spi.WasmBindingProvider");
      assertTrue(Files.exists(servicesFile));

      String content = Files.readString(servicesFile);
      assertTrue(content.contains("com.example.generated.CalculatorBindingProvider"));
      assertTrue(content.contains("com.example.generated.GreeterBindingProvider"));

      // Each entry should be on its own line
      String[] lines = content.trim().split("\n");
      assertEquals(2, lines.length);
    } finally {
      // Clean up temp files
      deleteRecursively(tempDir);
    }
  }

  @Test
  void writeServiceLoaderFileEmptyList() throws BindgenException, IOException {
    Path tempDir = Files.createTempDirectory("bindgen-test");
    try {
      generator.writeServiceLoaderFile(tempDir, Collections.emptyList());

      Path servicesDir = tempDir.resolve("META-INF").resolve("services");
      assertFalse(Files.exists(servicesDir));
    } finally {
      deleteRecursively(tempDir);
    }
  }

  private static void deleteRecursively(Path path) throws IOException {
    if (Files.isDirectory(path)) {
      Files.list(path).forEach(child -> {
        try {
          deleteRecursively(child);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      });
    }
    Files.deleteIfExists(path);
  }

  @Test
  void generateFloatReturnType() throws BindgenException {
    BindgenInterface iface = BindgenInterface.builder()
        .name("math")
        .addFunction(BindgenFunction.builder()
            .name("pi")
            .returnType(BindgenType.primitive("f64"))
            .build())
        .build();

    GeneratedSource implSource = generator.generateImpl(iface);
    String content = implSource.getContent();
    assertTrue(content.contains("public double pi()"));
    assertTrue(content.contains(".doubleValue()"));
  }
}
