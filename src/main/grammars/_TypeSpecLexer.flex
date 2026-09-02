package dev.tsp.intellij.typespec.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.TokenType;
import static dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.*;

%%

%class _TypeSpecLexer
%implements FlexLexer
%unicode
%function advance
%type IElementType
%eof{  return;
%eof}

%state STRING_S
%state MULTILINE_STRING_S
%state BLOCK_COMMENT_S
%state DOC_COMMENT_S

IdentifierStart = [:jletter:] | "_" | "$"
IdentifierPart  = [:jletterdigit:] | "_" | "$"
Identifier      = {IdentifierStart}{IdentifierPart}*
QualifiedName   = {Identifier} ("." {Identifier})*

// C-style comment body: any run of non-star characters, or a run of stars not
// immediately followed by a slash. Deliberately never consumes an actual "*/" —
// so the "closed" rule (which requires the literal close after this body) is
// always the longest possible match when a real close exists, and the
// "unterminated" fallback (this body alone, to EOF) only wins when no close
// exists anywhere in the remaining input. Comments do not nest.
BlockBody = ( [^*] | \*+ [^*/] )*

WhiteSpace = [ \t\f\r\n]+

%%

<YYINITIAL> {

  {WhiteSpace}                                  { return TokenType.WHITE_SPACE; }

  // ---- comments ---------------------------------------------------------
  "/**/"                                        { return BLOCK_COMMENT; }
  "/**" {BlockBody} "*"+ "/"                    { return DOC_COMMENT; }
  "/*" {BlockBody} "*"+ "/"                     { return BLOCK_COMMENT; }
  "//" [^\r\n]*                                 { return LINE_COMMENT; }
  "/**" {BlockBody}                             { yybegin(DOC_COMMENT_S); return DOC_COMMENT; }
  "/*" {BlockBody}                              { yybegin(BLOCK_COMMENT_S); return BLOCK_COMMENT; }

  // ---- strings ------------------------------------------------------------
  "\"\"\"" ( [^\"] | \"[^\"] | \"\"[^\"] )* "\"\"\""   { return MULTILINE_STRING; }
  "\"\"\"" ( [^\"] | \"[^\"] | \"\"[^\"] )*            { yybegin(MULTILINE_STRING_S); return MULTILINE_STRING; }
  \" [^\"\\\r\n]* \"                             { return STRING; }
  \" [^\"\\\r\n]*                                { yybegin(STRING_S); return STRING; }

  // ---- backtick identifiers ------------------------------------------------
  ` [^`\r\n]* `                                 { return IDENTIFIER; }
  ` [^`\r\n]*                                   { return IDENTIFIER; }

  // ---- decorators and directives ------------------------------------------
  "@@" {QualifiedName}                          { return AUGMENT_DECORATOR; }
  "@" {QualifiedName}                           { return DECORATOR; }
  "@@"                                          { return AT_AT; }
  "@"                                           { return AT; }

  "#{"                                          { return HASH_BRACE; }
  "#["                                          { return HASH_BRACKET; }
  "#" {Identifier}                              { return DIRECTIVE; }
  "#"                                           { return HASH; }

  // ---- numbers --------------------------------------------------------------
  "0x" [0-9a-fA-F]+                             { return NUMBER; }
  "0b" [01]+                                    { return NUMBER; }
  [0-9]+ ("." [0-9]+)? ("e" [+-]? [0-9]+)?      { return NUMBER; }

  // ---- keywords (active + reserved) — must precede the identifier rule -----
  "import"        { return KEYWORD; }
  "model"         { return KEYWORD; }
  "scalar"        { return KEYWORD; }
  "namespace"     { return KEYWORD; }
  "interface"     { return KEYWORD; }
  "union"         { return KEYWORD; }
  "if"            { return KEYWORD; }
  "else"          { return KEYWORD; }
  "projection"    { return KEYWORD; }
  "using"         { return KEYWORD; }
  "op"            { return KEYWORD; }
  "extends"       { return KEYWORD; }
  "is"            { return KEYWORD; }
  "enum"          { return KEYWORD; }
  "alias"         { return KEYWORD; }
  "dec"           { return KEYWORD; }
  "fn"            { return KEYWORD; }
  "valueof"       { return KEYWORD; }
  "typeof"        { return KEYWORD; }
  "const"         { return KEYWORD; }
  "init"          { return KEYWORD; }
  "true"          { return KEYWORD; }
  "false"         { return KEYWORD; }
  "return"        { return KEYWORD; }
  "void"          { return KEYWORD; }
  "never"         { return KEYWORD; }
  "unknown"       { return KEYWORD; }
  "extern"        { return KEYWORD; }
  "auto"          { return KEYWORD; }
  "internal"      { return KEYWORD; }
  "statemachine"  { return KEYWORD; }
  "macro"         { return KEYWORD; }
  "package"       { return KEYWORD; }
  "metadata"      { return KEYWORD; }
  "env"           { return KEYWORD; }
  "arg"           { return KEYWORD; }
  "declare"       { return KEYWORD; }
  "array"         { return KEYWORD; }
  "struct"        { return KEYWORD; }
  "record"        { return KEYWORD; }
  "module"        { return KEYWORD; }
  "mod"           { return KEYWORD; }
  "sym"           { return KEYWORD; }
  "context"       { return KEYWORD; }
  "prop"          { return KEYWORD; }
  "property"      { return KEYWORD; }
  "scenario"      { return KEYWORD; }
  "pub"           { return KEYWORD; }
  "sub"           { return KEYWORD; }
  "typeref"       { return KEYWORD; }
  "trait"         { return KEYWORD; }
  "this"          { return KEYWORD; }
  "self"          { return KEYWORD; }
  "super"         { return KEYWORD; }
  "keyof"         { return KEYWORD; }
  "with"          { return KEYWORD; }
  "implements"    { return KEYWORD; }
  "impl"          { return KEYWORD; }
  "satisfies"     { return KEYWORD; }
  "flag"          { return KEYWORD; }
  "partial"       { return KEYWORD; }
  "private"       { return KEYWORD; }
  "public"        { return KEYWORD; }
  "protected"     { return KEYWORD; }
  "sealed"        { return KEYWORD; }
  "local"         { return KEYWORD; }
  "async"         { return KEYWORD; }

  // ---- identifiers ----------------------------------------------------------
  {Identifier}                                  { return IDENTIFIER; }

  // ---- multi-character operators (must precede single-character prefixes) --
  "..."                                         { return ELLIPSIS; }
  "::"                                          { return COLON_COLON; }
  "<="                                          { return LE; }
  ">="                                          { return GE; }
  "=="                                          { return EQ_EQ; }
  "!="                                          { return NE; }
  "=>"                                          { return ARROW; }
  "&&"                                          { return AMP_AMP; }
  "||"                                          { return BAR_BAR; }

  // ---- single-character punctuation and operators ---------------------------
  "{"  { return LBRACE; }
  "}"  { return RBRACE; }
  "("  { return LPAREN; }
  ")"  { return RPAREN; }
  "["  { return LBRACKET; }
  "]"  { return RBRACKET; }
  ";"  { return SEMICOLON; }
  ","  { return COMMA; }
  "."  { return DOT; }
  ":"  { return COLON; }
  "<"  { return LT; }
  ">"  { return GT; }
  "="  { return EQ; }
  "&"  { return AMP; }
  "|"  { return BAR; }
  "?"  { return QUESTION; }
  "!"  { return EXCL; }
  "+"  { return PLUS; }
  "-"  { return MINUS; }
  "*"  { return STAR; }
  "/"  { return SLASH; }

  [^]  { return TokenType.BAD_CHARACTER; }
}

<STRING_S> {
  "\\" [\"\\ntr$`]                              { return VALID_ESCAPE; }
  "\\" [^]                                      { return INVALID_ESCAPE; }
  "\\"                                          { return INVALID_ESCAPE; }
  [^\"\\\r\n]* \"                               { yybegin(YYINITIAL); return STRING; }
  [^\"\\\r\n]+                                  { return STRING; }
  [\r\n]                                        { yypushback(1); yybegin(YYINITIAL); }
  <<EOF>>                                       { yybegin(YYINITIAL); return null; }
}

<MULTILINE_STRING_S> {
  "\"\"\""                                      { yybegin(YYINITIAL); return MULTILINE_STRING; }
  "\""                                          { return MULTILINE_STRING; }
  [^\"]+                                        { return MULTILINE_STRING; }
  <<EOF>>                                       { yybegin(YYINITIAL); return null; }
}

<BLOCK_COMMENT_S> {
  "*"+ "/"                                      { yybegin(YYINITIAL); return BLOCK_COMMENT; }
  "*"+                                          { return BLOCK_COMMENT; }
  [^*]+                                         { return BLOCK_COMMENT; }
  <<EOF>>                                       { yybegin(YYINITIAL); return null; }
}

<DOC_COMMENT_S> {
  "*"+ "/"                                      { yybegin(YYINITIAL); return DOC_COMMENT; }
  "*"+                                          { return DOC_COMMENT; }
  [^*]+                                         { return DOC_COMMENT; }
  <<EOF>>                                       { yybegin(YYINITIAL); return null; }
}
