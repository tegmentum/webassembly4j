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

package ai.tegmentum.webassembly4j.bindgen;

import ai.tegmentum.webassembly4j.bindgen.generator.ImplementationCodeGenerator;
import ai.tegmentum.webassembly4j.bindgen.generator.JavaCodeGenerator;
import ai.tegmentum.webassembly4j.bindgen.generator.LegacyCodeGenerator;
import ai.tegmentum.webassembly4j.bindgen.generator.ModernCodeGenerator;
import ai.tegmentum.webassembly4j.bindgen.model.BindgenField;
import ai.tegmentum.webassembly4j.bindgen.model.BindgenFunction;
import ai.tegmentum.webassembly4j.bindgen.model.BindgenInterface;
import ai.tegmentum.webassembly4j.bindgen.model.BindgenModel;
import ai.tegmentum.webassembly4j.bindgen.model.BindgenParameter;
import ai.tegmentum.webassembly4j.bindgen.model.BindgenType;
import ai.tegmentum.webassembly4j.bindgen.model.BindgenVariantCase;
import ai.tegmentum.webassembly4j.bindgen.wit.WitInterfaceParser;
import ai.tegmentum.webassembly4j.bindgen.wit.WitFunction;
import ai.tegmentum.webassembly4j.bindgen.wit.WitInterfaceDefinition;
import ai.tegmentum.webassembly4j.bindgen.wit.WitParameter;
import ai.tegmentum.webassembly4j.bindgen.wit.WitResourceBodyParser;
import ai.tegmentum.webassembly4j.bindgen.wit.WitResourceBodyParser.WitResourceMethod;
import ai.tegmentum.webassembly4j.bindgen.wit.WitType;
import ai.tegmentum.webassembly4j.bindgen.wit.WitTypeCategory;
import ai.tegmentum.webassembly4j.bindgen.wit.WitWorldPreprocessor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Main orchestrator for Java binding generation.
 *
 * <p>This class coordinates the parsing of WIT files, introspection of WASM modules, and generation
 * of Java source files based on the provided configuration.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * BindgenConfig config = BindgenConfig.builder()
 *     .packageName("com.example.generated")
 *     .outputDirectory(Path.of("target/generated-sources"))
 *     .codeStyle(CodeStyle.MODERN)
 *     .addWitSource(Path.of("src/main/wit"))
 *     .build();
 *
 * CodeGenerator generator = new CodeGenerator(config);
 * List<GeneratedSource> sources = generator.generate();
 * }</pre>
 */
public final class CodeGenerator {

  private static final Logger LOGGER = Logger.getLogger(CodeGenerator.class.getName());

  private final BindgenConfig config;
  private final JavaCodeGenerator javaCodeGenerator;

  /**
   * Creates a new CodeGenerator with the specified configuration.
   *
   * @param config the bindgen configuration
   * @throws BindgenException if configuration is invalid
   */
  public CodeGenerator(final BindgenConfig config) throws BindgenException {
    this.config = Objects.requireNonNull(config, "config");
    config.validate();

    // Select appropriate code generator based on style
    if (config.getCodeStyle() == CodeStyle.MODERN) {
      this.javaCodeGenerator = new ModernCodeGenerator(config);
    } else {
      this.javaCodeGenerator = new LegacyCodeGenerator(config);
    }
  }

  /**
   * Generates Java source files from the configured sources.
   *
   * @return the list of generated source files
   * @throws BindgenException if generation fails
   */
  public List<GeneratedSource> generate() throws BindgenException {
    List<GeneratedSource> allSources = new ArrayList<>();

    // Process WIT sources
    if (config.hasWitSources()) {
      LOGGER.info("Processing WIT sources...");
      for (Path witPath : config.getWitSources()) {
        BindgenModel model = parseWitSource(witPath);
        List<GeneratedSource> sources = javaCodeGenerator.generate(model);
        allSources.addAll(sources);
        LOGGER.fine("Generated " + sources.size() + " sources from " + witPath);
      }
    }

    // WASM source introspection is not yet implemented
    if (config.hasWasmSources()) {
      throw new BindgenException(
          "WASM source introspection is not yet implemented. Use WIT sources instead.");
    }

    LOGGER.info("Generated " + allSources.size() + " total source files");
    return allSources;
  }

