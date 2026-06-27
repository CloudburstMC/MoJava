package org.cloudburstmc.mojava.ast;

import org.cloudburstmc.mojava.Expression;
import org.cloudburstmc.mojava.StringHolder;
import lombok.Value;

import java.util.ArrayList;

@Value
public class NameExpression extends StringHolder implements Expression {

    ArrayList<String> names;
}
