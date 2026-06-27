package org.cloudburstmc.mojava.parser;

// Ordered loosest (lowest ordinal) to tightest. The parser binds an infix operator only when its
// precedence ordinal is strictly greater than the current parse precedence, so a higher ordinal
// means it binds tighter. Order matches the official Bedrock Molang operator precedence:
//   ??  <  ?:  <  ||  <  &&  <  (== !=)  <  (< <= > >=)  <  (+ -)  <  (* /)  <  unary  <  ->
public enum Precedence {
    ANYTHING,
    SCOPE,
    ASSIGNMENT,

    COALESCE,     // ??
    CONDITIONAL,  // ?:
    OR,           // ||
    AND,          // &&
    EQUALITY,     // == !=
    COMPARE,      // < <= > >=
    SUM,          // + -
    PRODUCT,      // * /
    PREFIX,       // unary ! - +
    ARROW,        // ->
    ARRAY_ACCESS  // [ ]
}
