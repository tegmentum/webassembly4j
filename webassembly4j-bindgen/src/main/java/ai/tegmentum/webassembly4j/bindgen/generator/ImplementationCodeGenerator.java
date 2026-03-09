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
import ai.tegmentum.webassembly4j.bindgen.model.BindgenParameter;
import ai.tegmentum.webassembly4j.bindgen.model.BindgenType;
import ai.tegmentum.webassembly4j.bindgen.util.JavaNaming;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.lang.model.element.Modifier;

/**
 * Generates implementation classes for bindgen interfaces.
 *
 * <p>For each {@link BindgenInterface}, generates a companion {@code *Impl} class that
 * implements the interface and wires WASM function calls through the marshalling infrastructure.
 */
public final class ImplementationCodeGenerator {

  private static final ClassName INSTANCE_CLASS =
      ClassName.get("ai.tegmentum.webassembly4j.api", "Instance");
  private static final ClassName MODULE_CLASS =
      ClassName.get("ai.tegmentum.webassembly4j.api", "Module");
  private static final ClassName ENGINE_CLASS =
      ClassName.get("ai.tegmentum.webassembly4j.api", "Engine");
  private static final ClassName FUNCTION_CLASS =
      ClassName.get("ai.tegmentum.webassembly4j.api", "Function");
  private static final ClassName MARSHAL_CONTEXT_CLASS =
      ClassName.get("ai.tegmentum.webassembly4j.runtime.marshal", "MarshalContext");
  private static final ClassName STRING_CODEC_CLASS =
      ClassName.get("ai.tegmentum.webassembly4j.runtime.marshal", "StringCodec");
  private static final ClassName WASM_BINDING_PROVIDER_CLASS =
      ClassName.get("ai.tegmentum.webassembly4j.runtime.spi", "WasmBindingProvider");

  private static final String WASM_BINDING_PROVIDER_FQN =
      "ai.tegmentum.webassembly4j.runtime.spi.WasmBindingProvider";

  private final BindgenConfig config;
  private final TypeMappingRegistry typeRegistry;

  /**
   * Creates a new ImplementationCodeGenerator.
   *
   * @param config the bindgen configuration
   */
  public ImplementationCodeGenerator(BindgenConfig config) {
    this.config = Objects.requireNonNull(config, "config");
    this.typeRegistry = new TypeMappingRegistry(config.getCodeStyle(), config.getPackageName());
  }

  /**
   * Generates implementation classes for the given interfaces.
   *
   * @param interfaces the interfaces to generate implementations for
   * @return the list of generated sources (Impl classes + binding providers)
   * @throws BindgenException if generation fails
   */
  public List<GeneratedSource> generate(List<BindgenInterface> interfaces) throws BindgenException {
    List<GeneratedSource> sources = new ArrayList<>();

    for (BindgenInterface iface : interfaces) {
      sources.add(generateImpl(iface));
      if (config.isGenerateServiceLoader()) {
        sources.add(generateBindingProvider(iface));
      }
    }

    return sources;
  }

