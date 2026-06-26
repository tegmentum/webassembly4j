/*
 * Copyright 2024 Tegmentum AI
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

package ai.tegmentum.webassembly4j.bindgen.wit;

import ai.tegmentum.webassembly4j.bindgen.BindgenException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for WebAssembly Interface Type (WIT) definitions.
 *
 * <p>This class provides comprehensive parsing capabilities for WIT interface definitions,
 * including type definitions, function signatures, and interface metadata.
 *
 * @since 1.0.0
 */
public final class WitInterfaceParser {

  private static final Pattern INTERFACE_PATTERN =
      Pattern.compile("interface\\s+([a-zA-Z0-9_-]+)\\s*\\{([\\s\\S]*)\\}", Pattern.DOTALL);

  // Simplified pattern to prevent ReDoS - uses possessive quantifiers
  private static final Pattern FUNCTION_PATTERN =
      Pattern.compile("([a-zA-Z0-9_-]++)\\s*:\\s*func\\s*\\(([^)]*)\\)\\s*(?:->\\s*([^;]+))?");

  private static final Pattern TYPE_PATTERN =
      Pattern.compile("type\\s+([a-zA-Z0-9_-]+)\\s*=\\s*([^;]+)", Pattern.DOTALL);

  private static final Pattern RECORD_PATTERN =
      Pattern.compile("record\\s*\\{([\\s\\S]*)\\}", Pattern.DOTALL);

  private static final Pattern VARIANT_PATTERN =
      Pattern.compile("variant\\s*\\{([\\s\\S]*)\\}", Pattern.DOTALL);

  private static final Pattern ENUM_PATTERN =
      Pattern.compile("enum\\s*\\{([\\s\\S]*)\\}", Pattern.DOTALL);

  private static final Pattern FLAGS_PATTERN =
      Pattern.compile("flags\\s*\\{([\\s\\S]*)\\}", Pattern.DOTALL);

  // Standalone declarations: `record name { fields }` etc. — the
  // canonical WIT syntax used by wit-bindgen output and most
  // real-world WIT files. The `type X = ...` alias forms above are
  // also valid but less common in practice. Non-greedy braces are
  // safe because records/variants/enums/flags don't nest braces in
  // their bodies.
  private static final Pattern RECORD_DECLARATION_PATTERN =
      Pattern.compile("record\\s+([a-zA-Z0-9_-]+)\\s*\\{([\\s\\S]*?)\\}", Pattern.DOTALL);

  private static final Pattern VARIANT_DECLARATION_PATTERN =
      Pattern.compile("variant\\s+([a-zA-Z0-9_-]+)\\s*\\{([\\s\\S]*?)\\}", Pattern.DOTALL);

  private static final Pattern ENUM_DECLARATION_PATTERN =
      Pattern.compile("enum\\s+([a-zA-Z0-9_-]+)\\s*\\{([\\s\\S]*?)\\}", Pattern.DOTALL);

  private static final Pattern FLAGS_DECLARATION_PATTERN =
      Pattern.compile("flags\\s+([a-zA-Z0-9_-]+)\\s*\\{([\\s\\S]*?)\\}", Pattern.DOTALL);

  private static final int MAX_WIT_TEXT_LENGTH = 1024 * 1024; // 1MB limit

  private final Map<String, WitType> typeCache;

  /** Creates a new WIT interface parser. */
  public WitInterfaceParser() {
    this.typeCache = new HashMap<>();
    initializePrimitiveTypes();
  }

  /**
   * Parses a WIT interface definition from text.
   *
   * @param witText the WIT interface text
   * @param packageName the package name
   * @return the parsed interface definition
   * @throws BindgenException if parsing fails
   */
  public WitInterfaceDefinition parseInterface(final String witText, final String packageName)
      throws BindgenException {
    Objects.requireNonNull(witText, "witText");
    Objects.requireNonNull(packageName, "packageName");

    // Prevent ReDoS by rejecting excessively large WIT definitions
    if (witText.length() > MAX_WIT_TEXT_LENGTH) {
      throw new BindgenException(
          "WIT definition exceeds maximum length of " + MAX_WIT_TEXT_LENGTH + " characters");
    }

    try {
      // Parse the interface declaration
      final Matcher interfaceMatcher = INTERFACE_PATTERN.matcher(witText);
      if (!interfaceMatcher.find()) {
        throw new BindgenException("Invalid WIT interface: no interface declaration found");
      }

      final String interfaceName = interfaceMatcher.group(1);
      final String interfaceBody = interfaceMatcher.group(2);

      // Parse types, functions, and exports
      final Map<String, WitType> types = parseTypes(interfaceBody);
      final Map<String, WitFunction> functions = parseFunctions(interfaceBody, types);
      final List<String> exports = parseExports(interfaceBody);

      return new WitInterfaceDefinitionImpl(
          interfaceName,
          "1.0", // Default version
          packageName,
          functions,
          types,
          List.of(),
          exports,
          witText);

    } catch (final Exception e) {
      throw new BindgenException("Failed to parse WIT interface", e);
    }
  }

