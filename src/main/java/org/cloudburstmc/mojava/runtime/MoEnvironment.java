package org.cloudburstmc.mojava.runtime;

import org.cloudburstmc.mojava.runtime.struct.*;
import org.cloudburstmc.mojava.runtime.value.DoubleValue;
import org.cloudburstmc.mojava.runtime.value.MoValue;

import java.util.Iterator;

public class MoEnvironment implements MoValue {
    public static final String QUERY = "query";
    public static final String MATH = "math";
    public static final String VARIABLE = "variable";
    public static final String TEMP = "temp";
    public static final String CONTEXT = "context";
    public static final String ARRAY = "array";

    public QueryStruct math = null;
    public QueryStruct query = null;
    public VariableStruct variable = null;
    public VariableStruct temp = null;
    public ContextStruct context = null;
    public ArrayStruct array = null;

    public void setStruct(String name, MoStruct struct) {
        switch (name) {
            case MATH -> math = (QueryStruct) struct;
            case QUERY -> query = (QueryStruct) struct;
            case VARIABLE -> variable = (VariableStruct) struct;
            case TEMP -> temp = (VariableStruct) struct;
            case CONTEXT -> context = (ContextStruct) struct;
            case ARRAY -> array = (ArrayStruct) struct;
        }
    }

    public void removeStruct(String name) {
        switch (name) {
            case CONTEXT -> context = null;
            case TEMP -> temp = null;
            case MATH -> math = null;
            case QUERY -> query = null;
            case VARIABLE -> variable = null;
            case ARRAY -> array = null;
        }
    }

    public MoStruct getStruct(String name) {
        return switch (name) {
            case MATH -> math;
            case QUERY -> query;
            case VARIABLE -> variable;
            case TEMP -> temp;
            case CONTEXT -> context;
            case ARRAY -> array;
            default -> null;
        };
    }

    public MoEnvironment() {
    }

    public MoValue getValue(Iterator<String> names) {
        return getValue(names, MoParams.EMPTY);
    }

    public MoValue getValue(Iterator<String> names, MoParams params) {
        String main = names.next();

        MoStruct struct = getStruct(main);
        if (struct != null) {
            return struct.get(names, params);
        }
        return new DoubleValue(0.0);
    }

    // Primitive-double resolution for the numeric compiled path (skips boxing for double-queries).
    public double getValueDouble(Iterator<String> names) {
        MoStruct struct = getStruct(names.next());
        if (struct != null) {
            return struct.getDouble(names, MoParams.EMPTY);
        }
        return 0.0;
    }

    // Existence-aware lookup for the ?? operator: null when the path is genuinely unresolved.
    public MoValue getRaw(Iterator<String> names) {
        String main = names.next();
        MoStruct struct = getStruct(main);
        if (struct == null) {
            return null;
        }
        return struct.getRaw(names, MoParams.EMPTY);
    }

    public void setValue(Iterator<String> names, MoValue value) {
        String main = names.next();
        MoStruct struct = getStruct(main);
        if (struct != null) {
            struct.set(names, value);
        }
    }

    public void setSimpleVariable(String name, MoValue value) {
        variable.setDirectly(name, value);
    }

    public MoValue getSimpleVariable(String name) {
        return variable.getMap().get(name);
    }

    @Override
    public Object value() {
        return this;
    }
}
