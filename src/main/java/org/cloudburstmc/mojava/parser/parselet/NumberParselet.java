package org.cloudburstmc.mojava.parser.parselet;

import org.cloudburstmc.mojava.Expression;
import org.cloudburstmc.mojava.ast.NumberExpression;
import org.cloudburstmc.mojava.parser.MoParser;
import org.cloudburstmc.mojava.parser.PrefixParselet;
import org.cloudburstmc.mojava.parser.tokenizer.Token;

public class NumberParselet implements PrefixParselet {

    @Override
    public Expression parse(MoParser parser, Token token) {
        return new NumberExpression(Double.parseDouble(token.getText()));
    }
}
