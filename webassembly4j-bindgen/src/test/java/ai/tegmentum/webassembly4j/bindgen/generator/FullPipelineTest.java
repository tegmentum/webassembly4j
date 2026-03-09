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
import ai.tegmentum.webassembly4j.bindgen.CodeStyle;
import ai.tegmentum.webassembly4j.bindgen.GeneratedSource;
import ai.tegmentum.webassembly4j.bindgen.model.BindgenFunction;
import ai.tegmentum.webassembly4j.bindgen.model.BindgenInterface;
import ai.tegmentum.webassembly4j.bindgen.model.BindgenModel;
import ai.tegmentum.webassembly4j.bindgen.model.BindgenType;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end tests validating the full bindgen pipeline:
 * BindgenModel → JavaCodeGenerator → interfaces + Impl classes + BindingProviders.
 */
class FullPipelineTest {

  /**
   * Full pipeline: primitive-only calculator interface.
   * Generates: Calculator (interface), CalculatorImpl, CalculatorBindingProvider.
   */
  @Test
  void primitiveOnlyInterface() throws BindgenException {
    BindgenConfig config = BindgenConfig.builder()
        .packageName("com.example")
        .outputDirectory(Path.of("target/test-output"))
        .codeStyle(CodeStyle.MODERN)
        .addWitSource(Path.of("test.wit"))
        .generateImplementations(true)
        .generateServiceLoader(true)
        .build();

    BindgenModel model = BindgenModel.builder()
        .name("test")
        .addInterface(BindgenInterface.builder()
            .name("calculator")
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
            .build())
        .build();

    ModernCodeGenerator generator = new ModernCodeGenerator(config);
    List<GeneratedSource> sources = generator.generate(model);

    Map<String, String> sourceMap = toSourceMap(sources);

    // Interface should be generated
    assertTrue(sourceMap.containsKey("Calculator"));
    String iface = sourceMap.get("Calculator");
    assertTrue(iface.contains("public interface Calculator"));
    assertTrue(iface.contains("int add(int a, int b)"));
    assertTrue(iface.contains("long multiply(long a, long b)"));

    // Impl should be generated
    assertTrue(sourceMap.containsKey("CalculatorImpl"));
    String impl = sourceMap.get("CalculatorImpl");
    assertTrue(impl.contains("class CalculatorImpl implements Calculator, AutoCloseable"));
    assertTrue(impl.contains("instance.function(\"add\")"));
    assertTrue(impl.contains("instance.function(\"multiply\")"));
    assertTrue(impl.contains("public int add(int a, int b)"));
    assertTrue(impl.contains("public long multiply(long a, long b)"));
    assertTrue(impl.contains(".intValue()"));
    assertTrue(impl.contains(".longValue()"));
    assertTrue(impl.contains("public void close()"));
    // No marshalling needed
    assertTrue(!impl.contains("MarshalContext"));

    // BindingProvider should be generated
    assertTrue(sourceMap.containsKey("CalculatorBindingProvider"));
    String provider = sourceMap.get("CalculatorBindingProvider");
    assertTrue(provider.contains("implements WasmBindingProvider"));
    assertTrue(provider.contains("iface == Calculator.class"));
    assertTrue(provider.contains("new CalculatorImpl(instance, module, engine)"));
  }

  /**
   * Full pipeline: string-based greeter interface.
   * Generates marshalling code for String parameters and return types.
   */
  @Test
  void stringInterface() throws BindgenException {
    BindgenConfig config = BindgenConfig.builder()
        .packageName("com.example")
        .outputDirectory(Path.of("target/test-output"))
        .codeStyle(CodeStyle.MODERN)
        .addWitSource(Path.of("test.wit"))
        .build();

    BindgenModel model = BindgenModel.builder()
        .name("test")
        .addInterface(BindgenInterface.builder()
            .name("greeter")
            .addFunction(BindgenFunction.builder()
                .name("greet")
                .addParameter("name", BindgenType.primitive("string"))
                .returnType(BindgenType.primitive("string"))
                .build())
            .build())
        .build();

    ModernCodeGenerator generator = new ModernCodeGenerator(config);
    List<GeneratedSource> sources = generator.generate(model);

    Map<String, String> sourceMap = toSourceMap(sources);

    // Interface
    assertTrue(sourceMap.containsKey("Greeter"));
    String iface = sourceMap.get("Greeter");
    assertTrue(iface.contains("String greet(String name)"));

    // Impl with marshalling
    assertTrue(sourceMap.containsKey("GreeterImpl"));
    String impl = sourceMap.get("GreeterImpl");
    assertTrue(impl.contains("MarshalContext"));
    assertTrue(impl.contains("StringCodec.encode"));
    assertTrue(impl.contains("marshal.reader().readString"));
    assertTrue(impl.contains("retptr"));
    assertTrue(impl.contains("marshal.allocator().allocate(8, 4)"));
  }

