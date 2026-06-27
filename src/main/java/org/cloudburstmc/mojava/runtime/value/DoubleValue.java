package org.cloudburstmc.mojava.runtime.value;

public class DoubleValue implements MoValue {

    public final static DoubleValue ZERO = new DoubleValue(0.0);
    public final static DoubleValue ONE = new DoubleValue(1.0);

    public final double value;

    public DoubleValue(double value) {
        this.value = value;
    }

    public DoubleValue(Object value) {
        if (value instanceof Boolean) {
            this.value = (boolean) value ? 1.0 : 0.0;
        } else if (value instanceof Number) {
            this.value = ((Number)value).doubleValue();
        } else {
            this.value = 1.0;
        }
    }

    @Override
    public Double value() {
        return value;
    }

    @Override
    public String asString() {
        // String.format is ~50-100x slower than direct conversion and is on the hot path
        // (string concat + cross-type equality). Output is identical to "%d"/"%s".
        if (value == (long) value) {
            return Long.toString((long) value);
        } else {
            return Double.toString(value);
        }
    }

    @Override
    public double asDouble() {
        return value;
    }

    @Override
    public boolean equals(Object obj) {
        // Type-strict: a number equals only another number with the same value. Molang does not treat
        // 0 and '0' as equal (cross-type operations are content errors, not coercions).
        return obj == this || (obj instanceof DoubleValue && ((DoubleValue) obj).value == value);
    }
}
