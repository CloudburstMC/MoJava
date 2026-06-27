package org.cloudburstmc.mojava.parser.parselet;

import org.cloudburstmc.mojava.Expression;
import org.cloudburstmc.mojava.ast.LoopExpression;
import org.cloudburstmc.mojava.parser.MoParser;
import org.cloudburstmc.mojava.parser.PrefixParselet;
import org.cloudburstmc.mojava.parser.tokenizer.Token;

import java.util.List;

public class LoopParselet implements PrefixParselet {

    @Override
    public Expression parse(MoParser parser, Token token) {
        List<Expression> args = parser.parseArgs();

        if (args.size() != 2) {
            throw new RuntimeException("Loop: Expected 2 argument, " + args.size() + " argument given");
        } else {
            return new LoopExpression(args.get(0), args.get(1));
        }
    }
}
