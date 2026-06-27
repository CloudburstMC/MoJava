package org.cloudburstmc.mojava;

import org.cloudburstmc.mojava.compiler.MoScript;
import org.cloudburstmc.mojava.runtime.MoRuntime;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins OPT-1 (unboxed {@code evaluateDouble}) and OPT-2 (scope elision). The contract is simple:
 * {@code executeDouble} must agree with {@code execute(...).asDouble()} for every script, and
 * {@code usesScope()} must be false exactly when the program has no control flow.
 */
@DisplayName("Fast path (evaluateDouble / scope elision)")
public class FastPathTest {

    private MoScript compile(String code) {
        return MoJava.createRuntime().compile(MoJava.parse(code));
    }

    private void assertSameResult(String code) {
        MoRuntime boxed = MoJava.createRuntime();
        MoRuntime unboxed = MoJava.createRuntime();
        double viaBoxed = boxed.execute(boxed.compile(MoJava.parse(code))).asDouble();
        double viaDouble = unboxed.executeDouble(unboxed.compile(MoJava.parse(code)));
        Assertions.assertEquals(viaBoxed, viaDouble, () -> "mismatch for: " + code);
    }

    @Test
    @DisplayName("evaluateDouble matches evaluate().asDouble() across workloads")
    public void doublePathMatchesBoxed() {
        assertSameResult("2 + 3 * 4");
        assertSameResult("math.cos(90) * 2.0 + math.sqrt(16)");
        assertSameResult("v.t = 5; v.t * v.t + math.sin(v.t * 38)");
        assertSameResult("v.t = 3; v.t > 1.0 ? v.t * 0.5 + 3.0 : v.t - 1.0"); // numeric ternary
        assertSameResult("v.t = 0; (v.t > 1.0 ? v.t * 0.5 + 3.0 : v.t - 1.0) / 2.0"); // ternary as subexpr
        assertSameResult("1 || 1 && 0");
        assertSameResult("v.x = 5; t.y = v.x * 2; t.y + v.x"); // multi-statement, last is the result
        assertSameResult("'a' == 'a'"); // boolean-from-string-equality is numeric
    }

    @Test
    @DisplayName("control flow forces the boxed path but still agrees")
    public void controlFlowStillAgrees() {
        assertSameResult("v.s = 'x'; return v.s * 2;"); // return
        assertSameResult("t.n = 0; loop(5, { t.n = t.n + 1; }); t.n"); // loop
    }

    @Test
    @DisplayName("usesScope() is false iff the program has no control flow")
    public void usesScopeTracksControlFlow() {
        Assertions.assertFalse(compile("v.t = 5; v.t * 2").usesScope());
        Assertions.assertFalse(compile("v.t > 1 ? 2 : 3").usesScope());
        Assertions.assertTrue(compile("return 5;").usesScope());
        Assertions.assertTrue(compile("loop(3, { t.n = t.n + 1; })").usesScope());
        Assertions.assertTrue(compile("for_each(t.x, q.foo, { break; })").usesScope());
    }
}
