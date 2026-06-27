package org.cloudburstmc.mojava.parser.parselet;

import org.cloudburstmc.mojava.Expression;
import org.cloudburstmc.mojava.ast.ArrayAccessExpression;
import org.cloudburstmc.mojava.parser.InfixParselet;
import org.cloudburstmc.mojava.parser.MoParser;
import org.cloudburstmc.mojava.parser.Precedence;
import org.cloudburstmc.mojava.parser.tokenizer.Token;
import org.cloudburstmc.mojava.parser.tokenizer.TokenType;

public class ArrayAccessParselet implements InfixParselet {

    @Override
    public Expression parse(MoParser parser, Token token, Expression leftExpr) {
        // The index is delimited by ']', so parse a full expression (loosest precedence) inside the
        // brackets rather than the parselet's own high binding precedence.
        Expression index = parser.parseExpression();
        parser.consumeToken(TokenType.ARRAY_RIGHT);

        return new ArrayAccessExpression(leftExpr, index);
    }

    @Override
    public Precedence getPrecedence() {
        return Precedence.ARRAY_ACCESS;
    }
}