  /**
   * Parses type definitions from WIT interface body.
   *
   * @param interfaceBody the interface body text
   * @return map of type name to type definition
   * @throws BindgenException if parsing fails
   */
  private Map<String, WitType> parseTypes(final String interfaceBody) throws BindgenException {
    final Map<String, WitType> types = new HashMap<>();

    // Pass 1: standalone declarations (`record name { ... }`,
    // `variant name { ... }`, `enum name { ... }`,
    // `flags name { ... }`). This is the canonical WIT syntax —
    // running it first lets the second pass resolve aliases that
    // reference them.
    collectStandalone(
        RECORD_DECLARATION_PATTERN,
        interfaceBody,
        types,
        (name, body) -> parseRecordType(name, body));
    collectStandalone(
        VARIANT_DECLARATION_PATTERN,
        interfaceBody,
        types,
        (name, body) -> parseVariantType(name, body));
    collectStandalone(
        ENUM_DECLARATION_PATTERN,
        interfaceBody,
        types,
        (name, body) -> parseEnumType(name, body));
    collectStandalone(
        FLAGS_DECLARATION_PATTERN,
        interfaceBody,
        types,
        (name, body) -> parseFlagsType(name, body));

    // Pass 2: `type X = ...;` alias declarations.
    final Matcher typeMatcher = TYPE_PATTERN.matcher(interfaceBody);

    while (typeMatcher.find()) {
      final String typeName = typeMatcher.group(1);
      final String typeDefinition = typeMatcher.group(2).trim();

      final WitType witType = parseTypeDefinition(typeName, typeDefinition);
      types.put(typeName, witType);
      typeCache.put(typeName, witType);
    }

    return types;
  }

  /**
   * Functional interface for parsing a standalone declaration's body. Matches the
   * {@code parseRecordType} / {@code parseVariantType} / {@code parseEnumType} /
   * {@code parseFlagsType} signatures so they compose with {@link
   * #collectStandalone(Pattern, String, Map, StandaloneBodyParser)}.
   */
  @FunctionalInterface
  private interface StandaloneBodyParser {
    WitType parse(String typeName, String body) throws BindgenException;
  }

  /**
   * Run a standalone-declaration pattern (e.g. {@code record name { fields }}) across
   * the interface body and register every match into {@code types} and the resolution
   * cache.
   */
  private void collectStandalone(
      final Pattern pattern,
      final String interfaceBody,
      final Map<String, WitType> types,
      final StandaloneBodyParser bodyParser)
      throws BindgenException {
    final Matcher m = pattern.matcher(interfaceBody);
    while (m.find()) {
      final String typeName = m.group(1);
      final String body = m.group(2);
      final WitType witType = bodyParser.parse(typeName, body);
      types.put(typeName, witType);
      typeCache.put(typeName, witType);
    }
  }

