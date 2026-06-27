package org.cloudburstmc.mojava.parser.parselet;

import org.cloudburstmc.mojava.Expression;
import org.cloudburstmc.mojava.ast.AssignExpression;
import org.cloudburstmc.mojava.parser.InfixParselet;
import org.cloudburstmc.mojava.parser.MoParser;
import org.cloudburstmc.mojava.parser.Precedence;
import org.cloudburstmc.mojava.parser.tokenizer.Token;

public class AssignParselet implements InfixParselet {

    @Override
    public Expression parse(MoParser parser, Token token, Expression leftExpr) {
        return new AssignExpression(leftExpr, parser.parseExpression(getPrecedence()));
    }

    @Override
    public Precedence getPrecedence() {
        return Precedence.ASSIGNMENT;
    }
}
