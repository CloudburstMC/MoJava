package org.cloudburstmc.mojava;

import org.cloudburstmc.mojava.runtime.MoRuntime;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the Workstream A spec-correctness fixes. Each assertion encodes the official Bedrock Molang
 * behavior and was red against the pre-fix interpreter.
 */
@DisplayName("Spec Compliance")
public class SpecComplianceTest {

    private double eval(String code) {
        MoRuntime runtime = MoJava.createRuntime();
        return runtime.execute(MoJava.parse(code)).asDouble();
    }

    // ---- A8: strings support only == and != (official Molang, latest) ----

    @Test
    @DisplayName("A8: string == / != compares by value")
    public void stringEquality() {
        Assertions.assertEquals(1.0, eval("'a' == 'a'"));
        Assertions.assertEquals(0.0, eval("'a' == 'b'"));
        Assertions.assertEquals(1.0, eval("'a' != 'b'"));
    }

    @Test
    @DisplayName("A8: string and number are never equal (no cross-type coercion)")
    public void noCrossTypeEquality() {
        Assertions.assertEquals(0.0, eval("'0' == 0"));
        Assertions.assertEquals(1.0, eval("'5' != 5"));
    }

    @Test
    @DisplayName("A8: a runtime string used in math resolves to 0.0")
    public void runtimeStringInMathIsZero() {
        Assertions.assertEquals(0.0, eval("v.s = 'x'; return v.s * 2;"));
        Assertions.assertEquals(0.0, eval("v.s = 'x'; return v.s + 1;")); // '+' is not concatenation
    }

    @Test
    @DisplayName("A8: a static string in a non-equality op is a compile-time content error")
    public void staticStringMisuseRejected() {
        Assertions.assertThrows(RuntimeException.class, () -> eval("'a' + 'b'"));
        Assertions.assertThrows(RuntimeException.class, () -> eval("'a' * 2"));
        Assertions.assertThrows(RuntimeException.class, () -> eval("'text' + 1"));
        Assertions.assertThrows(RuntimeException.class, () -> eval("-'a'"));
    }

    @Test
    @DisplayName("A1: comparison binds tighter than equality")
    public void comparisonOverEquality() {
        // 0 == (1 < 2) -> 0 == 1 -> false. (Old: (0==1)<2 -> 0<2 -> 1.)
        Assertions.assertEquals(0.0, eval("0 == 1 < 2"));
    }

    @Test
    @DisplayName("A1: && binds tighter than ||")
    public void andOverOr() {
        // 1 || (1 && 0) -> 1. (Old grouping (1||1)&&0 -> 0.)
        Assertions.assertEquals(1.0, eval("1 || 1 && 0"));
    }

    @Test
    @DisplayName("A1: * binds tighter than +")
    public void productOverSum() {
        Assertions.assertEquals(14.0, eval("2 + 3 * 4"));
    }

    @Test
    @DisplayName("A2: ternary is right-associative")
    public void ternaryRightAssociative() {
        // 1 ? 2 : (0 ? 3 : 4) -> 2. (Old left-assoc: (1?2:0)?3:4 -> 3.)
        Assertions.assertEquals(2.0, eval("1 ? 2 : 0 ? 3 : 4"));
    }

    @Test
    @DisplayName("A3: ?? returns default for an unset variable")
    public void coalesceUnset() {
        Assertions.assertEquals(1.2, eval("v.y ?? 1.2"), 1e-9);
    }

    @Test
    @DisplayName("A3: ?? returns the variable when set, even to 0")
    public void coalesceSetToZero() {
        Assertions.assertEquals(0.0, eval("v.y = 0; return v.y ?? 1.2;"));
        Assertions.assertEquals(5.0, eval("v.y = 5; return v.y ?? 1.2;"));
    }

    @Test
    @DisplayName("A4: arrow on a non-entity returns 0")
    public void arrowErrorReturnsZero() {
        Assertions.assertEquals(0.0, eval("1 -> v.x"));
    }

    @Test
    @DisplayName("A5: hermite_blend uses 3t^2 - 2t^3")
    public void hermiteBlend() {
        Assertions.assertEquals(0.5, eval("math.hermite_blend(0.5)"), 1e-9);
        Assertions.assertEquals(0.0, eval("math.hermite_blend(0)"), 1e-9);
        Assertions.assertEquals(1.0, eval("math.hermite_blend(1)"), 1e-9);
    }

    @Test
    @DisplayName("A5: trunc rounds toward zero")
    public void truncTowardZero() {
        Assertions.assertEquals(-1.0, eval("math.trunc(-1.5)"));
        Assertions.assertEquals(1.0, eval("math.trunc(1.9)"));
    }

    @Test
    @DisplayName("A5: inverse_lerp / min_angle / lerprotate")
    public void newMathFunctions() {
        Assertions.assertEquals(0.5, eval("math.inverse_lerp(0, 10, 5)"), 1e-9);
        Assertions.assertEquals(-90.0, eval("math.min_angle(270)"), 1e-9);
        Assertions.assertEquals(5.0, eval("math.lerprotate(0, 10, 0.5)"), 1e-9);
    }

    @Test
    @DisplayName("A6: negative array index clamps to 0")
    public void arrayIndexClamp() {
        Assertions.assertEquals(5.0, eval("array.test.0 = 5; return array.test[-1];"));
    }

    @Test
    @DisplayName("A7: loop counter is capped at 1024")
    public void loopCap() {
        Assertions.assertEquals(1024.0, eval("v.x = 0; loop(5000, { v.x = v.x + 1; }); return v.x;"));
    }

    // ---- variable-read CSE correctness: a cached read must be invalidated on mutation ----

    @Test
    @DisplayName("CSE: a re-read after reassignment sees the new value")
    public void cseReadAfterWrite() {
        // v.b = 3+3 = 6; then v.a := 10; result = 6 + 10 + 10 = 26 (not the cached 3).
        Assertions.assertEquals(26.0, eval("v.a = 3; v.b = v.a + v.a; v.a = 10; return v.b + v.a + v.a;"));
    }

    @Test
    @DisplayName("CSE: condition + both ternary arms + trailing read reuse one value")
    public void cseAcrossTernary() {
        Assertions.assertEquals(6.0, eval("v.a = 2; return (v.a > 1 ? v.a + v.a : 0) + v.a;"));
    }

    @Test
    @DisplayName("CSE: a mutating loop re-reads the variable each iteration")
    public void cseInMutatingLoop() {
        // iter0: b=0,a=1; iter1: b=2,a=2; iter2: b=6,a=3 -> 6.
        Assertions.assertEquals(6.0, eval("v.a = 0; v.b = 0; loop(3, { v.b = v.b + v.a + v.a; v.a = v.a + 1; }); return v.b;"));
    }
}
