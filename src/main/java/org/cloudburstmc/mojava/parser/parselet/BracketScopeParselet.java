package org.cloudburstmc.mojava.parser.parselet;

import org.cloudburstmc.mojava.Expression;
import org.cloudburstmc.mojava.ast.StatementExpression;
import org.cloudburstmc.mojava.parser.MoParser;
import org.cloudburstmc.mojava.parser.Precedence;
import org.cloudburstmc.mojava.parser.PrefixParselet;
import org.cloudburstmc.mojava.parser.tokenizer.Token;
import org.cloudburstmc.mojava.parser.tokenizer.TokenType;

import java.util.ArrayList;
import java.util.List;

public class BracketScopeParselet implements PrefixParselet {

    @Override
    public Expression parse(MoParser parser, Token token) {
        List<Expression> exprs = new ArrayList<>();

        if (!parser.matchToken(TokenType.CURLY_BRACKET_RIGHT)) {
            do {
                if (parser.matchToken(TokenType.CURLY_BRACKET_RIGHT, false)) {
                    break;
                }

                exprs.add(parser.parseExpression(Precedence.SCOPE));
            } while (parser.matchToken(TokenType.SEMICOLON));

            parser.consumeToken(TokenType.CURLY_BRACKET_RIGHT);
        }

        return new StatementExpression(exprs.toArray(new Expression[0]));
    }
}
