package org.cloudburstmc.mojava.parser;

import org.cloudburstmc.mojava.Expression;
import org.cloudburstmc.mojava.parser.tokenizer.Token;

public interface InfixParselet {

    Expression parse(MoParser parser, Token token, Expression leftExpr);

    default Precedence getPrecedence() {
        return Precedence.ANYTHING;
    }
}
