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
import ai.tegmentum.webassembly4j.bindgen.model.BindgenType;
import ai.tegmentum.webassembly4j.bindgen.model.BindgenVariantCase;
import ai.tegmentum.webassembly4j.bindgen.util.JavaNaming;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import javax.lang.model.element.Modifier;

/**
 * Code generator for Java 8+ using POJOs and visitor pattern.
 *
 * <p>This generator produces:
 *
 * <ul>
 *   <li>POJOs with private final fields for WIT record types
 *   <li>Abstract classes with visitor pattern for variants
 *   <li>Builder classes for complex types
 *   <li>Java enums for WIT enums
 * </ul>
 */
public final class LegacyCodeGenerator extends JavaCodeGenerator {

  /**
   * Creates a new LegacyCodeGenerator.
   *
   * @param config the bindgen configuration
   */
  public LegacyCodeGenerator(final BindgenConfig config) {
    super(config);
  }

  @Override
  protected TypeSpec generateRecord(final BindgenType type) {
    String className = JavaNaming.toClassName(type.getName());

    TypeSpec.Builder classBuilder =
        TypeSpec.classBuilder(className).addModifiers(Modifier.PUBLIC, Modifier.FINAL);

    // Add documentation
    if (config.isGenerateJavadoc() && type.getDocumentation().isPresent()) {
      classBuilder.addJavadoc(type.getDocumentation().get() + "\n");
    }

    // Add private final fields
    for (BindgenField field : type.getFields()) {
      String fieldName = JavaNaming.toFieldName(field.getName());
      TypeName fieldType = mapType(field.getType());
      classBuilder.addField(fieldType, fieldName, Modifier.PRIVATE, Modifier.FINAL);
    }

    // Add constructor
    classBuilder.addMethod(generateConstructor(type));

    // Add getters (traditional getXxx style)
    for (BindgenField field : type.getFields()) {
      classBuilder.addMethod(generateGetter(field));
    }

    // Add equals, hashCode, toString
    classBuilder.addMethod(generateEquals(type, className));
    classBuilder.addMethod(generateHashCode(type));
    classBuilder.addMethod(generateToString(type, className, "{", "}"));

    // Add builder if configured
    if (config.isGenerateBuilders() && !type.getFields().isEmpty()) {
      classBuilder.addType(generateBuilder(type, className));
      classBuilder.addMethod(generateBuilderMethod(className));
    }

    return classBuilder.build();
  }

  @Override
  protected TypeSpec generateVariant(final BindgenType type) {
    String className = JavaNaming.toClassName(type.getName());

    // Create abstract base class
    TypeSpec.Builder classBuilder =
        TypeSpec.classBuilder(className).addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT);

    // Add documentation
    if (config.isGenerateJavadoc() && type.getDocumentation().isPresent()) {
      classBuilder.addJavadoc(type.getDocumentation().get() + "\n");
    }

    // Generate visitor interface
    TypeSpec visitor = generateVisitor(type);
    classBuilder.addType(visitor);

    // Add abstract accept method
    classBuilder.addMethod(
        MethodSpec.methodBuilder("accept")
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .addTypeVariable(TypeVariableName.get("T"))
            .addParameter(ClassName.get("", "Visitor<T>"), "visitor")
            .returns(TypeVariableName.get("T"))
            .build());

    // Generate case classes as nested static classes
    for (BindgenVariantCase variantCase : type.getCases()) {
      TypeSpec caseClass = generateVariantCase(className, variantCase);
      classBuilder.addType(caseClass);
    }

