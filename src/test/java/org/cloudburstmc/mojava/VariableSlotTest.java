package org.cloudburstmc.mojava;

import org.cloudburstmc.mojava.runtime.MoRuntime;
import org.cloudburstmc.mojava.runtime.value.DoubleValue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * OPT-9: variable reads/writes go through a slot array, but the {@code map} must stay in sync so
 * {@code getMap()}/{@code getSimpleVariable} and the {@code setDirectly} entry point keep working.
 * These pin the write-through invariant in both directions.
 */
@DisplayName("Variable slots (OPT-9)")
public class VariableSlotTest {

    @Test
    @DisplayName("slot write is visible through getSimpleVariable (slots -> map)")
    public void slotWriteVisibleInMap() {
        MoRuntime r = MoJava.createRuntime();
        double b = r.executeDouble(r.compile(MoJava.parse("v.a = 5; v.b = v.a * 2; return v.b;")));
        Assertions.assertEquals(10.0, b);
        Assertions.assertEquals(5.0, r.getEnvironment().getSimpleVariable("a").asDouble());
        Assertions.assertEquals(10.0, r.getEnvironment().getSimpleVariable("b").asDouble());
    }

    @Test
    @DisplayName("setDirectly is visible to a compiled slot read (map -> slots)")
    public void setDirectlyVisibleToSlotRead() {
        MoRuntime r = MoJava.createRuntime();
        r.getEnvironment().variable.setDirectly("c", new DoubleValue(7.0));
        Assertions.assertEquals(8.0, r.executeDouble(r.compile(MoJava.parse("v.c + 1"))));
    }

    @Test
    @DisplayName("re-read after reassignment sees the new value")
    public void reassignment() {
        MoRuntime r = MoJava.createRuntime();
        Assertions.assertEquals(10.0, r.executeDouble(r.compile(MoJava.parse("v.x = 1; v.x = v.x + 9; return v.x;"))));
    }

    @Test
    @DisplayName("temp is cleared between executions")
    public void tempClearedBetweenRuns() {
        MoRuntime r = MoJava.createRuntime();
        var script = r.compile(MoJava.parse("t.z = t.z + 5; return t.z;"));
        Assertions.assertEquals(5.0, r.executeDouble(script));
        Assertions.assertEquals(5.0, r.executeDouble(script)); // not 10 — temp.clear() reset the slot
    }
}
