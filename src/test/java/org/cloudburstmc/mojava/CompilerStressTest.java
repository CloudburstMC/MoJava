package org.cloudburstmc.mojava;

import org.cloudburstmc.mojava.runtime.MoRuntime;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Compiles and discards many distinct scripts to exercise the hidden-class loader at scale. Hidden
 * classes are individually GC-collectible, so this should run in bounded metaspace rather than
 * accumulating a class per script forever.
 */
@Tag("slow")
@DisplayName("Compiler stress")
public class CompilerStressTest {

    @Test
    @DisplayName("compile + run many distinct scripts")
    public void manyScripts() {
        for (int i = 0; i < 5000; i++) {
            MoRuntime runtime = MoJava.createRuntime();
            String code = "v.x = " + i + "; return v.x * 2 + math.sqrt(" + i + ");";
            double result = runtime.execute(runtime.compile(MoJava.parse(code))).asDouble();
            Assertions.assertEquals(i * 2 + Math.sqrt(i), result, 1e-9);
        }
    }
}
