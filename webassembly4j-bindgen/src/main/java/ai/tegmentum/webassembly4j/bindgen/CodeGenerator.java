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
import ai.tegmentum.webassembly4j.bindgen.wit.WitType;
import ai.tegmentum.webassembly4j.bindgen.wit.WitTypeCategory;
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

      final WitInterfaceParser parser = new WitInterfaceParser();
      final WitInterfaceDefinition definition = parser.parseInterface(witText, packageName);

      final BindgenModel.Builder modelBuilder =
          BindgenModel.builder().name(definition.getName()).sourceFile(witPath.toString());

      // Convert WIT types to bindgen types
      final Map<String, BindgenType> convertedTypes = new HashMap<>();
      for (Map.Entry<String, WitType> entry : definition.getTypes().entrySet()) {
        final BindgenType bindgenType = convertWitType(entry.getKey(), entry.getValue());
        convertedTypes.put(entry.getKey(), bindgenType);
      }

      // Convert WIT functions to bindgen functions
      final List<BindgenFunction> convertedFunctions = new ArrayList<>();
      for (Map.Entry<String, WitFunction> entry : definition.getFunctions().entrySet()) {
        final BindgenFunction bindgenFunc = convertWitFunction(entry.getValue(), convertedTypes);
        convertedFunctions.add(bindgenFunc);
      }

      // Build interface containing types and functions
      final BindgenInterface.Builder ifaceBuilder =
          BindgenInterface.builder().name(definition.getName()).packageName(packageName);

      for (BindgenFunction func : convertedFunctions) {
        ifaceBuilder.addFunction(func);
      }
      for (BindgenType type : convertedTypes.values()) {
        ifaceBuilder.addType(type);
      }

      modelBuilder.addInterface(ifaceBuilder.build());

      return modelBuilder.build();
    } catch (final IOException e) {
      throw new BindgenException("Failed to read WIT file: " + witPath, e);
    } catch (final BindgenException e) {
      throw new BindgenException("Failed to parse WIT file: " + witPath, e);
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
      final BindgenType fieldType = convertWitType(field.getKey(), field.getValue());
      builder.addField(new BindgenField(field.getKey(), fieldType));
    }

    return builder.build();
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