  /**
   * Generates the *Impl class for an interface.
   */
  GeneratedSource generateImpl(BindgenInterface iface) throws BindgenException {
    String interfaceName = JavaNaming.toClassName(iface.getName());
    String implName = interfaceName + "Impl";
    ClassName interfaceClass = ClassName.get(config.getPackageName(), interfaceName);

    TypeSpec.Builder implBuilder = TypeSpec.classBuilder(implName)
        .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
        .addSuperinterface(interfaceClass)
        .addSuperinterface(AutoCloseable.class);

    // Fields
    implBuilder.addField(FieldSpec.builder(INSTANCE_CLASS, "instance", Modifier.PRIVATE, Modifier.FINAL).build());
    implBuilder.addField(FieldSpec.builder(MODULE_CLASS, "module", Modifier.PRIVATE, Modifier.FINAL).build());
    implBuilder.addField(FieldSpec.builder(ENGINE_CLASS, "engine", Modifier.PRIVATE, Modifier.FINAL).build());

    boolean needsMarshal = interfaceNeedsMarshalling(iface);
    if (needsMarshal) {
      implBuilder.addField(FieldSpec.builder(MARSHAL_CONTEXT_CLASS, "marshal", Modifier.PRIVATE, Modifier.FINAL).build());
    }

    // Function fields
    for (BindgenFunction function : iface.getFunctions()) {
      String fieldName = JavaNaming.toFieldName(function.getName()) + "Fn";
      implBuilder.addField(FieldSpec.builder(FUNCTION_CLASS, fieldName, Modifier.PRIVATE, Modifier.FINAL).build());
    }

    // Constructor
    implBuilder.addMethod(generateConstructor(iface, needsMarshal));

    // Method implementations
    for (BindgenFunction function : iface.getFunctions()) {
      implBuilder.addMethod(generateMethod(function));
    }

    // close() method
    implBuilder.addMethod(generateCloseMethod());

    JavaFile javaFile = JavaFile.builder(config.getPackageName(), implBuilder.build())
        .skipJavaLangImports(true)
        .indent("  ")
        .build();

    return new GeneratedSource(javaFile);
  }

