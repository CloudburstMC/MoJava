package org.cloudburstmc.mojava.runtime.struct;

import org.cloudburstmc.mojava.runtime.DoubleQueryFunction;
import org.cloudburstmc.mojava.runtime.MoParams;
import org.cloudburstmc.mojava.runtime.value.DoubleValue;
import org.cloudburstmc.mojava.runtime.value.MoValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.Iterator;
import java.util.function.Function;

@Getter
@RequiredArgsConstructor
public class QueryStruct implements MoStruct {

    public final HashMap<String, Function<MoParams, Object>> functions;

    @Override
    public MoValue get(Iterator<String> names, MoParams params) {
        String key = names.next();
        Function<MoParams, Object> func = functions.get(key);
        if (func == null) {
            return DoubleValue.ZERO;
        }
        if (!names.hasNext()) {
            if (func instanceof DoubleQueryFunction dqf) {
                return new DoubleValue(dqf.applyAsDouble(params)); // skip the Double autobox
            }
            return MoValue.of(func.apply(params));
        }
        Object result = func.apply(MoParams.EMPTY);
        if (result instanceof MoStruct) {
            return ((MoStruct) result).get(names, params);
        }
        return MoValue.of(result);
    }

    // Primitive-double resolution: a registered double-query allocates nothing here.
    @Override
    public double getDouble(Iterator<String> names, MoParams params) {
        String key = names.next();
        Function<MoParams, Object> func = functions.get(key);
        if (func == null) {
            return 0.0;
        }
        if (!names.hasNext()) {
            if (func instanceof DoubleQueryFunction dqf) {
                return dqf.applyAsDouble(params);
            }
            return MoValue.of(func.apply(params)).asDouble();
        }
        Object result = func.apply(MoParams.EMPTY);
        if (result instanceof MoStruct) {
            return ((MoStruct) result).getDouble(names, params);
        }
        return MoValue.of(result).asDouble();
    }

    @Override
    public MoValue getRaw(Iterator<String> names, MoParams params) {
        String key = names.next();
        Function<MoParams, Object> func = functions.get(key);
        if (func == null) {
            return null;
        }
        if (!names.hasNext() && func instanceof DoubleQueryFunction dqf) {
            return new DoubleValue(dqf.applyAsDouble(params));
        }
        MoParams currentParams = names.hasNext() ? MoParams.EMPTY : params;
        Object result = func.apply(currentParams);
        if (result instanceof MoStruct && names.hasNext()) {
            return ((MoStruct) result).getRaw(names, params);
        }
        return MoValue.of(result);
    }

    @Override
    public void set(Iterator<String> names, MoValue value) {
        String main = names.next();

        if (names.hasNext() && main != null) {
            Function<MoParams, Object> function = functions.get(main);
            if (function != null) {
                Object struct = function.apply(MoParams.EMPTY);
                if (!(struct instanceof MoStruct)) {
                    throw new RuntimeException("Cannot set a value in query struct");
                } else {
                    ((MoStruct) struct).set(names, value);
                }
            } else {
                throw new RuntimeException("Cannot set a value in query struct");
            }
        } else {
            throw new RuntimeException("Cannot set a value in query struct");
        }
    }

    @Override
    public void clear() {
        functions.clear();
    }

    public QueryStruct addFunction(String name, Function<MoParams, Object> func) {
        functions.put(name, func);
        return this;
    }

    // Opt-in primitive-double query: avoids the Double autobox always, and the DoubleValue box when the
    // result feeds the numeric compiled path. See DoubleQueryFunction.
    public QueryStruct addDoubleFunction(String name, DoubleQueryFunction func) {
        functions.put(name, func);
        return this;
    }
}
