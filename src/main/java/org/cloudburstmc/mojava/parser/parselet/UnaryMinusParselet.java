package org.cloudburstmc.mojava.parser.parselet;

import org.cloudburstmc.mojava.Expression;
import org.cloudburstmc.mojava.ast.UnaryMinusExpression;
import org.cloudburstmc.mojava.parser.MoParser;
import org.cloudburstmc.mojava.parser.Precedence;
import org.cloudburstmc.mojava.parser.PrefixParselet;
import org.cloudburstmc.mojava.parser.tokenizer.Token;

public class UnaryMinusParselet implements PrefixParselet {

    @Override
    public Expression parse(MoParser parser, Token token) {
        return new UnaryMinusExpression(parser.parseExpression(Precedence.PREFIX));
    }
}
