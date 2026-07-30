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
import ai.tegmentum.webassembly4j.bindgen.model.BindgenField;
import ai.tegmentum.webassembly4j.bindgen.model.BindgenFunction;
import ai.tegmentum.webassembly4j.bindgen.model.BindgenInterface;
import ai.tegmentum.webassembly4j.bindgen.model.BindgenModel;
import ai.tegmentum.webassembly4j.bindgen.model.BindgenParameter;
import ai.tegmentum.webassembly4j.bindgen.model.BindgenType;
import ai.tegmentum.webassembly4j.bindgen.util.JavaNaming;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;
import javax.lang.model.element.Modifier;

/**
 * Abstract base class for Java code generation.
 *
 * <p>Subclasses implement specific code generation strategies for different Java versions and
 * styles (modern records vs legacy POJOs).
 */
public abstract class JavaCodeGenerator {

  private static final Logger LOGGER = Logger.getLogger(JavaCodeGenerator.class.getName());

  protected final BindgenConfig config;
  protected final TypeMappingRegistry typeRegistry;

  /**
   * Creates a new JavaCodeGenerator.
   *
   * @param config the bindgen configuration
   */
  protected JavaCodeGenerator(final BindgenConfig config) {
    this.config = Objects.requireNonNull(config, "config");
    this.typeRegistry = new TypeMappingRegistry(config.getCodeStyle(), config.getPackageName());
  }

  /**
   * Generates Java source files from the bindgen model.
   *
   * @param model the bindgen model
   * @return the list of generated sources
   * @throws BindgenException if generation fails
   */
  public List<GeneratedSource> generate(final BindgenModel model) throws BindgenException {
    Objects.requireNonNull(model, "model");
    List<GeneratedSource> sources = new ArrayList<>();

    // Generate types
    for (BindgenType type : model.getTypes()) {
      sources.add(generateType(type));
    }

    // Filter out interfaces whose Java class-name would collide with a
    // nested type's Java class-name (same package = same simple name).
    // WIT permits `interface X { record X { ... } }` — the two land in the
    // same Java package as one `X.java`, and the record class overwrites
    // the interface, breaking `implements X` in the generated Impl with
    // "interface expected here" (ProviderManifestImpl in wasmos:runtime).
    // See {@link #shouldSkipInterface}.
    final List<BindgenInterface> emittedInterfaces = new ArrayList<>();
    for (BindgenInterface iface : model.getInterfaces()) {
      if (shouldSkipInterface(iface)) {
        continue;
      }
      emittedInterfaces.add(iface);
    }

    // Generate interfaces
    for (BindgenInterface iface : model.getInterfaces()) {
      final boolean skipInterface = shouldSkipInterface(iface);
      if (!skipInterface) {
        sources.add(generateInterface(iface));
      }
      // Generate types defined within interfaces regardless — the record
      // that caused the collision is still the intended output; only the
      // now-empty interface + Impl scaffolding is dropped.
      for (BindgenType type : iface.getTypes()) {
        sources.add(generateType(type));
      }
    }

    // Generate implementation classes if enabled
    if (config.isGenerateImplementations() && !emittedInterfaces.isEmpty()) {
      ImplementationCodeGenerator implGen = new ImplementationCodeGenerator(config);
      sources.addAll(implGen.generate(emittedInterfaces));
    }

    // Bindgen 2.0: emit runtime-provider SPI + registry when configured.
    // The SPI dispatches every method on every resource type in the model
    // (world-level + interface-level), so the embedder writes one impl and
    // generated resource bodies delegate to the installed provider.
    if (config.getRuntimeProviderName() != null && !config.getRuntimeProviderName().isEmpty()) {
      final List<BindgenType> resources = new ArrayList<>();
      for (BindgenType type : model.getTypes()) {
        if (type.getKind() == BindgenType.Kind.RESOURCE) {
          resources.add(type);
        }
      }
      for (BindgenInterface iface : model.getInterfaces()) {
        for (BindgenType type : iface.getTypes()) {
          if (type.getKind() == BindgenType.Kind.RESOURCE) {
            resources.add(type);
          }
        }
      }
      if (!resources.isEmpty()) {
        final RuntimeProviderCodeGenerator spiGen = new RuntimeProviderCodeGenerator(config);
        sources.add(spiGen.generateInterface(resources));
        sources.add(spiGen.generateRegistry());
      }
    }

    return sources;
  }

