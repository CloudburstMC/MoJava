package org.cloudburstmc.mojava;

import org.cloudburstmc.mojava.runtime.MoRuntime;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * OPT-10: a query registered as a primitive double ({@code addDoubleFunction}) must produce exactly
 * the same results as the boxed {@code addFunction} path, on every route — the numeric compiled path
 * ({@code getValueDouble}), a bare read, an argument call, and {@code ??}.
 */
@DisplayName("Double query (OPT-10)")
public class DoubleQueryTest {

    private MoRuntime runtime() {
        MoRuntime r = MoJava.createRuntime();
        r.getEnvironment().query
                .addDoubleFunction("speed", p -> 1.5)
                .addDoubleFunction("scale", p -> p.contains(0) ? p.getDouble(0) * 2 : 0.0);
        return r;
    }

    private double eval(String code) {
        MoRuntime r = runtime();
        return r.executeDouble(r.compile(MoJava.parse(code)));
    }

    @Test
    @DisplayName("double-query matches the boxed path on every route")
    public void matchesBoxedPath() {
        Assertions.assertEquals(1.5, eval("query.speed"));            // bare read (boxed evaluate)
        Assertions.assertEquals(3.0, eval("query.speed * 2"));        // numeric path -> getValueDouble
        Assertions.assertEquals(1.5, eval("q.speed"));                // alias
        Assertions.assertEquals(8.0, eval("query.scale(4)"));         // double-query with an argument
        Assertions.assertEquals(1.0, eval("query.speed > 1 ? 1 : 0")); // condition coerced to double
        Assertions.assertEquals(1.5, eval("query.speed ?? 9"));       // ?? sees a present value
        Assertions.assertEquals(9.0, eval("query.missing ?? 9"));     // unset query -> default
    }
}
