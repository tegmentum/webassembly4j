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
import ai.tegmentum.webassembly4j.bindgen.model.BindgenField;
import ai.tegmentum.webassembly4j.bindgen.model.BindgenFunction;
import ai.tegmentum.webassembly4j.bindgen.model.BindgenParameter;
import ai.tegmentum.webassembly4j.bindgen.model.BindgenType;
import ai.tegmentum.webassembly4j.bindgen.model.BindgenVariantCase;
import ai.tegmentum.webassembly4j.bindgen.util.JavaNaming;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import javax.lang.model.element.Modifier;

/**
 * Code generator for Java 17+ using records and sealed interfaces.
 *
 * <p>This generator produces:
 *
 * <ul>
 *   <li>Records for WIT record types
 *   <li>Sealed interfaces with record implementations for variants
 *   <li>Java enums for WIT enums
 *   <li>Interfaces with abstract methods for WIT functions
 * </ul>
 */
public final class ModernCodeGenerator extends JavaCodeGenerator {

  /**
   * Creates a new ModernCodeGenerator.
   *
   * @param config the bindgen configuration
   */
  public ModernCodeGenerator(final BindgenConfig config) {
    super(config);
  }

  @Override
  protected TypeSpec generateRecord(final BindgenType type) {
    String className = JavaNaming.toClassName(type.getName());

    // Note: JavaPoet doesn't directly support records, so we generate a class
    // that mimics record semantics. Users can manually convert to records.
    TypeSpec.Builder classBuilder =
        TypeSpec.classBuilder(className).addModifiers(Modifier.PUBLIC, Modifier.FINAL);

    // Add documentation
    if (config.isGenerateJavadoc() && type.getDocumentation().isPresent()) {
      classBuilder.addJavadoc(type.getDocumentation().get() + "\n");
      classBuilder.addJavadoc("\n<p>This class is designed to be used as a record.\n");
    }

    // Add private final fields
    for (BindgenField field : type.getFields()) {
      String fieldName = JavaNaming.toFieldName(field.getName());
      TypeName fieldType = mapType(field.getType());
      classBuilder.addField(fieldType, fieldName, Modifier.PRIVATE, Modifier.FINAL);
    }

    // Add constructor
    MethodSpec.Builder constructorBuilder =
        MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC);

    for (BindgenField field : type.getFields()) {
      String fieldName = JavaNaming.toFieldName(field.getName());
      TypeName fieldType = mapType(field.getType());
      constructorBuilder.addParameter(ParameterSpec.builder(fieldType, fieldName).build());
      constructorBuilder.addStatement("this.$N = $N", fieldName, fieldName);
    }

    classBuilder.addMethod(constructorBuilder.build());

    // Add getters (record-style: fieldName() instead of getFieldName())
    for (BindgenField field : type.getFields()) {
      String fieldName = JavaNaming.toFieldName(field.getName());
      TypeName fieldType = mapType(field.getType());

      MethodSpec.Builder getterBuilder =
          MethodSpec.methodBuilder(fieldName)
              .addModifiers(Modifier.PUBLIC)
              .returns(fieldType)
              .addStatement("return this.$N", fieldName);

      if (config.isGenerateJavadoc() && field.getDocumentation().isPresent()) {
        getterBuilder.addJavadoc(field.getDocumentation().get() + "\n");
      }

      classBuilder.addMethod(getterBuilder.build());
    }

    // Add equals
    classBuilder.addMethod(generateEquals(type, className));

    // Add hashCode
    classBuilder.addMethod(generateHashCode(type));

    // Add toString
    classBuilder.addMethod(generateToString(type, className, "[", "]"));

