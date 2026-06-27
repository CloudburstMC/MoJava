package org.cloudburstmc.mojava.parser.parselet;

import org.cloudburstmc.mojava.Expression;
import org.cloudburstmc.mojava.parser.MoParser;
import org.cloudburstmc.mojava.parser.PrefixParselet;
import org.cloudburstmc.mojava.parser.tokenizer.Token;
import org.cloudburstmc.mojava.parser.tokenizer.TokenType;

public class GroupParselet implements PrefixParselet {

    @Override
    public Expression parse(MoParser parser, Token token) {
        // this only for conditions
        Expression expr = parser.parseExpression();
        parser.consumeToken(TokenType.BRACKET_RIGHT);

        return expr;
    }
}