  /**
   * Generates the WasmBindingProvider class for an interface.
   */
  GeneratedSource generateBindingProvider(BindgenInterface iface) throws BindgenException {
    String interfaceName = JavaNaming.toClassName(iface.getName());
    String providerName = interfaceName + "BindingProvider";
    String implName = interfaceName + "Impl";
    ClassName interfaceClass = ClassName.get(config.getPackageName(), interfaceName);
    ClassName implClass = ClassName.get(config.getPackageName(), implName);

    TypeSpec.Builder providerBuilder = TypeSpec.classBuilder(providerName)
        .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
        .addSuperinterface(WASM_BINDING_PROVIDER_CLASS);

    // supports() method
    providerBuilder.addMethod(MethodSpec.methodBuilder("supports")
        .addModifiers(Modifier.PUBLIC)
        .addAnnotation(Override.class)
        .returns(boolean.class)
        .addParameter(ClassName.get(Class.class), "iface")
        .addStatement("return iface == $T.class", interfaceClass)
        .build());

    // create() method
    TypeVariableName typeVar = TypeVariableName.get("T");
    providerBuilder.addMethod(MethodSpec.methodBuilder("create")
        .addModifiers(Modifier.PUBLIC)
        .addAnnotation(Override.class)
        .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class)
            .addMember("value", "$S", "unchecked")
            .build())
        .addTypeVariable(typeVar)
        .returns(typeVar)
        .addParameter(ClassName.get(Class.class), "iface")
        .addParameter(INSTANCE_CLASS, "instance")
        .addParameter(MODULE_CLASS, "module")
        .addParameter(ENGINE_CLASS, "engine")
        .addStatement("return (T) new $T(instance, module, engine)", implClass)
        .build());

    JavaFile javaFile = JavaFile.builder(config.getPackageName(), providerBuilder.build())
        .skipJavaLangImports(true)
        .indent("  ")
        .build();

    return new GeneratedSource(javaFile);
  }

  /**
   * Returns the fully qualified provider class names for the given interfaces.
   *
   * @param interfaces the interfaces that have generated providers
   * @return the list of provider class names
   */
  public List<String> getProviderClassNames(List<BindgenInterface> interfaces) {
    List<String> names = new ArrayList<>();
    for (BindgenInterface iface : interfaces) {
      String className = JavaNaming.toClassName(iface.getName()) + "BindingProvider";
      names.add(config.getPackageName() + "." + className);
    }
    return names;
  }

  /**
   * Writes the {@code META-INF/services} file for ServiceLoader discovery.
   *
   * <p>The file is written under the given output directory at
   * {@code META-INF/services/ai.tegmentum.webassembly4j.runtime.spi.WasmBindingProvider}.
   *
   * @param outputDirectory the base output directory (same as for generated sources)
   * @param interfaces the interfaces that have generated providers
   * @throws BindgenException if writing fails
   */
  public void writeServiceLoaderFile(Path outputDirectory, List<BindgenInterface> interfaces)
      throws BindgenException {
    List<String> providerNames = getProviderClassNames(interfaces);
    if (providerNames.isEmpty()) {
      return;
    }

    Path servicesDir = outputDirectory.resolve("META-INF").resolve("services");
    Path servicesFile = servicesDir.resolve(WASM_BINDING_PROVIDER_FQN);

    try {
      Files.createDirectories(servicesDir);
      StringBuilder content = new StringBuilder();
      for (String name : providerNames) {
        content.append(name).append('\n');
      }
      Files.writeString(servicesFile, content.toString(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw BindgenException.ioError("writing ServiceLoader file to " + servicesFile, e);
    }
  }

  private MethodSpec generateConstructor(BindgenInterface iface, boolean needsMarshal) {
    MethodSpec.Builder ctor = MethodSpec.constructorBuilder()
        .addModifiers(Modifier.PUBLIC)
        .addParameter(INSTANCE_CLASS, "instance")
        .addParameter(MODULE_CLASS, "module")
        .addParameter(ENGINE_CLASS, "engine")
        .addStatement("this.instance = instance")
        .addStatement("this.module = module")
        .addStatement("this.engine = engine");

    if (needsMarshal) {
      ctor.addStatement("this.marshal = $T.fromInstance(instance)", MARSHAL_CONTEXT_CLASS);
    }

    for (BindgenFunction function : iface.getFunctions()) {
      String fieldName = JavaNaming.toFieldName(function.getName()) + "Fn";
      String exportName = function.getName();
      ctor.addStatement("this.$L = instance.function($S).orElseThrow(() -> "
              + "new $T($S + $S))",
          fieldName, exportName,
          IllegalArgumentException.class, "WASM module does not export function: ", exportName);
    }

    return ctor.build();
  }

  private MethodSpec generateMethod(BindgenFunction function) {
    String methodName = JavaNaming.toMethodName(function.getName());
    String fieldName = JavaNaming.toFieldName(function.getName()) + "Fn";

    MethodSpec.Builder method = MethodSpec.methodBuilder(methodName)
        .addModifiers(Modifier.PUBLIC)
        .addAnnotation(Override.class);

    // Add parameters
    for (BindgenParameter param : function.getParameters()) {
      String paramName = JavaNaming.toParameterName(param.getName());
      TypeName paramType = mapType(param.getType());
      method.addParameter(paramType, paramName);
    }

    // Set return type
    boolean hasReturn = function.hasReturnType();
    TypeName returnType = TypeName.VOID;
    if (hasReturn) {
      returnType = mapType(function.getReturnType().get());
      method.returns(returnType);
    }

    // Determine if marshalling is needed
    boolean needsMarshal = functionNeedsMarshalling(function);

    if (needsMarshal) {
      generateMarshallingBody(method, function, fieldName, returnType, hasReturn);
    } else {
      generateDirectBody(method, function, fieldName, returnType, hasReturn);
    }

    return method.build();
  }

  private void generateDirectBody(MethodSpec.Builder method, BindgenFunction function,
                                   String fieldName, TypeName returnType, boolean hasReturn) {
    StringBuilder args = new StringBuilder();
    for (int i = 0; i < function.getParameters().size(); i++) {
      if (i > 0) {
        args.append(", ");
      }
      args.append(JavaNaming.toParameterName(function.getParameters().get(i).getName()));
    }

    if (!hasReturn) {
      method.addStatement("$L.invoke($L)", fieldName, args);
    } else if (returnType.equals(TypeName.INT)) {
      method.addStatement("return (($T) $L.invoke($L)).intValue()", Number.class, fieldName, args);
    } else if (returnType.equals(TypeName.LONG)) {
      method.addStatement("return (($T) $L.invoke($L)).longValue()", Number.class, fieldName, args);
    } else if (returnType.equals(TypeName.FLOAT)) {
      method.addStatement("return (($T) $L.invoke($L)).floatValue()", Number.class, fieldName, args);
    } else if (returnType.equals(TypeName.DOUBLE)) {
      method.addStatement("return (($T) $L.invoke($L)).doubleValue()", Number.class, fieldName, args);
    } else if (returnType.equals(TypeName.BOOLEAN)) {
      method.addStatement("return (($T) $L.invoke($L)).intValue() != 0", Number.class, fieldName, args);
    } else {
      method.addStatement("return ($T) $L.invoke($L)", returnType, fieldName, args);
    }
  }

  private void generateMarshallingBody(MethodSpec.Builder method, BindgenFunction function,
                                        String fieldName, TypeName returnType, boolean hasReturn) {
    BindgenType returnBindgenType = hasReturn ? function.getReturnType().get() : null;
    boolean complexReturn = MarshallingStrategy.requiresMarshalling(returnBindgenType);

    method.addStatement("java.util.List<Object> args = new java.util.ArrayList<>()");

    // Allocate return pointer for complex returns
    if (complexReturn) {
      method.addStatement("int retptr = marshal.allocator().allocate(8, 4)");
      method.addStatement("args.add(retptr)");
    }

    // Lower each parameter
    for (BindgenParameter param : function.getParameters()) {
      String paramName = JavaNaming.toParameterName(param.getName());
      BindgenType paramType = param.getType();

      if (MarshallingStrategy.requiresMarshalling(paramType)) {
        method.addCode(MarshallingStrategy.lowerArgument(paramType, paramName, "args"));
      } else {
        method.addStatement("args.add($L)", paramName);
      }
    }

    // Invoke
    if (!hasReturn || complexReturn) {
      method.addStatement("$L.invoke(args.toArray())", fieldName);
    } else {
      method.addStatement("Object result = $L.invoke(args.toArray())", fieldName);
    }

    // Lift return
    if (hasReturn) {
      if (complexReturn) {
        CodeBlock liftCode = MarshallingStrategy.liftReturn(returnBindgenType, "retptr");
        method.addStatement("return $L", liftCode);
      } else {
        CodeBlock liftCode = MarshallingStrategy.liftReturn(returnBindgenType, "result");
        method.addStatement("return $L", liftCode);
      }
    }
  }

  private MethodSpec generateCloseMethod() {
    return MethodSpec.methodBuilder("close")
        .addModifiers(Modifier.PUBLIC)
        .addAnnotation(Override.class)
        .beginControlFlow("try")
        .addStatement("module.close()")
        .nextControlFlow("finally")
        .addStatement("engine.close()")
        .endControlFlow()
        .build();
  }

  private boolean interfaceNeedsMarshalling(BindgenInterface iface) {
    for (BindgenFunction function : iface.getFunctions()) {
      if (functionNeedsMarshalling(function)) {
        return true;
      }
    }
    return false;
  }

  private boolean functionNeedsMarshalling(BindgenFunction function) {
    for (BindgenParameter param : function.getParameters()) {
      if (MarshallingStrategy.requiresMarshalling(param.getType())) {
        return true;
      }
    }
    if (function.hasReturnType()) {
      return MarshallingStrategy.requiresMarshalling(function.getReturnType().get());
    }
    return false;
  }

  private TypeName mapType(BindgenType type) {
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
      case REFERENCE:
        return typeRegistry.mapGeneratedType(type.getReferencedTypeName().get());
      case ENUM:
        return typeRegistry.mapGeneratedType(type.getName());
      default:
        return typeRegistry.mapGeneratedType(type.getName());
    }
  }
}
