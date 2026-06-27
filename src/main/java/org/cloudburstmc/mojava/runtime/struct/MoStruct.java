package org.cloudburstmc.mojava.runtime.struct;

import org.cloudburstmc.mojava.runtime.MoParams;
import org.cloudburstmc.mojava.runtime.value.MoValue;

import java.util.Iterator;

public interface MoStruct extends MoValue {

    void set(Iterator<String> keys, MoValue value);

    MoValue get(Iterator<String> names, MoParams params);

    // Primitive-double resolution for the numeric compiled path. The default boxes (get().asDouble());
    // QueryStruct overrides it so a registered double-query allocates nothing.
    default double getDouble(Iterator<String> names, MoParams params) {
        return get(names, params).asDouble();
    }

    // Like get, but returns null for a genuinely-absent path instead of defaulting to 0.0.
    // Used by the ?? operator to tell "missing" apart from a real value of 0.
    MoValue getRaw(Iterator<String> names, MoParams params);

    void clear();

    @Override
    default MoStruct value() {
        return this;
    }
}
