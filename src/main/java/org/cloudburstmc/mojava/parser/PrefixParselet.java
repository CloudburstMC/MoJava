package org.cloudburstmc.mojava.parser;

import org.cloudburstmc.mojava.Expression;
import org.cloudburstmc.mojava.parser.tokenizer.Token;

public interface PrefixParselet {

    Expression parse(MoParser parser, Token token);
}
