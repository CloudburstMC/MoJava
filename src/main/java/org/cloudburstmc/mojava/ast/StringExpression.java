package org.cloudburstmc.mojava.ast;

import org.cloudburstmc.mojava.Expression;
import org.cloudburstmc.mojava.StringHolder;
import org.cloudburstmc.mojava.runtime.value.StringValue;
import lombok.Value;

@Value
public class StringExpression extends StringHolder implements Expression {

    StringValue string;
}