  /**
   * True when {@code iface} declares a nested type whose Java class-name would
   * be identical to the interface's own Java class-name in the target package.
   *
   * <p>WIT allows an interface and a nested record/variant/enum to share the
   * same identifier — {@code interface provider-manifest { record
   * provider-manifest {...} }} in
   * {@code wasmos:runtime/provider-manifest.wit} is a live example. Java
   * cannot: both compile to {@code ProviderManifest.java} in the same
   * package, and the second overwrites the first. The generated Impl then
   * fails to compile with "interface expected here" against a class name.
   *
   * <p>When the interface has zero functions (it exists purely as a
   * namespace for the nested types), skipping the interface + its Impl +
   * BindingProvider is a safe, name-preserving fix — the nested types are
   * still emitted as top-level classes in the package.
   *
   * <p>When the interface has functions, dropping it silently would delete
   * user-visible API. This method throws {@link BindgenException} in that
   * case so the caller sees a specific error directing them to rename the
   * WIT identifier. A follow-on change may promote this to a real rename
   * pass (append "Record" / "Variant" to the nested type's Java class); the
   * current fix defers that surface change until it becomes required.
   *
   * @param iface the interface to check
   * @return true when the interface should be skipped from code emission
   * @throws BindgenException when the collision is present AND the interface
   *     carries functions (a rename pass, not a skip, is required)
   */
  public static boolean shouldSkipInterface(final BindgenInterface iface) throws BindgenException {
    final String ifaceClassName = JavaNaming.toClassName(iface.getName());
    BindgenType collidingType = null;
    for (BindgenType nested : iface.getTypes()) {
      final String nestedClassName = JavaNaming.toClassName(nested.getName());
      if (ifaceClassName.equals(nestedClassName)) {
        collidingType = nested;
        break;
      }
    }
    if (collidingType == null) {
      return false;
    }
    if (!iface.getFunctions().isEmpty()) {
      throw new BindgenException(
          "WIT interface '"
              + iface.getName()
              + "' contains a nested "
              + collidingType.getKind().name().toLowerCase()
              + " with the same identifier — both would map to '"
              + ifaceClassName
              + "' in the same Java package, and the interface carries "
              + iface.getFunctions().size()
              + " function(s) that cannot be silently dropped. Rename either the"
              + " interface or the nested type in the WIT source (recommended:"
              + " rename the nested type, since it is the source of the collision).");
    }
    LOGGER.warning(
        "WIT interface '"
            + iface.getName()
            + "' contains a nested "
            + collidingType.getKind().name().toLowerCase()
            + " with the same identifier — both would map to '"
            + ifaceClassName
            + "' in the same Java package. The interface declares no functions,"
            + " so it is effectively a namespace for the nested type; skipping"
            + " the empty interface + its Impl + its BindingProvider. Rename the"
            + " nested type in WIT if you want the interface to keep its own"
            + " Java class.");
    return true;
  }

  /**
   * Generates a Java type for the given bindgen type.
   *
   * @param type the bindgen type
   * @return the generated source
   * @throws BindgenException if generation fails
   */
  public GeneratedSource generateType(final BindgenType type) throws BindgenException {
    Objects.requireNonNull(type, "type");

    TypeSpec typeSpec;
    switch (type.getKind()) {
      case RECORD:
        typeSpec = generateRecord(type);
        break;
      case VARIANT:
        typeSpec = generateVariant(type);
        break;
      case ENUM:
        typeSpec = generateEnum(type);
        break;
      case FLAGS:
        typeSpec = generateFlags(type);
        break;
      case RESOURCE:
        typeSpec = generateResource(type);
        break;
      default:
        throw new BindgenException("Cannot generate type for kind: " + type.getKind());
    }

    JavaFile javaFile =
        JavaFile.builder(config.getPackageName(), typeSpec)
            .skipJavaLangImports(true)
            .indent("  ")
            .build();

    return new GeneratedSource(javaFile);
  }

