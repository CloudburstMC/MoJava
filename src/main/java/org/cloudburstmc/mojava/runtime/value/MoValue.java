package org.cloudburstmc.mojava.runtime.value;

public interface MoValue {
    static MoValue of(Object value) {
        if (value instanceof MoValue) {
            return (MoValue) value;
        }
        // Numbers and Booleans are handled by DoubleValue(Object); anything else defaults to 1.0.
        return new DoubleValue(value);
    }

    Object value();

    default String asString() {
        return this.toString();
    }

    default double asDouble() {
        return 1.0;
    }
}
