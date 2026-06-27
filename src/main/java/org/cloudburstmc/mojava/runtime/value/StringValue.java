package org.cloudburstmc.mojava.runtime.value;

public class StringValue implements MoValue {

    public final String value;

    public StringValue(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    @Override
    public String asString() {
        return value;
    }

    @Override
    public double asDouble() {
        // Molang has no string->number coercion: a string used where a number is required is a content
        // error, which the engine resolves to 0.0. (Strings officially support only == and !=.)
        return 0.0;
    }

    @Override
    public boolean equals(Object obj) {
        // Type-strict: a string equals only another string with the same text. Molang does not treat
        // '0' and 0 as equal (cross-type operations are content errors, not coercions).
        return obj == this || (obj instanceof StringValue && ((StringValue) obj).value.equals(value));
    }
}