  /**
   * Generates Java source files and writes them to the output directory.
   *
   * <p>If implementation generation and ServiceLoader registration are enabled,
   * also writes the {@code META-INF/services} file.
   *
   * @throws BindgenException if generation or writing fails
   */
  public void generateAndWrite() throws BindgenException {
    List<GeneratedSource> sources = generate();
    Path outputDir = config.getOutputDirectory();

    LOGGER.info("Writing generated sources to " + outputDir);
    for (GeneratedSource source : sources) {
      source.writeTo(outputDir);
      LOGGER.fine("Wrote " + source.getQualifiedName());
    }

    // Write ServiceLoader registration file
    if (config.isGenerateImplementations() && config.isGenerateServiceLoader()) {
      List<BindgenInterface> allInterfaces = new ArrayList<>();
      if (config.hasWitSources()) {
        for (Path witPath : config.getWitSources()) {
          BindgenModel model = parseWitSource(witPath);
          allInterfaces.addAll(model.getInterfaces());
        }
      }
      if (!allInterfaces.isEmpty()) {
        ImplementationCodeGenerator implGen = new ImplementationCodeGenerator(config);
        implGen.writeServiceLoaderFile(outputDir, allInterfaces);
        LOGGER.info("Wrote ServiceLoader registration file");
      }
    }
  }

  /**
   * Parses a WIT source file or directory.
   *
   * @param witPath the path to the WIT file or directory
   * @return the parsed model
   * @throws BindgenException if parsing fails
   */
  private BindgenModel parseWitSource(final Path witPath) throws BindgenException {
    try {
      final String witText = Files.readString(witPath);
      final String packageName =
          config.getPackageName() != null ? config.getPackageName() : "generated";

      // Fan a full WIT world source (package + world + multiple
      // interfaces + resource bodies) out into per-interface entries
      // the existing single-interface WIT parser can consume.
      final List<WitWorldPreprocessor.Interface> preprocessed =
          WitWorldPreprocessor.preprocess(witText);

      // Fall back to legacy single-interface parsing if the input
      // wasn't a multi-interface world (e.g. tests that pass a bare
      // `interface X { ... }`).
      final List<WitWorldPreprocessor.Interface> interfacesToParse;
      if (preprocessed.isEmpty()) {
        return parseWitSourceLegacy(witPath, witText, packageName);
      }
      interfacesToParse = preprocessed;

      final BindgenModel.Builder modelBuilder =
          BindgenModel.builder().name(witPath.getFileName().toString()).sourceFile(witPath.toString());

      for (final WitWorldPreprocessor.Interface pre : interfacesToParse) {
        final String reconstructed = "interface " + pre.getName() + " {\n" + pre.getBody() + "\n}\n";
        final WitInterfaceParser parser = new WitInterfaceParser();
        final WitInterfaceDefinition definition = parser.parseInterface(reconstructed, packageName);

        // LinkedHashMap preserves WIT declaration order for cross-JVM
    // deterministic generation. See WitInterfaceParser.parseTypes.
    final Map<String, BindgenType> convertedTypes = new java.util.LinkedHashMap<>();
        for (final Map.Entry<String, WitType> entry : definition.getTypes().entrySet()) {
          BindgenType bindgenType = convertWitType(entry.getKey(), entry.getValue());
          if (bindgenType.getKind() == BindgenType.Kind.RESOURCE
              && pre.getResourceBodies().containsKey(entry.getKey())) {
            final BindgenType.Builder rebuilt =
                BindgenType.builder()
                    .name(bindgenType.getName())
                    .kind(BindgenType.Kind.RESOURCE)
                    .documentation(bindgenType.getDocumentation().orElse(null));
            final List<WitResourceMethod> methods =
                WitResourceBodyParser.parse(
                    entry.getKey(), pre.getResourceBodies().get(entry.getKey()));
            for (final WitResourceMethod m : methods) {
              rebuilt.addResourceMethod(convertResourceMethod(m, convertedTypes));
            }
            bindgenType = rebuilt.build();
          }
          convertedTypes.put(entry.getKey(), bindgenType);
        }

        final List<BindgenFunction> convertedFunctions = new ArrayList<>();
        for (final Map.Entry<String, WitFunction> entry : definition.getFunctions().entrySet()) {
          final BindgenFunction bindgenFunc =
              convertWitFunction(entry.getValue(), convertedTypes);
          convertedFunctions.add(bindgenFunc);
        }

        if (pre.isWorld()) {
          // A hoisted `world X { ... }` block declares types + resources at
          // the top level (per ADR-006). It has no functions of its own —
          // its `import`/`export` clauses reference already-declared
          // interfaces. Emit the types as top-level model types so the
          // generator produces one Java file per type/resource without an
          // empty carrier interface. Any function that did slip in gets
          // added as a top-level function.
          for (final BindgenType type : convertedTypes.values()) {
            modelBuilder.addType(type);
          }
          for (final BindgenFunction func : convertedFunctions) {
            modelBuilder.addFunction(func);
          }
        } else {
          final BindgenInterface.Builder ifaceBuilder =
              BindgenInterface.builder().name(definition.getName()).packageName(packageName);
          for (final BindgenFunction func : convertedFunctions) {
            ifaceBuilder.addFunction(func);
          }
          for (final BindgenType type : convertedTypes.values()) {
            ifaceBuilder.addType(type);
          }
          modelBuilder.addInterface(ifaceBuilder.build());
        }
      }

      return modelBuilder.build();
    } catch (final IOException e) {
      throw new BindgenException("Failed to read WIT file: " + witPath, e);
    } catch (final BindgenException e) {
      throw new BindgenException("Failed to parse WIT file: " + witPath, e);
    }
  }

