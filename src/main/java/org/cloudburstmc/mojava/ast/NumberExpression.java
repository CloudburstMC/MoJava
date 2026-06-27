package org.cloudburstmc.mojava.ast;

import org.cloudburstmc.mojava.Expression;
import org.cloudburstmc.mojava.StringHolder;
import org.cloudburstmc.mojava.runtime.value.DoubleValue;

public class NumberExpression extends StringHolder implements Expression {

    final DoubleValue number;

    public NumberExpression(double number) {
        this.number = new DoubleValue(number);
        this.setOriginalString(String.valueOf(number));
    }

    public DoubleValue getNumber() {
        return number;
    }
}