  /**
   * Parses a single type definition.
   *
   * @param typeName the type name
   * @param definition the type definition
   * @return the parsed WIT type
   * @throws BindgenException if parsing fails
   */
  private WitType parseTypeDefinition(final String typeName, final String definition)
      throws BindgenException {
    final String trimmed = definition.trim();

    // Check for primitive types
    try {
      final WitPrimitiveType primitive = WitPrimitiveType.fromString(trimmed);
      return WitType.primitive(primitive);
    } catch (final IllegalArgumentException e) {
      // Not a primitive type, continue parsing
    }

    // Check for record type
    final Matcher recordMatcher = RECORD_PATTERN.matcher(trimmed);
    if (recordMatcher.matches()) {
      return parseRecordType(typeName, recordMatcher.group(1));
    }

    // Check for variant type
    final Matcher variantMatcher = VARIANT_PATTERN.matcher(trimmed);
    if (variantMatcher.matches()) {
      return parseVariantType(typeName, variantMatcher.group(1));
    }

    // Check for enum type
    final Matcher enumMatcher = ENUM_PATTERN.matcher(trimmed);
    if (enumMatcher.matches()) {
      return parseEnumType(typeName, enumMatcher.group(1));
    }

    // Check for flags type
    final Matcher flagsMatcher = FLAGS_PATTERN.matcher(trimmed);
    if (flagsMatcher.matches()) {
      return parseFlagsType(typeName, flagsMatcher.group(1));
    }

    // Check for list type
    if (trimmed.startsWith("list<") && trimmed.endsWith(">")) {
      final String elementTypeName = trimmed.substring(5, trimmed.length() - 1).trim();
      final WitType elementType = resolveType(elementTypeName);
      return WitType.list(elementType);
    }

    // Check for option type
    if (trimmed.startsWith("option<") && trimmed.endsWith(">")) {
      final String innerTypeName = trimmed.substring(7, trimmed.length() - 1).trim();
      final WitType innerType = resolveType(innerTypeName);
      return WitType.option(innerType);
    }

    // Check for result type
    if (trimmed.startsWith("result<") && trimmed.endsWith(">")) {
      return parseResultType(trimmed);
    }

    // Check for resource type
    if (trimmed.startsWith("resource")) {
      return WitType.resource(typeName, typeName);
    }

    throw new BindgenException("Unknown type definition: " + definition);
  }

  /**
   * Parses a record type definition.
   *
   * @param typeName the type name
   * @param fieldsText the fields definition text
   * @return the parsed record type
   * @throws BindgenException if parsing fails
   */
  private WitType parseRecordType(final String typeName, final String fieldsText)
      throws BindgenException {
    final Map<String, WitType> fields = new HashMap<>();
    final List<String> fieldDefs = splitTopLevel(stripComments(fieldsText), ',');

    for (final String fieldDef : fieldDefs) {
      final String trimmed = fieldDef.trim();
      if (trimmed.isEmpty()) {
        continue;
      }

      // Split on the FIRST colon — type expressions like
      // `list<u32>` don't contain colons but type aliases that
      // resolve to records aren't allowed inline either, so the
      // first `:` is the field/type separator.
      final int colon = trimmed.indexOf(':');
      if (colon < 0) {
        throw new BindgenException("Invalid record field definition: " + fieldDef);
      }
      final String fieldName = trimmed.substring(0, colon).trim();
      final String fieldTypeName = trimmed.substring(colon + 1).trim();
      final WitType fieldType = resolveType(fieldTypeName);
      fields.put(fieldName, fieldType);
    }

    return WitType.record(typeName, fields);
  }

  /**
   * Strip WIT doc-comment lines ({@code ///…}) and plain {@code //…} comments
   * from declaration bodies. Mutating the body before field/case splitting avoids
   * comma-inside-comment surprises.
   */
  private static String stripComments(final String body) {
    final StringBuilder out = new StringBuilder(body.length());
    for (final String line : body.split("\n", -1)) {
      final int commentStart = indexOfLineComment(line);
      if (commentStart < 0) {
        out.append(line).append('\n');
      } else {
        out.append(line, 0, commentStart).append('\n');
      }
    }
    return out.toString();
  }

  /**
   * Find the first {@code //} that starts a WIT line comment, respecting
   * "…" strings so a {@code //} inside a string literal isn't misread.
   */
  private static int indexOfLineComment(final String line) {
    boolean inString = false;
    for (int i = 0; i < line.length() - 1; i++) {
      final char c = line.charAt(i);
      if (c == '"' && (i == 0 || line.charAt(i - 1) != '\\')) {
        inString = !inString;
        continue;
      }
      if (!inString && c == '/' && line.charAt(i + 1) == '/') {
        return i;
      }
    }
    return -1;
  }

