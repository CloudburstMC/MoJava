package org.cloudburstmc.mojava.parser.parselet;

import org.cloudburstmc.mojava.Expression;
import org.cloudburstmc.mojava.ast.StringExpression;
import org.cloudburstmc.mojava.parser.MoParser;
import org.cloudburstmc.mojava.parser.PrefixParselet;
import org.cloudburstmc.mojava.parser.tokenizer.Token;
import org.cloudburstmc.mojava.runtime.value.StringValue;

public class StringParselet implements PrefixParselet {

    @Override
    public Expression parse(MoParser parser, Token token) {
        return new StringExpression(new StringValue(token.getText()));
    }
}
