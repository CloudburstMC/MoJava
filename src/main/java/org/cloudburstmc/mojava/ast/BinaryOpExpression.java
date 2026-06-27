package org.cloudburstmc.mojava.ast;

import org.cloudburstmc.mojava.Expression;
import org.cloudburstmc.mojava.StringHolder;
import lombok.Getter;

/**
 * Any infix binary operation. The {@link Operator} distinguishes the kind, so there is one class
 * rather than a subclass per operator.
 */
@Getter
public class BinaryOpExpression extends StringHolder implements Expression {

    public enum Operator {
        ADD("+"),
        SUBTRACT("-"),
        MULTIPLY("*"),
        DIVIDE("/"),
        EQUAL("=="),
        NOT_EQUAL("!="),
        GREATER(">"),
        GREATER_OR_EQUAL(">="),
        SMALLER("<"),
        SMALLER_OR_EQUAL("<="),
        AND("&&"),
        OR("||"),
        COALESCE("??"),
        ARROW("->");

        public final String sigil;

        Operator(String sigil) {
            this.sigil = sigil;
        }
    }

    private final Operator operator;
    private final Expression left;
    private final Expression right;

    public BinaryOpExpression(Operator operator, Expression left, Expression right) {
        this.operator = operator;
        this.left = left;
        this.right = right;
    }
}
