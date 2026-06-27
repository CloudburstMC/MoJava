package org.cloudburstmc.mojava.parser.parselet;

import org.cloudburstmc.mojava.Expression;
import org.cloudburstmc.mojava.ast.FuncCallExpression;
import org.cloudburstmc.mojava.ast.NameExpression;
import org.cloudburstmc.mojava.parser.MoParser;
import org.cloudburstmc.mojava.parser.PrefixParselet;
import org.cloudburstmc.mojava.parser.tokenizer.Token;
import org.cloudburstmc.mojava.runtime.MoEnvironment;

import java.util.ArrayList;
import java.util.List;

public class NameParselet implements PrefixParselet {

    @Override
    public Expression parse(MoParser parser, Token token) {
        List<Expression> args = parser.parseArgs();
        String name = parser.fixNameShortcut(token.getText());
        ArrayList<String> names = new ArrayList<>(List.of(name.split("\\.")));

        Expression nameExpr = new NameExpression(names);

        if (args.size() > 0){
            return new FuncCallExpression(nameExpr, args.toArray(new Expression[args.size()]));
        }

        return nameExpr;
    }
}
