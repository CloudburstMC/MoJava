package org.cloudburstmc.mojava.runtime;

import org.cloudburstmc.mojava.runtime.struct.MoStruct;
import org.cloudburstmc.mojava.runtime.value.DoubleValue;
import org.cloudburstmc.mojava.runtime.value.MoValue;
import org.cloudburstmc.mojava.runtime.value.StringValue;

import java.util.Arrays;
import java.util.List;

public class MoParams {

    private static final MoValue[] NO_ARGS = new MoValue[0];

    public static final MoParams EMPTY = new MoParams(new MoEnvironment(), NO_ARGS);

    private final MoEnvironment environment;
    // Held as an array: the compiled call path already builds a MoValue[] of arguments, so wrapping it
    // in a List per call (the old constructor) was a wasted allocation on every function/query call.
    private final MoValue[] params;

    public MoParams(MoEnvironment environment, MoValue[] params) {
        this.environment = environment;
        this.params = params;
    }

    public MoParams(MoEnvironment environment, List<MoValue> params) {
        this(environment, params.toArray(NO_ARGS));
    }

    @SuppressWarnings("unchecked")
    public <T extends MoValue> T get(int index) {
        return (T) params[index];
    }

    public boolean contains(int index) {
        return params.length >= index + 1;
    }

    public int getInt(int index) {
        return (int) getDouble(index);
    }

    public double getDouble(int index) {
        return this.<DoubleValue>get(index).asDouble();
    }

    public MoStruct getStruct(int index) {
        return get(index);
    }

    public String getString(int index) {
        return this.<StringValue>get(index).asString();
    }

    public MoEnvironment getEnv(int index) {
        return get(index);
    }
    public MoEnvironment getEnvironment() {
        return environment;
    }

    public List<MoValue> getParams() {
        return Arrays.asList(params);
    }
}