  private BindgenModel parseWitSourceLegacy(
      final Path witPath, final String witText, final String packageName)
      throws BindgenException {
    final WitInterfaceParser parser = new WitInterfaceParser();
    final WitInterfaceDefinition definition = parser.parseInterface(witText, packageName);

    final BindgenModel.Builder modelBuilder =
        BindgenModel.builder().name(definition.getName()).sourceFile(witPath.toString());

    // LinkedHashMap preserves WIT declaration order for cross-JVM
    // deterministic generation. See WitInterfaceParser.parseTypes.
    final Map<String, BindgenType> convertedTypes = new java.util.LinkedHashMap<>();
    for (final Map.Entry<String, WitType> entry : definition.getTypes().entrySet()) {
      final BindgenType bindgenType = convertWitType(entry.getKey(), entry.getValue());
      convertedTypes.put(entry.getKey(), bindgenType);
    }

    final List<BindgenFunction> convertedFunctions = new ArrayList<>();
    for (final Map.Entry<String, WitFunction> entry : definition.getFunctions().entrySet()) {
      final BindgenFunction bindgenFunc = convertWitFunction(entry.getValue(), convertedTypes);
      convertedFunctions.add(bindgenFunc);
    }

    final BindgenInterface.Builder ifaceBuilder =
        BindgenInterface.builder().name(definition.getName()).packageName(packageName);
    for (final BindgenFunction func : convertedFunctions) {
      ifaceBuilder.addFunction(func);
    }
    for (final BindgenType type : convertedTypes.values()) {
      ifaceBuilder.addType(type);
    }
    modelBuilder.addInterface(ifaceBuilder.build());
    return modelBuilder.build();
  }

