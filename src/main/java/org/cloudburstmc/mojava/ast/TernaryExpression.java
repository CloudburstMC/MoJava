package org.cloudburstmc.mojava.ast;

import org.cloudburstmc.mojava.Expression;
import org.cloudburstmc.mojava.StringHolder;
import lombok.Value;

@Value
public class TernaryExpression extends StringHolder implements Expression {

    Expression condition;
    Expression thenExpr;
    Expression elseExpr;
}