  /**
   * Generates a Java interface for the given bindgen interface.
   *
   * @param iface the bindgen interface
   * @return the generated source
   * @throws BindgenException if generation fails
   */
  public GeneratedSource generateInterface(final BindgenInterface iface) throws BindgenException {
    Objects.requireNonNull(iface, "iface");

    String className = JavaNaming.toClassName(iface.getName());
    TypeSpec.Builder interfaceBuilder =
        TypeSpec.interfaceBuilder(className).addModifiers(Modifier.PUBLIC);

    // Add documentation
    if (config.isGenerateJavadoc() && iface.getDocumentation().isPresent()) {
      interfaceBuilder.addJavadoc(iface.getDocumentation().get() + "\n");
    }

    // Add function declarations
    for (BindgenFunction function : iface.getFunctions()) {
      MethodSpec method = generateFunctionSignature(function);
      interfaceBuilder.addMethod(method);
    }

    JavaFile javaFile =
        JavaFile.builder(config.getPackageName(), interfaceBuilder.build())
            .skipJavaLangImports(true)
            .indent("  ")
            .build();

    return new GeneratedSource(javaFile);
  }

  /**
   * Generates a record type.
   *
   * @param type the record type
   * @return the generated TypeSpec
   */
  protected abstract TypeSpec generateRecord(BindgenType type);

  /**
   * Generates a variant type.
   *
   * @param type the variant type
   * @return the generated TypeSpec
   */
  protected abstract TypeSpec generateVariant(BindgenType type);

  /**
   * Generates an enum type.
   *
   * @param type the enum type
   * @return the generated TypeSpec
   */
  protected TypeSpec generateEnum(final BindgenType type) {
    String className = JavaNaming.toClassName(type.getName());
    TypeSpec.Builder enumBuilder = TypeSpec.enumBuilder(className).addModifiers(Modifier.PUBLIC);

    // Add documentation
    if (config.isGenerateJavadoc() && type.getDocumentation().isPresent()) {
      enumBuilder.addJavadoc(type.getDocumentation().get() + "\n");
    }

    // Add enum values
    for (String value : type.getEnumValues()) {
      enumBuilder.addEnumConstant(JavaNaming.toEnumConstant(value));
    }

    return enumBuilder.build();
  }

  /**
   * Generates a flags type.
   *
   * @param type the flags type
   * @return the generated TypeSpec
   */
  protected TypeSpec generateFlags(final BindgenType type) {
    // Generate as an enum that can be used with EnumSet
    return generateEnum(type);
  }

  /**
   * Generates a resource type.
   *
   * @param type the resource type
   * @return the generated TypeSpec
   */
  protected abstract TypeSpec generateResource(BindgenType type);

  /**
   * Generates a method signature for a function.
   *
   * @param function the function
   * @return the method specification
   */
  protected MethodSpec generateFunctionSignature(final BindgenFunction function) {
    String methodName = JavaNaming.toMethodName(function.getName());
    MethodSpec.Builder methodBuilder =
        MethodSpec.methodBuilder(methodName).addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT);

    // Add documentation
    if (config.isGenerateJavadoc() && function.getDocumentation().isPresent()) {
      methodBuilder.addJavadoc(function.getDocumentation().get() + "\n");
    }

    // Add parameters
    for (BindgenParameter param : function.getParameters()) {
      String paramName = JavaNaming.toParameterName(param.getName());
      TypeName paramType = mapType(param.getType());
      methodBuilder.addParameter(ParameterSpec.builder(paramType, paramName).build());
    }

    // Add return type
    if (function.getReturnType().isPresent()) {
      TypeName returnType = mapType(function.getReturnType().get());
      methodBuilder.returns(returnType);
    }