  /**
   * Split {@code text} on top-level occurrences of {@code separator}, respecting
   * angle-bracket nesting ({@code list<u32, u32>}), paren nesting
   * ({@code case(payload)}), and double-quoted strings.
   */
  private static List<String> splitTopLevel(final String text, final char separator) {
    final List<String> out = new ArrayList<>();
    int depthAngle = 0;
    int depthParen = 0;
    boolean inString = false;
    final StringBuilder current = new StringBuilder();
    for (int i = 0; i < text.length(); i++) {
      final char c = text.charAt(i);
      if (c == '"' && (i == 0 || text.charAt(i - 1) != '\\')) {
        inString = !inString;
        current.append(c);
        continue;
      }
      if (!inString) {
        if (c == '<') {
          depthAngle++;
        } else if (c == '>') {
          depthAngle = Math.max(0, depthAngle - 1);
        } else if (c == '(') {
          depthParen++;
        } else if (c == ')') {
          depthParen = Math.max(0, depthParen - 1);
        }
      }
      if (!inString && depthAngle == 0 && depthParen == 0 && c == separator) {
        out.add(current.toString());
        current.setLength(0);
      } else {
        current.append(c);
      }
    }
    if (current.length() > 0) {
      out.add(current.toString());
    }
    return out;
  }

  /**
   * Parses a variant type definition.
   *
   * @param typeName the type name
   * @param casesText the cases definition text
   * @return the parsed variant type
   * @throws BindgenException if parsing fails
   */
  private WitType parseVariantType(final String typeName, final String casesText)
      throws BindgenException {
    final Map<String, Optional<WitType>> cases = new HashMap<>();
    final List<String> caseDefs = splitTopLevel(stripComments(casesText), ',');

    for (final String caseDef : caseDefs) {
      final String trimmed = caseDef.trim();
      if (trimmed.isEmpty()) {
        continue;
      }

      if (trimmed.contains("(")) {
        // Case with payload
        final int parenIndex = trimmed.indexOf('(');
        final String caseName = trimmed.substring(0, parenIndex).trim();
        final String payloadType =
            trimmed.substring(parenIndex + 1, trimmed.lastIndexOf(')')).trim();
        final WitType caseType = resolveType(payloadType);
        cases.put(caseName, Optional.of(caseType));
      } else {
        // Case without payload
        cases.put(trimmed, Optional.empty());
      }
    }

    return WitType.variant(typeName, cases);
  }

  /**
   * Parses an enum type definition.
   *
   * @param typeName the type name
   * @param valuesText the values definition text
   * @return the parsed enum type
   * @throws BindgenException if parsing fails
   */
  private WitType parseEnumType(final String typeName, final String valuesText)
      throws BindgenException {
    final List<String> values = new ArrayList<>();
    final List<String> valueDefs = splitTopLevel(stripComments(valuesText), ',');

    for (final String valueDef : valueDefs) {
      final String trimmed = valueDef.trim();
      if (!trimmed.isEmpty()) {
        values.add(trimmed);
      }
    }

    return WitType.enumType(typeName, values);
  }

  /**
   * Parses a flags type definition.
   *
   * @param typeName the type name
   * @param flagsText the flags definition text
   * @return the parsed flags type
   * @throws BindgenException if parsing fails
   */
  private WitType parseFlagsType(final String typeName, final String flagsText)
      throws BindgenException {
    final List<String> flags = new ArrayList<>();
    final List<String> flagDefs = splitTopLevel(stripComments(flagsText), ',');

    for (final String flagDef : flagDefs) {
      final String trimmed = flagDef.trim();
      if (!trimmed.isEmpty()) {
        flags.add(trimmed);
      }
    }

    return WitType.flags(typeName, flags);
  }

  /**
   * Parses a result type definition.
   *
   * @param definition the result type definition
   * @return the parsed result type
   * @throws BindgenException if parsing fails
   */
  private WitType parseResultType(final String definition) throws BindgenException {
    final String content = definition.substring(7, definition.length() - 1).trim();

    if (content.isEmpty()) {
      return WitType.result(Optional.empty(), Optional.empty());
    }

    final String[] parts = content.split(",");
    if (parts.length > 2) {
      throw new BindgenException("Result type can have at most two type parameters");
    }

    Optional<WitType> okType = Optional.empty();
    Optional<WitType> errorType = Optional.empty();

    if (parts.length >= 1 && !parts[0].trim().equals("_")) {
      okType = Optional.of(resolveType(parts[0].trim()));
    }

    if (parts.length == 2 && !parts[1].trim().equals("_")) {
      errorType = Optional.of(resolveType(parts[1].trim()));
    }

    return WitType.result(okType, errorType);
  }

