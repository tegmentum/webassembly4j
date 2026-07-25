/*
 * Copyright 2026 Tegmentum AI
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
import java.util.List;

/**
 * Character-stream lexer for WebAssembly Interface Type (WIT) source.
 *
 * <p>Produces a flat {@link WitToken} list from a WIT source string. Handles
 * whitespace, {@code //} line comments (including doc-comment {@code ///}
 * lines), block comments {@code /* … *}{@code /}, identifiers with WIT
 * kebab-case rules, punctuation, {@code ->} arrow, and version literals
 * after {@code @}.
 *
 * <p>The lexer is intentionally permissive about identifier shape — it
 * accepts anything the WIT grammar recognises as an identifier and lets
 * {@link WitParser} enforce semantic constraints. Keywords are surfaced as
 * plain {@link WitToken.Kind#IDENT} tokens so the parser can branch on the
 * lexeme text.
 *
 * <p>Together with {@link WitParser}, this replaces the retired
 * {@code WitInterfaceParser} regex chain plus its {@code WitWorldPreprocessor}
 * and {@code WitResourceBodyParser} wrappers — a single recursive-descent
 * walk over a proper token stream now covers the WIT constructs the wrappers
 * used to patch (package / world / multi-interface / use / import / export /
 * resource bodies).
 */
final class WitLexer {

  private final String source;
  private int pos;
  private int line = 1;
  private int column = 1;

  WitLexer(final String source) {
    this.source = source;
  }

  /**
   * Tokenise the input in one pass.
   *
   * @return the token stream, terminated by an {@link WitToken.Kind#EOF} token
   * @throws BindgenException if the input contains an unterminated string,
   *     unterminated block comment, or an unrecognised character
   */
  List<WitToken> tokenize() throws BindgenException {
    final List<WitToken> tokens = new ArrayList<>();
    while (pos < source.length()) {
      skipWhitespaceAndComments();
      if (pos >= source.length()) {
        break;
      }
      final int startLine = line;
      final int startCol = column;
      final char c = source.charAt(pos);
      if (c == '{') {
        advance();
        tokens.add(new WitToken(WitToken.Kind.LBRACE, "{", startLine, startCol));
      } else if (c == '}') {
        advance();
        tokens.add(new WitToken(WitToken.Kind.RBRACE, "}", startLine, startCol));
      } else if (c == '(') {
        advance();
        tokens.add(new WitToken(WitToken.Kind.LPAREN, "(", startLine, startCol));
      } else if (c == ')') {
        advance();
        tokens.add(new WitToken(WitToken.Kind.RPAREN, ")", startLine, startCol));
      } else if (c == '<') {
        advance();
        tokens.add(new WitToken(WitToken.Kind.LT, "<", startLine, startCol));
      } else if (c == '>') {
        advance();
        tokens.add(new WitToken(WitToken.Kind.GT, ">", startLine, startCol));
      } else if (c == ',') {
        advance();
        tokens.add(new WitToken(WitToken.Kind.COMMA, ",", startLine, startCol));
      } else if (c == ';') {
        advance();
        tokens.add(new WitToken(WitToken.Kind.SEMI, ";", startLine, startCol));
      } else if (c == ':') {
        advance();
        tokens.add(new WitToken(WitToken.Kind.COLON, ":", startLine, startCol));
      } else if (c == '=') {
        advance();
        tokens.add(new WitToken(WitToken.Kind.EQ, "=", startLine, startCol));
      } else if (c == '.') {
        advance();
        tokens.add(new WitToken(WitToken.Kind.DOT, ".", startLine, startCol));
      } else if (c == '/') {
        advance();
        tokens.add(new WitToken(WitToken.Kind.SLASH, "/", startLine, startCol));
      } else if (c == '@') {
        advance();
        tokens.add(new WitToken(WitToken.Kind.AT, "@", startLine, startCol));
        // Everything after @ up to the next whitespace / structural
        // char is the version literal (e.g. `0.1.0`, `1.2.3-alpha`).
        // Kept as an opaque string; downstream doesn't need semver
        // semantics. Emitted as its own token so the parser branches
        // on AT before consuming the VERSION.
        final int vLine = line;
        final int vCol = column;
        if (pos < source.length() && isVersionChar(source.charAt(pos))) {
          tokens.add(readVersion(vLine, vCol));
        }
      } else if (c == '_') {
        // Bare underscore is a valid placeholder in `result<_, err>`.
        // If followed by an ident char it's part of an identifier.
        if (pos + 1 < source.length() && isIdentChar(source.charAt(pos + 1))) {
          tokens.add(readIdent(startLine, startCol));
        } else {
          advance();
          tokens.add(new WitToken(WitToken.Kind.UNDERSCORE, "_", startLine, startCol));
        }
      } else if (c == '-') {
        // `->` is the func return arrow. A lone `-` doesn't appear in
        // WIT except inside kebab-case identifiers (`host-provider`),
        // and identifiers start with a letter, so a `-` at token-start
        // is either the arrow or an error.
        if (pos + 1 < source.length() && source.charAt(pos + 1) == '>') {
          advance();
          advance();
          tokens.add(new WitToken(WitToken.Kind.ARROW, "->", startLine, startCol));
        } else {
          throw error("unexpected '-' at token start (expected '->')", startLine, startCol);
        }
      } else if (c == '"') {
        tokens.add(readString(startLine, startCol));
      } else if (isIdentStart(c)) {
        tokens.add(readIdent(startLine, startCol));
      } else if (Character.isDigit(c)) {
        tokens.add(readInt(startLine, startCol));
      } else {
        throw error("unexpected character '" + c + "'", startLine, startCol);
      }
    }
    tokens.add(new WitToken(WitToken.Kind.EOF, "", line, column));
    return tokens;
  }