  /**
   * Convert a parsed WIT resource method into a {@link BindgenFunction}. Type
   * expressions in parameters and the return position are resolved against the
   * interface's already-materialized type table. Anything unresolved falls back
   * to an opaque reference — bindgen doesn't run a full second-pass symbol
   * table today, so cross-resource references need the caller to have declared
   * the sibling resource first.
   */
  private BindgenFunction convertResourceMethod(
      final WitResourceMethod method, final Map<String, BindgenType> knownTypes) {
    final BindgenFunction.Builder builder =
        BindgenFunction.builder()
            .name(method.getName())
            .constructor(method.getKind() == WitResourceMethod.Kind.CONSTRUCTOR)
            .staticMethod(method.getKind() == WitResourceMethod.Kind.STATIC);
    for (final WitParameter p : method.getParameters()) {
      builder.addParameter(
          new BindgenParameter(p.getName(), resolveTypeExpression(p.getType().getName(), knownTypes)));
    }
    if (method.getReturnTypeExpression().isPresent()) {
      builder.returnType(
          resolveTypeExpression(method.getReturnTypeExpression().get(), knownTypes));
    }
    return builder.build();
  }

  /**
   * Resolve a raw WIT type expression string (e.g. {@code list<u8>},
   * {@code borrow<host-provider>}, {@code result<runtime-instance, error>})
   * into a bindgen type. Names that match already-known types resolve as
   * references; primitives resolve as primitives; container expressions
   * (list/option/result) recurse. Anything else lands as an opaque reference.
   */
  private BindgenType resolveTypeExpression(
      final String expression, final Map<String, BindgenType> knownTypes) {
    String expr = expression.trim();
    // Unwrap borrow<T> and own<T> — resource-lifetime qualifiers are ABI
    // concerns; the generated Java surface only cares about the referenced
    // type.
    while (true) {
      if (expr.startsWith("borrow<") && expr.endsWith(">")) {
        expr = expr.substring("borrow<".length(), expr.length() - 1).trim();
      } else if (expr.startsWith("own<") && expr.endsWith(">")) {
        expr = expr.substring("own<".length(), expr.length() - 1).trim();
      } else {
        break;
      }
    }
    if (knownTypes.containsKey(expr)) {
      return BindgenType.reference(expr);
    }
    // Primitive?
    if (isKnownPrimitive(expr)) {
      return BindgenType.primitive(expr);
    }
    // list<T>
    if (expr.startsWith("list<") && expr.endsWith(">")) {
      final String inner = expr.substring(5, expr.length() - 1).trim();
      return BindgenType.list(resolveTypeExpression(inner, knownTypes));
    }
    // option<T>
    if (expr.startsWith("option<") && expr.endsWith(">")) {
      final String inner = expr.substring(7, expr.length() - 1).trim();
      return BindgenType.option(resolveTypeExpression(inner, knownTypes));
    }
    // result<T, E>, result<_, E>, result<T, _>, result<_>, result
    if (expr.equals("result")) {
      return BindgenType.result(null, null);
    }
    if (expr.startsWith("result<") && expr.endsWith(">")) {
      final String inner = expr.substring(7, expr.length() - 1).trim();
      if (inner.isEmpty()) {
        return BindgenType.result(null, null);
      }
      final int comma = topLevelComma(inner);
      final String okPart = comma < 0 ? inner : inner.substring(0, comma).trim();
      final String errPart = comma < 0 ? "" : inner.substring(comma + 1).trim();
      final BindgenType ok =
          okPart.isEmpty() || okPart.equals("_")
              ? null
              : resolveTypeExpression(okPart, knownTypes);
      final BindgenType err =
          errPart.isEmpty() || errPart.equals("_")
              ? null
              : resolveTypeExpression(errPart, knownTypes);
      return BindgenType.result(ok, err);
    }
    return BindgenType.reference(expr);
  }

  private static int topLevelComma(final String s) {
    int angle = 0;
    for (int i = 0; i < s.length(); i++) {
      final char c = s.charAt(i);
      if (c == '<') {
        angle++;
      } else if (c == '>') {
        angle = Math.max(0, angle - 1);
      } else if (c == ',' && angle == 0) {
        return i;
      }
    }
    return -1;
  }