  /**
   * Parses function definitions from WIT interface body.
   *
   * @param interfaceBody the interface body text
   * @param types the available types
   * @return map of function name to function definition
   * @throws BindgenException if parsing fails
   */
  private Map<String, WitFunction> parseFunctions(
      final String interfaceBody, final Map<String, WitType> types) throws BindgenException {
    final Map<String, WitFunction> functions = new HashMap<>();
    final Matcher functionMatcher = FUNCTION_PATTERN.matcher(interfaceBody);

    while (functionMatcher.find()) {
      final String functionName = functionMatcher.group(1);
      final String parametersText = functionMatcher.group(2);
      final String returnTypeText = functionMatcher.group(3);

      final List<WitParameter> parameters = parseParameters(parametersText);
      final List<WitType> returnTypes = parseReturnTypes(returnTypeText);

      final WitFunction function =
          new WitFunction(
              functionName,
              parameters,
              returnTypes,
              false, // Not async by default
              Optional.empty());

      functions.put(functionName, function);
    }

    return functions;
  }

  /**
   * Parses function parameters.
   *
   * @param parametersText the parameters definition text
   * @return list of parsed parameters
   * @throws BindgenException if parsing fails
   */
  private List<WitParameter> parseParameters(final String parametersText) throws BindgenException {
    final List<WitParameter> parameters = new ArrayList<>();

    if (parametersText == null || parametersText.trim().isEmpty()) {
      return parameters;
    }

    final String[] paramDefs = splitRespectingBraces(parametersText);
    for (final String paramDef : paramDefs) {
      final String trimmed = paramDef.trim();
      if (trimmed.isEmpty()) {
        continue;
      }

      final String[] parts = trimmed.split(":");
      if (parts.length != 2) {
        throw new BindgenException("Invalid parameter definition: " + paramDef);
      }

      final String paramName = parts[0].trim();
      final String paramTypeName = parts[1].trim();
      final WitType paramType = resolveType(paramTypeName);

      parameters.add(
          new WitParameter(
              paramName,
              paramType,
              false, // Not optional by default
              Optional.empty()));
    }

    return parameters;
  }

  /**
   * Parses function return types.
   *
   * @param returnTypeText the return type definition text
   * @return list of parsed return types
   * @throws BindgenException if parsing fails
   */
  private List<WitType> parseReturnTypes(final String returnTypeText) throws BindgenException {
    final List<WitType> returnTypes = new ArrayList<>();

    if (returnTypeText == null || returnTypeText.trim().isEmpty()) {
      return returnTypes;
    }

    // For now, assume single return type
    final WitType returnType = resolveType(returnTypeText.trim());
    returnTypes.add(returnType);

    return returnTypes;
  }

  /**
   * Parses export declarations (simplified implementation).
   *
   * @param interfaceBody the interface body text
   * @return list of export names
   */
  private List<String> parseExports(final String interfaceBody) {
    final List<String> exports = new ArrayList<>();
    // Simplified - exports are typically function names
    final Matcher functionMatcher = FUNCTION_PATTERN.matcher(interfaceBody);
    while (functionMatcher.find()) {
      exports.add(functionMatcher.group(1));
    }
    return exports;
  }

  /**
   * Resolves a type name to a WIT type.
   *
   * @param typeName the type name
   * @return the resolved type
   * @throws BindgenException if type cannot be resolved
   */
  private WitType resolveType(final String typeName) throws BindgenException {
    // Check cache first
    final WitType cachedType = typeCache.get(typeName);
    if (cachedType != null) {
      return cachedType;
    }

    // Try to parse as primitive type
    try {
      final WitPrimitiveType primitive = WitPrimitiveType.fromString(typeName);
      return WitType.primitive(primitive);
    } catch (final IllegalArgumentException e) {
      // Not a primitive type
    }

    // Try to parse as inline type definition (list<T>, option<T>, etc.)
    try {
      return parseTypeDefinition(typeName, typeName);
    } catch (final BindgenException e) {
      // Not a parseable type definition
    }

    throw new BindgenException("Unknown type: " + typeName);
  }

  /** Initializes primitive types in the type cache. */
  private void initializePrimitiveTypes() {
    for (final WitPrimitiveType primitive : WitPrimitiveType.values()) {
      final WitType type = WitType.primitive(primitive);
      typeCache.put(primitive.getWitTypeName(), type);
      typeCache.put(primitive.name().toLowerCase(Locale.ROOT), type);
    }
  }