  private void skipWhitespaceAndComments() throws BindgenException {
    while (pos < source.length()) {
      final char c = source.charAt(pos);
      if (Character.isWhitespace(c)) {
        advance();
      } else if (c == '/' && pos + 1 < source.length() && source.charAt(pos + 1) == '/') {
        // Line comment — includes `///` doc comments.
        while (pos < source.length() && source.charAt(pos) != '\n') {
          advance();
        }
      } else if (c == '/' && pos + 1 < source.length() && source.charAt(pos + 1) == '*') {
        skipBlockComment();
      } else {
        return;
      }
    }
  }

  private void skipBlockComment() throws BindgenException {
    final int startLine = line;
    final int startCol = column;
    advance();
    advance();
    int depth = 1;
    while (pos < source.length() && depth > 0) {
      if (pos + 1 < source.length()
          && source.charAt(pos) == '/'
          && source.charAt(pos + 1) == '*') {
        advance();
        advance();
        depth++;
      } else if (pos + 1 < source.length()
          && source.charAt(pos) == '*'
          && source.charAt(pos + 1) == '/') {
        advance();
        advance();
        depth--;
      } else {
        advance();
      }
    }
    if (depth > 0) {
      throw error("unterminated block comment", startLine, startCol);
    }
  }

  private WitToken readIdent(final int startLine, final int startCol) {
    // Handle explicit-identifier `%name` prefix (WIT syntax for using a
    // keyword as an identifier). The `%` is stripped; the parser sees the
    // raw name.
    boolean explicit = false;
    if (source.charAt(pos) == '%') {
      explicit = true;
      advance();
    }
    final int start = pos;
    while (pos < source.length() && isIdentChar(source.charAt(pos))) {
      advance();
    }
    final String text = source.substring(start, pos);
    // Guard against a lone `%` with no ident after it.
    if (explicit && text.isEmpty()) {
      // Fall through — the parser will report an error on the empty ident.
    }
    return new WitToken(WitToken.Kind.IDENT, text, startLine, startCol);
  }

  private WitToken readInt(final int startLine, final int startCol) {
    final int start = pos;
    while (pos < source.length() && Character.isDigit(source.charAt(pos))) {
      advance();
    }
    return new WitToken(WitToken.Kind.INT, source.substring(start, pos), startLine, startCol);
  }

  private WitToken readString(final int startLine, final int startCol) throws BindgenException {
    advance(); // consume opening quote
    final StringBuilder sb = new StringBuilder();
    while (pos < source.length()) {
      final char c = source.charAt(pos);
      if (c == '\\' && pos + 1 < source.length()) {
        sb.append(source.charAt(pos + 1));
        advance();
        advance();
      } else if (c == '"') {
        advance();
        return new WitToken(WitToken.Kind.STRING, sb.toString(), startLine, startCol);
      } else {
        sb.append(c);
        advance();
      }
    }
    throw error("unterminated string literal", startLine, startCol);
  }

  private WitToken readVersion(final int startLine, final int startCol) {
    final int start = pos;
    while (pos < source.length() && isVersionChar(source.charAt(pos))) {
      advance();
    }
    return new WitToken(WitToken.Kind.VERSION, source.substring(start, pos), startLine, startCol);
  }

  private static boolean isVersionChar(final char c) {
    // Semver-ish body: digits, dots, hyphens, plus, letters (pre-release
    // tags like `1.2.3-alpha.1+build`).
    return Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '+';
  }

  private static boolean isIdentStart(final char c) {
    return Character.isLetter(c) || c == '_' || c == '%';
  }

  private static boolean isIdentChar(final char c) {
    return Character.isLetterOrDigit(c) || c == '_' || c == '-';
  }

  private void advance() {
    final char c = source.charAt(pos);
    pos++;
    if (c == '\n') {
      line++;
      column = 1;
    } else {
      column++;
    }
  }

  private BindgenException error(final String message, final int atLine, final int atCol) {
    return new BindgenException("WIT lex error at " + atLine + ":" + atCol + ": " + message);
  }
}