  private static boolean isKnownPrimitive(final String name) {
    switch (name) {
      case "bool":
      case "s8":
      case "s16":
      case "s32":
      case "s64":
      case "u8":
      case "u16":
      case "u32":
      case "u64":
      case "f32":
      case "f64":
      case "float32":
      case "float64":
      case "char":
      case "string":
        return true;
      default:
        return false;
    }
  }

  /**
   * Converts a WIT type to a bindgen type.
   *
   * @param name the type name
   * @param witType the WIT type
   * @return the bindgen type
   */
  private BindgenType convertWitType(final String name, final WitType witType) {
    final WitTypeCategory category = witType.getKind().getCategory();

    switch (category) {
      case PRIMITIVE:
        return BindgenType.primitive(witType.getName());

      case RECORD:
        return convertRecordType(name, witType);

      case VARIANT:
        return convertVariantType(name, witType);

      case ENUM:
        return BindgenType.builder()
            .name(name)
            .kind(BindgenType.Kind.ENUM)
            .enumValues(witType.getKind().getEnumValues())
            .documentation(witType.getDocumentation().orElse(null))
            .build();

      case FLAGS:
        return BindgenType.builder()
            .name(name)
            .kind(BindgenType.Kind.FLAGS)
            .enumValues(witType.getKind().getFlags())
            .documentation(witType.getDocumentation().orElse(null))
            .build();

      case LIST:
        final Optional<WitType> listInner = witType.getKind().getInnerType();
        final BindgenType listElement =
            listInner.isPresent()
                ? convertWitType(listInner.get().getName(), listInner.get())
                : BindgenType.primitive("u8");
        return BindgenType.list(listElement);

      case OPTION:
        final Optional<WitType> optInner = witType.getKind().getInnerType();
        final BindgenType optElement =
            optInner.isPresent()
                ? convertWitType(optInner.get().getName(), optInner.get())
                : BindgenType.primitive("u8");
        return BindgenType.option(optElement);

      case RESULT:
        final BindgenType okType =
            witType.getKind().getOkType().map(t -> convertWitType(t.getName(), t)).orElse(null);
        final BindgenType errorType =
            witType.getKind().getErrorType().map(t -> convertWitType(t.getName(), t)).orElse(null);
        return BindgenType.result(okType, errorType);

      case TUPLE:
        final List<BindgenType> tupleElements = new ArrayList<>();
        for (WitType element : witType.getKind().getTupleElements()) {
          tupleElements.add(convertWitType(element.getName(), element));
        }
        return BindgenType.builder()
            .name(witType.getName())
            .kind(BindgenType.Kind.TUPLE)
            .tupleElements(tupleElements)
            .documentation(witType.getDocumentation().orElse(null))
            .build();

      case RESOURCE:
        return BindgenType.builder()
            .name(name)
            .kind(BindgenType.Kind.RESOURCE)
            .documentation(witType.getDocumentation().orElse(null))
            .build();

      default:
        // Unknown category - treat as reference
        return BindgenType.reference(name);
    }
  }

  /**
   * Converts a WIT record type to a bindgen record type.
   *
   * @param name the type name
   * @param witType the WIT record type
   * @return the bindgen record type
   */
  private BindgenType convertRecordType(final String name, final WitType witType) {
    final BindgenType.Builder builder =
        BindgenType.builder()
            .name(name)
            .kind(BindgenType.Kind.RECORD)
            .documentation(witType.getDocumentation().orElse(null));

    for (Map.Entry<String, WitType> field : witType.getKind().getRecordFields().entrySet()) {
      final WitType fieldWit = field.getValue();
      // A record field can reference a previously-declared named
      // compound type (record / variant / enum / flags). For those
      // we emit a REFERENCE so the generator picks up the existing
      // declaration; previously every nested call passed the
      // FIELD name to convertWitType, so a `start-position:
      // position` field generated a synthetic `StartPosition`
      // Java type that never existed.
      final BindgenType fieldType;
      if (isNamedCompound(fieldWit)) {
        fieldType = BindgenType.reference(fieldWit.getName());
      } else {
        fieldType = convertWitType(fieldWit.getName(), fieldWit);
      }
      builder.addField(new BindgenField(field.getKey(), fieldType));
    }

    return builder.build();
  }

