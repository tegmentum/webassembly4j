/*
 * Copyright 2026 Tegmentum AI. All rights reserved.
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
import ai.tegmentum.webassembly4j.bindgen.GeneratedSource;
import ai.tegmentum.webassembly4j.bindgen.model.BindgenFunction;
import ai.tegmentum.webassembly4j.bindgen.model.BindgenParameter;
import ai.tegmentum.webassembly4j.bindgen.model.BindgenType;
import ai.tegmentum.webassembly4j.bindgen.util.JavaNaming;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import java.util.List;
import java.util.Objects;
import javax.lang.model.element.Modifier;

/**
 * Emits the bindgen-2.0 runtime-provider SPI: a {@code <name>} interface with one dispatch method
 * per resource method, plus a {@code <name>Registry} class that stores and hands out the installed
 * provider.
 *
 * <p>Method-name convention: {@code <resourceName><methodName>} in camelCase, with a {@code long
 * handle} parameter prepended to instance-method dispatch signatures. Constructor factories keep
 * the resource-typed return so the SPI impl is the sole locus that knows how to wrap a
 * runtime-issued raw handle in the generated class. See {@code docs/design-2.0.md}.
 *
 * <p>Determinism: methods are emitted in the same order as the model's iteration (WIT declaration
 * order), matching the convention already enforced by the resource generator.
 */
public final class RuntimeProviderCodeGenerator {

  private static final String CLOSE_METHOD_NAME = "close";

  private final BindgenConfig config;
  private final TypeMappingRegistry typeRegistry;

  /**
   * Creates a new runtime-provider generator.
   *
   * @param config the bindgen configuration (must have a non-null {@code runtimeProviderName})
   */
  public RuntimeProviderCodeGenerator(final BindgenConfig config) {
    this.config = Objects.requireNonNull(config, "config");
    if (config.getRuntimeProviderName() == null || config.getRuntimeProviderName().isEmpty()) {
      throw new IllegalArgumentException(
          "runtimeProviderName must be set to use RuntimeProviderCodeGenerator");
    }
    this.typeRegistry = new TypeMappingRegistry(config.getCodeStyle(), config.getPackageName());
  }

  /**
   * Generate the SPI interface for the supplied resource types.
   *
   * @param resources resource types to expose in the SPI (in declaration order)
   * @return the generated interface source
   */
  public GeneratedSource generateInterface(final List<BindgenType> resources) {
    final String spiName = config.getRuntimeProviderName();
    final TypeSpec.Builder ifaceBuilder =
        TypeSpec.interfaceBuilder(spiName).addModifiers(Modifier.PUBLIC);
    if (config.isGenerateJavadoc()) {
      ifaceBuilder.addJavadoc(
          "Runtime-provider SPI for the generated bindings.\n\n"
              + "<p>The embedder implements this interface once and installs the implementation\n"
              + "via {@link $LRegistry#install($L)}. Generated resource method bodies dispatch\n"
              + "through {@link $LRegistry#runtime()} to reach the installed provider.\n\n"
              + "<p>Emitted by webassembly4j-bindgen 2.0 (runtime-provider dispatch mode).\n",
          spiName, spiName, spiName);
    }

    for (final BindgenType resource : resources) {
      emitResourceDispatchers(ifaceBuilder, resource);
    }

    final JavaFile file =
        JavaFile.builder(config.getPackageName(), ifaceBuilder.build())
            .skipJavaLangImports(true)
            .indent("  ")
            .build();
    return new GeneratedSource(file);
  }

  /**
   * Generate the {@code <name>Registry} carrier class.
   *
   * @return the generated registry source
   */
  public GeneratedSource generateRegistry() {
    final String spiName = config.getRuntimeProviderName();
    final String registryName = spiName + "Registry";
    final ClassName spiType = ClassName.get(config.getPackageName(), spiName);

    final TypeSpec.Builder classBuilder =
        TypeSpec.classBuilder(registryName).addModifiers(Modifier.PUBLIC, Modifier.FINAL);
    if (config.isGenerateJavadoc()) {
      classBuilder.addJavadoc(
          "Single-slot registry for the {@link $L} runtime-provider SPI.\n\n"
              + "<p>Call {@link #install($L)} once from the embedder's startup path, before\n"
              + "invoking any generated resource method. Generated bodies reach the installed\n"
              + "provider via {@link #runtime()}, which throws {@link IllegalStateException} if\n"
              + "nothing was installed.\n\n"
              + "<p>Emitted by webassembly4j-bindgen 2.0.\n",
          spiName, spiName);
    }

    // Private constructor.
    classBuilder.addMethod(
        MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build());

    // volatile field.
    classBuilder.addField(
        com.squareup.javapoet.FieldSpec.builder(
                spiType, "provider", Modifier.PRIVATE, Modifier.STATIC, Modifier.VOLATILE)
            .build());

    classBuilder.addMethod(
        MethodSpec.methodBuilder("install")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(TypeName.VOID)
            .addParameter(spiType, "provider")
            .addStatement("$T.provider = $T.requireNonNull(provider, $S)", ClassName.get(config.getPackageName(), registryName), ClassName.get(Objects.class), "provider")
            .build());

    classBuilder.addMethod(
        MethodSpec.methodBuilder("uninstall")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(TypeName.VOID)
            .addStatement("$T.provider = null", ClassName.get(config.getPackageName(), registryName))
            .build());

    classBuilder.addMethod(
        MethodSpec.methodBuilder("runtime")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(spiType)
            .addStatement("$T r = provider", spiType)
            .beginControlFlow("if (r == null)")
            .addStatement(
                "throw new $T($S)",
                IllegalStateException.class,
                spiName
                    + " not installed; call "
                    + registryName
                    + ".install(...) before invoking generated bindings")
            .endControlFlow()
            .addStatement("return r")
            .build());

    final JavaFile file =
        JavaFile.builder(config.getPackageName(), classBuilder.build())
            .skipJavaLangImports(true)
            .indent("  ")
            .build();
    return new GeneratedSource(file);
  }