  /**
   * Splits a comma-separated string while respecting braces and angle brackets.
   *
   * @param text the text to split
   * @return array of split parts
   */
  private static String[] splitRespectingBraces(final String text) {
    final List<String> parts = new ArrayList<>();
    final StringBuilder current = new StringBuilder();
    int braceDepth = 0;
    int angleDepth = 0;

    for (final char c : text.toCharArray()) {
      if (c == '{') {
        braceDepth++;
        current.append(c);
      } else if (c == '}') {
        braceDepth--;
        current.append(c);
      } else if (c == '<') {
        angleDepth++;
        current.append(c);
      } else if (c == '>') {
        angleDepth--;
        current.append(c);
      } else if (c == ',' && braceDepth == 0 && angleDepth == 0) {
        parts.add(current.toString());
        current.setLength(0);
      } else {
        current.append(c);
      }
    }

    if (current.length() > 0) {
      parts.add(current.toString());
    }

    return parts.toArray(new String[0]);
  }

  /** Implementation of WitInterfaceDefinition. */
  private static final class WitInterfaceDefinitionImpl implements WitInterfaceDefinition {
    private final String name;
    private final String version;
    private final String packageName;
    private final Map<String, WitFunction> functions;
    private final Map<String, WitType> types;
    private final List<String> imports;
    private final List<String> exports;
    private final String witText;

    public WitInterfaceDefinitionImpl(
        final String name,
        final String version,
        final String packageName,
        final Map<String, WitFunction> functions,
        final Map<String, WitType> types,
        final List<String> imports,
        final List<String> exports,
        final String witText) {
      this.name = name;
      this.version = version;
      this.packageName = packageName;
      this.functions = Map.copyOf(functions);
      this.types = Map.copyOf(types);
      this.imports = List.copyOf(imports);
      this.exports = List.copyOf(exports);
      this.witText = witText;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public String getVersion() {
      return version;
    }

    @Override
    public String getPackageName() {
      return packageName;
    }

    @Override
    public List<String> getFunctionNames() {
      return new ArrayList<>(functions.keySet());
    }

    @Override
    public Map<String, WitFunction> getFunctions() {
      return functions;
    }

    @Override
    public List<String> getTypeNames() {
      return new ArrayList<>(types.keySet());
    }

    @Override
    public Map<String, WitType> getTypes() {
      return types;
    }

    @Override
    public Set<String> getDependencies() {
      return Set.copyOf(imports);
    }

    @Override
    public WitCompatibilityResult isCompatibleWith(final WitInterfaceDefinition other) {
      if (!name.equals(other.getName())) {
        return new WitCompatibilityResult(
            false,
            "Interface names do not match: " + name + " vs " + other.getName(),
            Set.of(),
            Set.of());
      }

      final Set<String> ourFunctions = Set.copyOf(functions.keySet());
      final Set<String> otherFunctions = Set.copyOf(other.getFunctionNames());
      final Set<String> ourTypes = Set.copyOf(types.keySet());
      final Set<String> otherTypes = Set.copyOf(other.getTypeNames());

      final Set<String> missingFunctions = new java.util.HashSet<>(ourFunctions);
      missingFunctions.removeAll(otherFunctions);
      final Set<String> extraFunctions = new java.util.HashSet<>(otherFunctions);
      extraFunctions.removeAll(ourFunctions);

      final Set<String> missingTypes = new java.util.HashSet<>(ourTypes);
      missingTypes.removeAll(otherTypes);
      final Set<String> extraTypes = new java.util.HashSet<>(otherTypes);
      extraTypes.removeAll(ourTypes);

      final Set<String> allMissing = new java.util.HashSet<>();
      missingFunctions.forEach(f -> allMissing.add("function:" + f));
      missingTypes.forEach(t -> allMissing.add("type:" + t));

      final Set<String> allExtra = new java.util.HashSet<>();
      extraFunctions.forEach(f -> allExtra.add("function:" + f));
      extraTypes.forEach(t -> allExtra.add("type:" + t));

      final boolean compatible = allMissing.isEmpty() && allExtra.isEmpty();
      final String message =
          compatible
              ? "Interfaces are compatible"
              : "Interfaces differ: missing=" + allMissing + ", extra=" + allExtra;

      return new WitCompatibilityResult(compatible, message, allMissing, allExtra);
    }

    @Override
    public String getWitText() {
      return witText;
    }

    @Override
    public List<String> getImportNames() {
      return imports;
    }

    @Override
    public List<String> getExportNames() {
      return exports;
    }
  }
}