  /**
   * True when {@code witType} is a top-level named compound (record / variant /
   * enum / flags). Such types are always declared at the interface scope; a
   * field, case payload, or list/option element that has one of these
   * categories is always a reference, never an inline declaration.
   */
  private static boolean isNamedCompound(final WitType witType) {
    if (witType.getName() == null || witType.getName().isEmpty()) {
      return false;
    }
    final WitTypeCategory cat = witType.getKind().getCategory();
    return cat == WitTypeCategory.RECORD
        || cat == WitTypeCategory.VARIANT
        || cat == WitTypeCategory.ENUM
        || cat == WitTypeCategory.FLAGS;
  }

  /**
   * Converts a WIT variant type to a bindgen variant type.
   *
   * @param name the type name
   * @param witType the WIT variant type
   * @return the bindgen variant type
   */
  private BindgenType convertVariantType(final String name, final WitType witType) {
    final BindgenType.Builder builder =
        BindgenType.builder()
            .name(name)
            .kind(BindgenType.Kind.VARIANT)
            .documentation(witType.getDocumentation().orElse(null));

    for (Map.Entry<String, Optional<WitType>> entry :
        witType.getKind().getVariantCases().entrySet()) {
      final BindgenType payload =
          entry.getValue().map(t -> convertWitType(t.getName(), t)).orElse(null);
      builder.addCase(new BindgenVariantCase(entry.getKey(), payload));
    }

    return builder.build();
  }

  /**
   * Converts a WIT function to a bindgen function.
   *
   * @param witFunction the WIT function
   * @param knownTypes previously converted types for resolving references
   * @return the bindgen function
   */
  private BindgenFunction convertWitFunction(
      final WitFunction witFunction, final Map<String, BindgenType> knownTypes) {
    final BindgenFunction.Builder builder =
        BindgenFunction.builder()
            .name(witFunction.getName())
            .async(witFunction.isAsync())
            .documentation(witFunction.getDocumentation().orElse(null));

    // Convert parameters
    for (WitParameter param : witFunction.getParameters()) {
      final BindgenType paramType =
          resolveOrConvertType(param.getType().getName(), param.getType(), knownTypes);
      builder.addParameter(new BindgenParameter(param.getName(), paramType));
    }

    // Convert return type (WIT supports multiple returns, BindgenFunction supports single)
    final List<WitType> returnTypes = witFunction.getReturnTypes();
    if (!returnTypes.isEmpty()) {
      if (returnTypes.size() == 1) {
        final WitType retType = returnTypes.get(0);
        builder.returnType(resolveOrConvertType(retType.getName(), retType, knownTypes));
      } else {
        // Multiple returns: wrap in a tuple
        final List<BindgenType> tupleElements = new ArrayList<>();
        for (WitType retType : returnTypes) {
          tupleElements.add(resolveOrConvertType(retType.getName(), retType, knownTypes));
        }
        builder.returnType(
            BindgenType.builder()
                .name("tuple")
                .kind(BindgenType.Kind.TUPLE)
                .tupleElements(tupleElements)
                .build());
      }
    }

    return builder.build();
  }

  /**
   * Resolves a type from known types or converts it from WIT.
   *
   * @param name the type name
   * @param witType the WIT type
   * @param knownTypes previously converted types
   * @return the bindgen type
   */
  private BindgenType resolveOrConvertType(
      final String name, final WitType witType, final Map<String, BindgenType> knownTypes) {
    final BindgenType known = knownTypes.get(name);
    if (known != null) {
      return BindgenType.reference(name);
    }
    return convertWitType(name, witType);
  }

  /**
   * Returns the configuration used by this generator.
   *
   * @return the bindgen configuration
   */
  public BindgenConfig getConfig() {
    return config;
  }
}
