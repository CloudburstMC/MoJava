package org.cloudburstmc.mojava.parser.parselet;

import org.cloudburstmc.mojava.Expression;
import org.cloudburstmc.mojava.ast.TernaryExpression;
import org.cloudburstmc.mojava.parser.InfixParselet;
import org.cloudburstmc.mojava.parser.MoParser;
import org.cloudburstmc.mojava.parser.Precedence;
import org.cloudburstmc.mojava.parser.tokenizer.Token;
import org.cloudburstmc.mojava.parser.tokenizer.TokenType;

public class TernaryParselet implements InfixParselet {

    @Override
    public Expression parse(MoParser parser, Token token, Expression leftExpr) {
        // Ternary is right-associative (spec 1.18.10): A ? B : C ? D : E parses as A ? B : (C ? D : E).
        // Parsing the else-branch one precedence rung looser lets the trailing ternary be absorbed.
        Precedence elsePrecedence = Precedence.values()[getPrecedence().ordinal() - 1];

        if (parser.matchToken(TokenType.COLON)) {
            return new TernaryExpression(leftExpr, null, parser.parseExpression(elsePrecedence));
        } else {
            Expression thenExpr = parser.parseExpression(getPrecedence());

            if (!parser.matchToken(TokenType.COLON)) {
                return new TernaryExpression(leftExpr, thenExpr, null);
            } else {
                return new TernaryExpression(leftExpr, thenExpr, parser.parseExpression(elsePrecedence));
            }
        }
    }

    @Override
    public Precedence getPrecedence() {
        return Precedence.CONDITIONAL;
    }
}
