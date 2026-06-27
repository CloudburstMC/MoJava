package org.cloudburstmc.mojava.ast;

import org.cloudburstmc.mojava.Expression;
import org.cloudburstmc.mojava.StringHolder;
import lombok.Value;

@Value
public class ArrayAccessExpression extends StringHolder implements Expression {

    Expression array;
    Expression index;
}