  /**
   * Full pipeline: mixed interface with both primitives and strings.
   */
  @Test
  void mixedInterface() throws BindgenException {
    BindgenConfig config = BindgenConfig.builder()
        .packageName("com.example")
        .outputDirectory(Path.of("target/test-output"))
        .codeStyle(CodeStyle.MODERN)
        .addWitSource(Path.of("test.wit"))
        .build();

    BindgenModel model = BindgenModel.builder()
        .name("test")
        .addInterface(BindgenInterface.builder()
            .name("key-value-store")
            .addFunction(BindgenFunction.builder()
                .name("get")
                .addParameter("key", BindgenType.primitive("string"))
                .returnType(BindgenType.primitive("string"))
                .build())
            .addFunction(BindgenFunction.builder()
                .name("set")
                .addParameter("key", BindgenType.primitive("string"))
                .addParameter("value", BindgenType.primitive("string"))
                .build())
            .addFunction(BindgenFunction.builder()
                .name("size")
                .returnType(BindgenType.primitive("i32"))
                .build())
            .build())
        .build();

    ModernCodeGenerator generator = new ModernCodeGenerator(config);
    List<GeneratedSource> sources = generator.generate(model);

    Map<String, String> sourceMap = toSourceMap(sources);

    String impl = sourceMap.get("KeyValueStoreImpl");
    assertNotNull(impl, "KeyValueStoreImpl should be generated");

    // Has MarshalContext because some functions need it
    assertTrue(impl.contains("MarshalContext"));

    // get() needs marshalling
    assertTrue(impl.contains("String get(String key)"));

    // set() is void with string params
    assertTrue(impl.contains("void set(String key, String value)"));

    // size() is primitive-only
    assertTrue(impl.contains("int size()"));
  }

  /**
   * Full pipeline: multiple interfaces in one model.
   */
  @Test
  void multipleInterfaces() throws BindgenException {
    BindgenConfig config = BindgenConfig.builder()
        .packageName("com.example")
        .outputDirectory(Path.of("target/test-output"))
        .codeStyle(CodeStyle.MODERN)
        .addWitSource(Path.of("test.wit"))
        .build();

    BindgenModel model = BindgenModel.builder()
        .name("test")
        .addInterface(BindgenInterface.builder()
            .name("math")
            .addFunction(BindgenFunction.builder()
                .name("add")
                .addParameter("a", BindgenType.primitive("f64"))
                .addParameter("b", BindgenType.primitive("f64"))
                .returnType(BindgenType.primitive("f64"))
                .build())
            .build())
        .addInterface(BindgenInterface.builder()
            .name("logger")
            .addFunction(BindgenFunction.builder()
                .name("log")
                .addParameter("message", BindgenType.primitive("string"))
                .build())
            .build())
        .build();

    ModernCodeGenerator generator = new ModernCodeGenerator(config);
    List<GeneratedSource> sources = generator.generate(model);

    Map<String, String> sourceMap = toSourceMap(sources);

    // 2 interfaces + 2 impls + 2 providers = 6
    assertEquals(6, sources.size());

    assertTrue(sourceMap.containsKey("Math"));
    assertTrue(sourceMap.containsKey("MathImpl"));
    assertTrue(sourceMap.containsKey("MathBindingProvider"));
    assertTrue(sourceMap.containsKey("Logger"));
    assertTrue(sourceMap.containsKey("LoggerImpl"));
    assertTrue(sourceMap.containsKey("LoggerBindingProvider"));

    // Math should not need marshalling
    assertTrue(!sourceMap.get("MathImpl").contains("MarshalContext"));

    // Logger needs marshalling for string param
    assertTrue(sourceMap.get("LoggerImpl").contains("MarshalContext"));
  }

