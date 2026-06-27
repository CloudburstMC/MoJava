package org.cloudburstmc.mojava;

import org.cloudburstmc.mojava.runtime.MoParams;
import org.cloudburstmc.mojava.runtime.struct.QueryStruct;
import org.cloudburstmc.mojava.runtime.value.DoubleValue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Set;
import java.util.function.Function;

@DisplayName("Evaluator Test")
public class EvaluatorTest {

    private void eval(String file, double expected, boolean round) throws IOException {
        var parsed = MoJava.parse(getClass().getClassLoader().getResourceAsStream(file));
        var runtime = MoJava.createRuntime();
        var actual = runtime.execute(parsed).asDouble();

        Assertions.assertEquals(round ? Math.round(expected) : expected, round ? Math.round(actual) : actual);
    }

    @Test
    public void testEval3() throws IOException {
        eval("expr3.txt", (213 + 2 / 0.5 + 5 + 2 * 3), true);
    }

    @Test
    public void testEval4() throws IOException {
        eval("expr4.txt", (213 + 2 / 0.5 + 5 + 2 * 3) + 310.5 + (10 * Math.cos(270 * Math.PI / 180)) + 100, true);
    }

    @Test
    public void testEval5() throws IOException {
        eval("expr5.txt", 0, true);
    }

    @Test
    public void testEval6() throws IOException {
        eval("expr6.txt", 2, true);
    }

    @Test
    public void testEval7() throws IOException {
        // expr7 assigns a string to a variable; the expression's value is that string. A string has no
        // numeric value (that would be a content error -> 0.0), so assert the string result directly.
        var parsed = MoJava.parse(getClass().getClassLoader().getResourceAsStream("expr7.txt"));
        var runtime = MoJava.createRuntime();
        Assertions.assertEquals("hello", runtime.execute(parsed).asString());
    }
}