    return classBuilder.build();
  }

  @Override
  protected TypeSpec generateVariant(final BindgenType type) {
    String interfaceName = JavaNaming.toClassName(type.getName());

    // Create sealed interface
    TypeSpec.Builder interfaceBuilder =
        TypeSpec.interfaceBuilder(interfaceName).addModifiers(Modifier.PUBLIC);

    // Add documentation
    if (config.isGenerateJavadoc() && type.getDocumentation().isPresent()) {
      interfaceBuilder.addJavadoc(type.getDocumentation().get() + "\n");
      interfaceBuilder.addJavadoc("\n<p>This is a sealed interface for variant type.\n");
    }

    // Note: JavaPoet doesn't support 'sealed' keyword directly
    // We generate the interface and nested record-like classes

    // Generate case classes as nested static classes
    for (BindgenVariantCase variantCase : type.getCases()) {
      TypeSpec caseClass = generateVariantCase(interfaceName, variantCase);
      interfaceBuilder.addType(caseClass);
    }

    return interfaceBuilder.build();
  }

  private TypeSpec generateVariantCase(
      final String parentName, final BindgenVariantCase variantCase) {
    String caseName = JavaNaming.toClassName(variantCase.getName());
    ClassName parentType = ClassName.get(config.getPackageName(), parentName);

    TypeSpec.Builder caseBuilder =
        TypeSpec.classBuilder(caseName)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
            .addSuperinterface(parentType);

    // Add documentation
    if (config.isGenerateJavadoc() && variantCase.getDocumentation().isPresent()) {
      caseBuilder.addJavadoc(variantCase.getDocumentation().get() + "\n");
    }

    if (variantCase.hasPayload()) {
      BindgenType payloadType = variantCase.getPayload().get();
      TypeName javaType = mapType(payloadType);

      // Add value field
      caseBuilder.addField(javaType, "value", Modifier.PRIVATE, Modifier.FINAL);

      // Add constructor
      caseBuilder.addMethod(
          MethodSpec.constructorBuilder()
              .addModifiers(Modifier.PUBLIC)
              .addParameter(javaType, "value")
              .addStatement("this.value = value")
              .build());

      // Add getter
      caseBuilder.addMethod(
          MethodSpec.methodBuilder("value")
              .addModifiers(Modifier.PUBLIC)
              .returns(javaType)
              .addStatement("return this.value")
              .build());
    } else {
      // Add private constructor for singleton-like behavior
      caseBuilder.addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC).build());
    }

    return caseBuilder.build();
  }

  @Override
  protected TypeSpec generateResource(final BindgenType type) {
    String className = JavaNaming.toClassName(type.getName());

    TypeSpec.Builder classBuilder =
        TypeSpec.classBuilder(className)
            .addModifiers(Modifier.PUBLIC)
            .addSuperinterface(ClassName.get(AutoCloseable.class));

    // Add documentation
    if (config.isGenerateJavadoc()) {
      classBuilder.addJavadoc("Resource type: $L\n", type.getName());
      classBuilder.addJavadoc("\n<p>This resource should be closed after use.\n");
    }

    // Add handle field
    classBuilder.addField(TypeName.LONG, "handle", Modifier.PRIVATE, Modifier.FINAL);

    // Emit the handle-holding constructor. When the WIT resource declared
    // its own `constructor(...)`, the visibility depends on dispatch mode:
    //   * Legacy (throwing bodies): protected — external callers must go
    //     through the WIT `create(...)` factory; only subclasses (host
    //     adapters) may wrap an existing raw handle.
    //   * Bindgen 2.0 SPI-dispatch: public — the SPI impl lives outside
    //     the generated package and must construct resources to fulfil
    //     the `WitResult<Resource, Error> instantiate(...)` /
    //     `Resource create(...)` contracts. The registry entry-point is
    //     the enforcement boundary, not the ctor's visibility.
    final boolean hasWitConstructor =
        type.getResourceMethods().stream().anyMatch(BindgenFunction::isConstructor);
    final Modifier ctorVis =
        (hasWitConstructor && !isSpiDispatchMode()) ? Modifier.PROTECTED : Modifier.PUBLIC;
    classBuilder.addMethod(
        MethodSpec.constructorBuilder()
            .addModifiers(ctorVis)
            .addParameter(TypeName.LONG, "handle")
            .addStatement("this.handle = handle")
            .build());

    // Add handle getter
    classBuilder.addMethod(
        MethodSpec.methodBuilder("handle")
            .addModifiers(Modifier.PUBLIC)
            .returns(TypeName.LONG)
            .addStatement("return this.handle")
            .build());

    // Add close method. In bindgen 2.0 SPI-dispatch mode the body routes
    // through the installed runtime provider; in the legacy mode it stays a
    // no-op (matches bindgen 1.x behaviour that host adapters override).
    final MethodSpec.Builder closeBuilder =
        MethodSpec.methodBuilder("close")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override.class);
    if (isSpiDispatchMode()) {
      closeBuilder.addStatement(
          "$T.runtime().$L(this.handle)", registryClass(), RuntimeProviderCodeGenerator.dispatchCloseName(type.getName()));
    } else {
      closeBuilder.addComment("Resource cleanup - to be implemented by runtime");
    }
    classBuilder.addMethod(closeBuilder.build());

    // Emit the resource's WIT-declared methods. In bindgen 2.0 SPI-dispatch
    // mode bodies delegate to the installed runtime provider; in the legacy
    // mode they throw UnsupportedOperationException with a "not yet wired"
    // message, matching bindgen 1.x behaviour that the shape tests pin down.
    for (final BindgenFunction method : type.getResourceMethods()) {
      classBuilder.addMethod(generateResourceMethod(type.getName(), className, method));
    }

    return classBuilder.build();
  }

  private MethodSpec generateResourceMethod(
      final String resourceWitName, final String className, final BindgenFunction method) {
    final boolean spi = isSpiDispatchMode();
    if (method.isConstructor()) {
      // Emit as a static factory `create` — matches the WIT constructor
      // shape (constructs a new resource from primitive args) but doesn't
      // collide with Java's ctor rules (return type, no name).
      MethodSpec.Builder factory =
          MethodSpec.methodBuilder("create")
              .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
              .returns(ClassName.get(config.getPackageName(), className));
      for (BindgenParameter p : method.getParameters()) {
        factory.addParameter(mapType(p.getType()), JavaNaming.toParameterName(p.getName()));
      }
      if (spi) {
        factory.addStatement(
            "return $T.runtime().$L($L)",
            registryClass(),
            RuntimeProviderCodeGenerator.dispatchConstructorName(resourceWitName),
            parameterForwardList(method));
      } else {
        factory.addStatement(
            "throw new $T($S)",
            UnsupportedOperationException.class,
            "wasmos:host/embedder constructor dispatch not yet wired");
      }
      return factory.build();
    }
    final String name = JavaNaming.toMethodName(method.getName());
    MethodSpec.Builder mb = MethodSpec.methodBuilder(name).addModifiers(Modifier.PUBLIC);
    if (method.isStatic()) {
      mb.addModifiers(Modifier.STATIC);
    }
    for (BindgenParameter p : method.getParameters()) {
      mb.addParameter(mapType(p.getType()), JavaNaming.toParameterName(p.getName()));
    }
    final String dispatchName =
        RuntimeProviderCodeGenerator.dispatchMethodName(resourceWitName, method.getName());
    if (method.hasReturnType()) {
      mb.returns(mapType(method.getReturnType().get()));
      if (spi) {
        if (method.isStatic()) {
          mb.addStatement(
              "return $T.runtime().$L($L)",
              registryClass(),
              dispatchName,
              parameterForwardList(method));
        } else {
          mb.addStatement(
              "return $T.runtime().$L(this.handle$L)",
              registryClass(),
              dispatchName,
              parameterForwardListPrefixed(method));
        }
      } else {
        mb.addStatement(
            "throw new $T($S)",
            UnsupportedOperationException.class,
            "wasmos:host/embedder method dispatch not yet wired");
      }
    } else {
      if (spi) {
        if (method.isStatic()) {
          mb.addStatement(
              "$T.runtime().$L($L)",
              registryClass(),
              dispatchName,
              parameterForwardList(method));
        } else {
          mb.addStatement(
              "$T.runtime().$L(this.handle$L)",
              registryClass(),
              dispatchName,
              parameterForwardListPrefixed(method));
        }
      } else {
        mb.addStatement(
            "throw new $T($S)",
            UnsupportedOperationException.class,
            "wasmos:host/embedder method dispatch not yet wired");
      }
    }
    return mb.build();
  }

  private boolean isSpiDispatchMode() {
    final String spi = config.getRuntimeProviderName();
    return spi != null && !spi.isEmpty();
  }

  private ClassName registryClass() {
    return ClassName.get(config.getPackageName(), config.getRuntimeProviderName() + "Registry");
  }

  private static String parameterForwardList(final BindgenFunction method) {
    final StringBuilder sb = new StringBuilder();
    boolean first = true;
    for (BindgenParameter p : method.getParameters()) {
      if (!first) {
        sb.append(", ");
      }
      sb.append(JavaNaming.toParameterName(p.getName()));
      first = false;
    }
    return sb.toString();
  }

  /**
   * Same as {@link #parameterForwardList} but always prefixed with a leading ", " so it can be
   * concatenated after {@code this.handle} in the dispatch call site. Empty when the method takes
   * no user-supplied parameters.
   */
  private static String parameterForwardListPrefixed(final BindgenFunction method) {
    final String list = parameterForwardList(method);
    if (list.isEmpty()) {
      return "";
    }
    return ", " + list;
  }
}
