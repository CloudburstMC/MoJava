package org.cloudburstmc.mojava.parser.parselet;

import org.cloudburstmc.mojava.Expression;
import org.cloudburstmc.mojava.ast.BinaryOpExpression;
import org.cloudburstmc.mojava.ast.BinaryOpExpression.Operator;
import org.cloudburstmc.mojava.parser.InfixParselet;
import org.cloudburstmc.mojava.parser.MoParser;
import org.cloudburstmc.mojava.parser.Precedence;
import org.cloudburstmc.mojava.parser.tokenizer.Token;
import org.cloudburstmc.mojava.parser.tokenizer.TokenType;
import lombok.Value;

@Value
public class GenericBinaryOpParselet implements InfixParselet {
    Precedence precedence;

    @Override
    public Expression parse(MoParser parser, Token token, Expression leftExpr) {
        Expression rightExpr = parser.parseExpression(getPrecedence());
        return new BinaryOpExpression(operatorFor(token.getType()), leftExpr, rightExpr);
    }

    private static Operator operatorFor(TokenType type) {
        switch (type) {
            case ARROW: return Operator.ARROW;
            case AND: return Operator.AND;
            case OR: return Operator.OR;
            case COALESCE: return Operator.COALESCE;
            case SLASH: return Operator.DIVIDE;
            case EQUALS: return Operator.EQUAL;
            case GREATER: return Operator.GREATER;
            case GREATER_OR_EQUALS: return Operator.GREATER_OR_EQUAL;
            case MINUS: return Operator.SUBTRACT;
            case NOT_EQUALS: return Operator.NOT_EQUAL;
            case PLUS: return Operator.ADD;
            case ASTERISK: return Operator.MULTIPLY;
            case SMALLER: return Operator.SMALLER;
            case SMALLER_OR_EQUALS: return Operator.SMALLER_OR_EQUAL;
            default: throw new IllegalArgumentException("Not a binary operator token: " + type);
        }
    }

    @Override
    public Precedence getPrecedence() {
        return precedence;
    }
}