  /**
   * Full pipeline with implementations disabled.
   */
  @Test
  void implementationsDisabled() throws BindgenException {
    BindgenConfig config = BindgenConfig.builder()
        .packageName("com.example")
        .outputDirectory(Path.of("target/test-output"))
        .codeStyle(CodeStyle.MODERN)
        .addWitSource(Path.of("test.wit"))
        .generateImplementations(false)
        .build();

    BindgenModel model = BindgenModel.builder()
        .name("test")
        .addInterface(BindgenInterface.builder()
            .name("calculator")
            .addFunction(BindgenFunction.builder()
                .name("add")
                .addParameter("a", BindgenType.primitive("i32"))
                .addParameter("b", BindgenType.primitive("i32"))
                .returnType(BindgenType.primitive("i32"))
                .build())
            .build())
        .build();

    ModernCodeGenerator generator = new ModernCodeGenerator(config);
    List<GeneratedSource> sources = generator.generate(model);

    // Only the interface, no impl or provider
    assertEquals(1, sources.size());
    assertEquals("Calculator", sources.get(0).getClassName());
  }

  /**
   * Full pipeline: enum parameter function.
   */
  @Test
  void enumInterface() throws BindgenException {
    BindgenConfig config = BindgenConfig.builder()
        .packageName("com.example")
        .outputDirectory(Path.of("target/test-output"))
        .codeStyle(CodeStyle.MODERN)
        .addWitSource(Path.of("test.wit"))
        .build();

    BindgenType colorEnum = BindgenType.builder()
        .name("color")
        .kind(BindgenType.Kind.ENUM)
        .addEnumValue("red")
        .addEnumValue("green")
        .addEnumValue("blue")
        .build();

    BindgenModel model = BindgenModel.builder()
        .name("test")
        .addType(colorEnum)
        .addInterface(BindgenInterface.builder()
            .name("painter")
            .addFunction(BindgenFunction.builder()
                .name("paint")
                .addParameter("color", BindgenType.reference("color"))
                .returnType(BindgenType.primitive("bool"))
                .build())
            .build())
        .build();

    ModernCodeGenerator generator = new ModernCodeGenerator(config);
    List<GeneratedSource> sources = generator.generate(model);

    Map<String, String> sourceMap = toSourceMap(sources);

    assertTrue(sourceMap.containsKey("Color"));
    assertTrue(sourceMap.containsKey("Painter"));
    assertTrue(sourceMap.containsKey("PainterImpl"));
    assertTrue(sourceMap.containsKey("PainterBindingProvider"));
  }

  /**
   * Full pipeline with LEGACY code style still generates impls.
   */
  @Test
  void legacyCodeStyleGeneratesImpls() throws BindgenException {
    BindgenConfig config = BindgenConfig.builder()
        .packageName("com.example")
        .outputDirectory(Path.of("target/test-output"))
        .codeStyle(CodeStyle.LEGACY)
        .addWitSource(Path.of("test.wit"))
        .build();

    BindgenModel model = BindgenModel.builder()
        .name("test")
        .addInterface(BindgenInterface.builder()
            .name("calculator")
            .addFunction(BindgenFunction.builder()
                .name("add")
                .addParameter("a", BindgenType.primitive("i32"))
                .addParameter("b", BindgenType.primitive("i32"))
                .returnType(BindgenType.primitive("i32"))
                .build())
            .build())
        .build();

    LegacyCodeGenerator generator = new LegacyCodeGenerator(config);
    List<GeneratedSource> sources = generator.generate(model);

    Map<String, String> sourceMap = toSourceMap(sources);

    assertTrue(sourceMap.containsKey("Calculator"));
    assertTrue(sourceMap.containsKey("CalculatorImpl"));
    assertTrue(sourceMap.containsKey("CalculatorBindingProvider"));
  }

  private Map<String, String> toSourceMap(List<GeneratedSource> sources) {
    Map<String, String> map = new HashMap<>();
    for (GeneratedSource src : sources) {
      map.put(src.getClassName(), src.getContent());
    }
    return map;
  }
}
