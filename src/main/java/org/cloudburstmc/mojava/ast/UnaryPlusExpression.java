package org.cloudburstmc.mojava.ast;

import org.cloudburstmc.mojava.Expression;
import org.cloudburstmc.mojava.StringHolder;
import lombok.Value;

@Value
public class UnaryPlusExpression extends StringHolder implements Expression {

    Expression expression;
}