    return methodBuilder.build();
  }

  /**
   * Generates an equals method for a record type.
   *
   * @param type the record type
   * @param className the class name
   * @return the equals method specification
   */
  protected MethodSpec generateEquals(final BindgenType type, final String className) {
    MethodSpec.Builder equalsBuilder =
        MethodSpec.methodBuilder("equals")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override.class)
            .returns(TypeName.BOOLEAN)
            .addParameter(Object.class, "obj");

    equalsBuilder.addStatement("if (this == obj) return true");
    equalsBuilder.addStatement("if (obj == null || getClass() != obj.getClass()) return false");
    equalsBuilder.addStatement("$L that = ($L) obj", className, className);

    if (type.getFields().isEmpty()) {
      equalsBuilder.addStatement("return true");
    } else {
      StringBuilder comparison = new StringBuilder();
      for (int i = 0; i < type.getFields().size(); i++) {
        BindgenField field = type.getFields().get(i);
        String fieldName = JavaNaming.toFieldName(field.getName());
        TypeName fieldType = mapType(field.getType());

        if (i > 0) {
          comparison.append(" && ");
        }

        if (fieldType.isPrimitive()) {
          comparison.append(String.format("this.%s == that.%s", fieldName, fieldName));
        } else {
          comparison.append(
              String.format("java.util.Objects.equals(this.%s, that.%s)", fieldName, fieldName));
        }
      }
      equalsBuilder.addStatement("return " + comparison);
    }

    return equalsBuilder.build();
  }

  /**
   * Generates a hashCode method for a record type.
   *
   * @param type the record type
   * @return the hashCode method specification
   */
  protected MethodSpec generateHashCode(final BindgenType type) {
    MethodSpec.Builder hashCodeBuilder =
        MethodSpec.methodBuilder("hashCode")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override.class)
            .returns(TypeName.INT);

    if (type.getFields().isEmpty()) {
      hashCodeBuilder.addStatement("return 0");
    } else {
      StringBuilder args = new StringBuilder();
      for (int i = 0; i < type.getFields().size(); i++) {
        BindgenField field = type.getFields().get(i);
        String fieldName = JavaNaming.toFieldName(field.getName());
        if (i > 0) {
          args.append(", ");
        }
        args.append(fieldName);
      }
      hashCodeBuilder.addStatement("return java.util.Objects.hash($L)", args);
    }

    return hashCodeBuilder.build();
  }

  /**
   * Generates a toString method for a record type.
   *
   * @param type the record type
   * @param className the class name
   * @param openBracket the opening bracket character (e.g., "[" or "{")
   * @param closeBracket the closing bracket character (e.g., "]" or "}")
   * @return the toString method specification
   */
  protected MethodSpec generateToString(
      final BindgenType type,
      final String className,
      final String openBracket,
      final String closeBracket) {
    MethodSpec.Builder toStringBuilder =
        MethodSpec.methodBuilder("toString")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override.class)
            .returns(String.class);

    if (type.getFields().isEmpty()) {
      toStringBuilder.addStatement("return \"$L$L$L\"", className, openBracket, closeBracket);
    } else {
      StringBuilder format = new StringBuilder();
      format.append(className).append(openBracket);
      for (int i = 0; i < type.getFields().size(); i++) {
        BindgenField field = type.getFields().get(i);
        String fieldName = JavaNaming.toFieldName(field.getName());
        if (i > 0) {
          format.append(", ");
        }
        format.append(fieldName).append("=\" + ").append(fieldName).append(" + \"");
      }
      format.append(closeBracket);
      toStringBuilder.addStatement("return \"$L\"", format);
    }

    return toStringBuilder.build();
  }

  /**
   * Maps a bindgen type to a Java type.
   *
   * @param type the bindgen type
   * @return the Java type name
   */
  protected TypeName mapType(final BindgenType type) {
    switch (type.getKind()) {
      case PRIMITIVE:
        return typeRegistry.mapWitPrimitive(type.getName());
      case LIST:
        TypeName elementType = mapType(type.getElementType().get());
        return typeRegistry.mapList(elementType);
      case OPTION:
        TypeName innerType = mapType(type.getElementType().get());
        return typeRegistry.mapOption(innerType);
      case RESULT:
        TypeName okType = type.getOkType().map(this::mapType).orElse(null);
        TypeName errorType = type.getErrorType().map(this::mapType).orElse(null);
        return typeRegistry.mapResult(okType, errorType);
      case TUPLE:
        // Recurse into every element so a nested container (e.g. the
        // `list<u8>` inside `tuple<string, list<u8>>`) is mapped to a real
        // Java type instead of being echoed back as raw WIT source. Before
        // this branch existed, TUPLE fell through to the REFERENCE default
        // and mapGeneratedType was handed the whole `tuple<...>` string,
        // which JavaPoet embedded verbatim into the emitted field type.
        List<TypeName> tupleElements = new ArrayList<>(type.getTupleElements().size());
        for (BindgenType elem : type.getTupleElements()) {
          tupleElements.add(mapType(elem));
        }
        return typeRegistry.mapTuple(tupleElements);
      case REFERENCE:
        return typeRegistry.mapGeneratedType(type.getReferencedTypeName().get());
      default:
        return typeRegistry.mapGeneratedType(type.getName());
    }
  }
}