  /**
   * The camel-cased prefix used for every dispatch method on the resource. E.g.
   * "runtime-instance" → "runtimeInstance".
   */
  static String dispatchMethodPrefix(final String resourceWitName) {
    return JavaNaming.toFieldName(resourceWitName);
  }

  /**
   * The SPI dispatch method name for a resource method.
   *
   * <p>E.g. resource "runtime-instance" + method "call-export" → "runtimeInstanceCallExport".
   */
  static String dispatchMethodName(final String resourceWitName, final String methodWitName) {
    final String prefix = dispatchMethodPrefix(resourceWitName);
    final String capMethod = JavaNaming.toClassName(methodWitName);
    return prefix + capMethod;
  }

  /** The SPI dispatch method name for a WIT constructor. */
  static String dispatchConstructorName(final String resourceWitName) {
    return dispatchMethodPrefix(resourceWitName) + "Create";
  }

  /** The SPI dispatch method name for the intrinsic {@code close()}. */
  static String dispatchCloseName(final String resourceWitName) {
    return dispatchMethodPrefix(resourceWitName) + "Close";
  }

  private void emitResourceDispatchers(final TypeSpec.Builder ifaceBuilder, final BindgenType resource) {
    final String resourceWit = resource.getName();
    final ClassName resourceType =
        ClassName.get(config.getPackageName(), JavaNaming.toClassName(resourceWit));

    for (final BindgenFunction method : resource.getResourceMethods()) {
      if (method.isConstructor()) {
        ifaceBuilder.addMethod(dispatchForConstructor(resourceWit, method, resourceType));
      } else {
        ifaceBuilder.addMethod(dispatchForMethod(resourceWit, method));
      }
    }

    // Always emit a close dispatcher for every resource, even if the WIT declared
    // no explicit close() method — every generated resource has an
    // AutoCloseable.close() body that dispatches through the SPI.
    ifaceBuilder.addMethod(
        MethodSpec.methodBuilder(dispatchCloseName(resourceWit))
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .returns(TypeName.VOID)
            .addParameter(TypeName.LONG, "handle")
            .build());
  }

  private MethodSpec dispatchForConstructor(
      final String resourceWit, final BindgenFunction ctor, final ClassName resourceType) {
    final MethodSpec.Builder mb =
        MethodSpec.methodBuilder(dispatchConstructorName(resourceWit))
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .returns(resourceType);
    for (final BindgenParameter p : ctor.getParameters()) {
      mb.addParameter(mapType(p.getType()), JavaNaming.toParameterName(p.getName()));
    }
    return mb.build();
  }

  private MethodSpec dispatchForMethod(final String resourceWit, final BindgenFunction method) {
    final MethodSpec.Builder mb =
        MethodSpec.methodBuilder(dispatchMethodName(resourceWit, method.getName()))
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT);
    if (!method.isStatic()) {
      mb.addParameter(TypeName.LONG, "handle");
    }
    for (final BindgenParameter p : method.getParameters()) {
      mb.addParameter(mapType(p.getType()), JavaNaming.toParameterName(p.getName()));
    }
    if (method.hasReturnType()) {
      mb.returns(mapType(method.getReturnType().get()));
    } else {
      mb.returns(TypeName.VOID);
    }
    return mb.build();
  }

  private TypeName mapType(final BindgenType type) {
    switch (type.getKind()) {
      case PRIMITIVE:
        return typeRegistry.mapWitPrimitive(type.getName());
      case LIST:
        return typeRegistry.mapList(mapType(type.getElementType().get()));
      case OPTION:
        return typeRegistry.mapOption(mapType(type.getElementType().get()));
      case RESULT:
        final TypeName ok = type.getOkType().map(this::mapType).orElse(null);
        final TypeName err = type.getErrorType().map(this::mapType).orElse(null);
        return typeRegistry.mapResult(ok, err);
      case REFERENCE:
        return typeRegistry.mapGeneratedType(type.getReferencedTypeName().get());
      default:
        return typeRegistry.mapGeneratedType(type.getName());
    }
  }
}