    return classBuilder.build();
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
    }

    // Add handle field
    classBuilder.addField(TypeName.LONG, "handle", Modifier.PRIVATE, Modifier.FINAL);

    // Add constructor
    classBuilder.addMethod(
        MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(TypeName.LONG, "handle")
            .addStatement("this.handle = handle")
            .build());

    // Add handle getter
    classBuilder.addMethod(
        MethodSpec.methodBuilder("getHandle")
            .addModifiers(Modifier.PUBLIC)
            .returns(TypeName.LONG)
            .addStatement("return this.handle")
            .build());

    // Add close method
    classBuilder.addMethod(
        MethodSpec.methodBuilder("close")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override.class)
            .addComment("Resource cleanup - to be implemented by runtime")
            .build());

    return classBuilder.build();
  }

  private MethodSpec generateConstructor(final BindgenType type) {
    MethodSpec.Builder constructorBuilder =
        MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC);

    for (BindgenField field : type.getFields()) {
      String fieldName = JavaNaming.toFieldName(field.getName());
      TypeName fieldType = mapType(field.getType());
      constructorBuilder.addParameter(
          ParameterSpec.builder(fieldType, fieldName).addModifiers(Modifier.FINAL).build());
      constructorBuilder.addStatement("this.$N = $N", fieldName, fieldName);
    }

    return constructorBuilder.build();
  }

  private MethodSpec generateGetter(final BindgenField field) {
    String fieldName = JavaNaming.toFieldName(field.getName());
    String methodName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
    TypeName fieldType = mapType(field.getType());

    MethodSpec.Builder getterBuilder =
        MethodSpec.methodBuilder(methodName)
            .addModifiers(Modifier.PUBLIC)
            .returns(fieldType)
            .addStatement("return this.$N", fieldName);

    if (config.isGenerateJavadoc() && field.getDocumentation().isPresent()) {
      getterBuilder.addJavadoc(field.getDocumentation().get() + "\n");
      getterBuilder.addJavadoc("@return the $L value\n", fieldName);
    }

    return getterBuilder.build();
  }

  private TypeSpec generateBuilder(final BindgenType type, final String parentClassName) {
    TypeSpec.Builder builderBuilder =
        TypeSpec.classBuilder("Builder")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL);

    // Add fields
    for (BindgenField field : type.getFields()) {
      String fieldName = JavaNaming.toFieldName(field.getName());
      TypeName fieldType = mapType(field.getType());
      builderBuilder.addField(fieldType, fieldName, Modifier.PRIVATE);
    }

    // Add setter methods
    for (BindgenField field : type.getFields()) {
      String fieldName = JavaNaming.toFieldName(field.getName());
      TypeName fieldType = mapType(field.getType());

      builderBuilder.addMethod(
          MethodSpec.methodBuilder(fieldName)
              .addModifiers(Modifier.PUBLIC)
              .addParameter(
                  ParameterSpec.builder(fieldType, fieldName).addModifiers(Modifier.FINAL).build())
              .returns(ClassName.get("", "Builder"))
              .addStatement("this.$N = $N", fieldName, fieldName)
              .addStatement("return this")
              .build());
    }

    // Add build method
    MethodSpec.Builder buildMethod =
        MethodSpec.methodBuilder("build")
            .addModifiers(Modifier.PUBLIC)
            .returns(ClassName.get(config.getPackageName(), parentClassName));

    StringBuilder args = new StringBuilder();
    for (int i = 0; i < type.getFields().size(); i++) {
      BindgenField field = type.getFields().get(i);
      String fieldName = JavaNaming.toFieldName(field.getName());
      if (i > 0) {
        args.append(", ");
      }
      args.append(fieldName);
    }
    buildMethod.addStatement("return new $L($L)", parentClassName, args);

    builderBuilder.addMethod(buildMethod.build());

    return builderBuilder.build();
  }

  private MethodSpec generateBuilderMethod(final String className) {
    return MethodSpec.methodBuilder("builder")
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        .returns(ClassName.get("", "Builder"))
        .addStatement("return new Builder()")
        .build();
  }

  private TypeSpec generateVisitor(final BindgenType type) {
    TypeSpec.Builder visitorBuilder =
        TypeSpec.interfaceBuilder("Visitor")
            .addModifiers(Modifier.PUBLIC)
            .addTypeVariable(TypeVariableName.get("T"));

    for (BindgenVariantCase variantCase : type.getCases()) {
      String caseName = JavaNaming.toClassName(variantCase.getName());
      String methodName = "visit" + caseName;

      MethodSpec.Builder visitMethod =
          MethodSpec.methodBuilder(methodName)
              .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
              .returns(TypeVariableName.get("T"))
              .addParameter(ClassName.get("", caseName), "value");

      visitorBuilder.addMethod(visitMethod.build());
    }

    return visitorBuilder.build();
  }

  private TypeSpec generateVariantCase(
      final String parentName, final BindgenVariantCase variantCase) {
    String caseName = JavaNaming.toClassName(variantCase.getName());
    ClassName parentType = ClassName.get(config.getPackageName(), parentName);

    TypeSpec.Builder caseBuilder =
        TypeSpec.classBuilder(caseName)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
            .superclass(parentType);

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
              .addParameter(
                  ParameterSpec.builder(javaType, "value").addModifiers(Modifier.FINAL).build())
              .addStatement("this.value = value")
              .build());

      // Add getter
      caseBuilder.addMethod(
          MethodSpec.methodBuilder("getValue")
              .addModifiers(Modifier.PUBLIC)
              .returns(javaType)
              .addStatement("return this.value")
              .build());
    } else {
      // Add default constructor
      caseBuilder.addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC).build());
    }

    // Add accept method
    caseBuilder.addMethod(
        MethodSpec.methodBuilder("accept")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override.class)
            .addTypeVariable(TypeVariableName.get("T"))
            .addParameter(ClassName.get("", "Visitor<T>"), "visitor")
            .returns(TypeVariableName.get("T"))
            .addStatement("return visitor.visit$L(this)", caseName)
            .build());

    return caseBuilder.build();
  }
}
