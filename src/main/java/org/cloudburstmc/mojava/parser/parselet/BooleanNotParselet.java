package org.cloudburstmc.mojava.parser.parselet;

import org.cloudburstmc.mojava.Expression;
import org.cloudburstmc.mojava.ast.BooleanNotExpression;
import org.cloudburstmc.mojava.parser.MoParser;
import org.cloudburstmc.mojava.parser.Precedence;
import org.cloudburstmc.mojava.parser.PrefixParselet;
import org.cloudburstmc.mojava.parser.tokenizer.Token;

public class BooleanNotParselet implements PrefixParselet {

    @Override
    public Expression parse(MoParser parser, Token token) {
        return new BooleanNotExpression(parser.parseExpression(Precedence.PREFIX));
    }
}
