package org.cloudburstmc.mojava.ast;

import org.cloudburstmc.mojava.Expression;
import org.cloudburstmc.mojava.StringHolder;
import lombok.Value;

@Value
public class ForEachExpression extends StringHolder implements Expression {

    Expression variable;
    Expression array;
    Expression body;
}
