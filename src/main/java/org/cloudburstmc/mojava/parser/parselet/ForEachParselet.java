package org.cloudburstmc.mojava.parser.parselet;

import org.cloudburstmc.mojava.Expression;
import org.cloudburstmc.mojava.ast.ForEachExpression;
import org.cloudburstmc.mojava.parser.MoParser;
import org.cloudburstmc.mojava.parser.PrefixParselet;
import org.cloudburstmc.mojava.parser.tokenizer.Token;

import java.util.List;

public class ForEachParselet implements PrefixParselet {

    @Override
    public Expression parse(MoParser parser, Token token) {
        List<Expression> args = parser.parseArgs();

        if (args.size() != 3) {
            throw new RuntimeException("ForEach: Expected 3 argument, " + args.size() + " argument given");
        } else {
            return new ForEachExpression(args.get(0), args.get(1), args.get(2));
        }
    }
}
