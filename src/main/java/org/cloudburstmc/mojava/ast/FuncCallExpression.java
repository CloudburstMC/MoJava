package org.cloudburstmc.mojava.ast;

import org.cloudburstmc.mojava.Expression;
import org.cloudburstmc.mojava.StringHolder;
import lombok.Value;

@Value
public class FuncCallExpression extends StringHolder implements Expression {

    Expression name;
    Expression[] args;
}
