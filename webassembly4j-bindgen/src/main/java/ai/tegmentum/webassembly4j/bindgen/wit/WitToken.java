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

/**
 * A single lexical token emitted by {@link WitLexer}.
 *
 * <p>Keywords (e.g. {@code interface}, {@code world}, {@code resource})
 * are surfaced as {@link Kind#IDENT} tokens; {@link WitParser} matches
 * them by textual equality. Punctuation and structural tokens carry
 * their own kinds so the parser can branch on kind without re-lexing.
 *
 * <p>Line + column are 1-based and preserved for diagnostic messages.
 */
final class WitToken {

  /** Distinct token kinds produced by the lexer. */
  enum Kind {
    /** An identifier or keyword — text carries the exact lexeme. */
    IDENT,
    /** Integer literal (used inside {@code @version} strings). */
    INT,
    /** Version literal after {@code @}, e.g. {@code 1.2.3} or {@code 0.1.0-pre}. */
    VERSION,
    /** {@code "..."} — carries the unquoted content. */
    STRING,
    LBRACE,
    RBRACE,
    LPAREN,
    RPAREN,
    LT,
    GT,
    COMMA,
    SEMI,
    COLON,
    EQ,
    ARROW,
    DOT,
    SLASH,
    AT,
    UNDERSCORE,
    /** End-of-input sentinel. */
    EOF,
  }

  final Kind kind;
  final String text;
  final int line;
  final int column;

  WitToken(final Kind kind, final String text, final int line, final int column) {
    this.kind = kind;
    this.text = text;
    this.line = line;
    this.column = column;
  }

  @Override
  public String toString() {
    return kind + "(" + text + ")@" + line + ":" + column;
  }
}
