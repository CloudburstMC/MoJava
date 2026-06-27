package org.cloudburstmc.mojava;

import org.cloudburstmc.mojava.runtime.MoRuntime;
import org.cloudburstmc.mojava.runtime.value.DoubleValue;
import org.cloudburstmc.mojava.runtime.value.StringValue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards that every benchmark workload (W1–W6 in {@code WorkloadBenchmark}) parses, compiles and
 * evaluates with the intended query/variable wiring — so a long {@code ./gradlew jmh} run can't fail
 * on a setup typo. Also pins the expected result of each script.
 */
@DisplayName("Workload smoke test")
public class WorkloadSmokeTest {

    private static final String W1 = "math.sin(q.life_time * 90) * 30 + math.cos(q.life_time * 45)";
    private static final String W2 =
            "math.cos(v.t * 38) * 2.0 + v.t * v.t + math.sqrt(v.t * v.t + 1.0) - (v.t > 1.0 ? v.t * 0.5 + 3.0 : v.t - 1.0) / 2.0";
    private static final String W3 = "query.is_on_ground * query.modified_distance_moved + query.anim_time(0)";
    private static final String W4 = "q.is_baby ? 0.5 : (v.health > 10 ? 1.0 : 0.25)";
    private static final String W5 = "t.sum = 0; loop(10, { t.sum = t.sum + math.sqrt(t.sum + 1.0); }); t.sum";
    private static final String W6 = "q.mark_variant == 'red' ? 1 : 0";

    private MoRuntime runtime() {
        MoRuntime runtime = MoJava.createRuntime();
        var env = runtime.getEnvironment();
        env.variable.setDirectly("t", DoubleValue.ZERO);
        env.variable.setDirectly("health", new DoubleValue(12.0));
        env.query.addFunction("life_time", p -> 0.5);
        env.query.addFunction("is_on_ground", p -> 1.0);
        env.query.addFunction("modified_distance_moved", p -> 0.7);
        env.query.addFunction("anim_time", p -> 0.3);
        env.query.addFunction("is_baby", p -> 0.0);
        env.query.addFunction("mark_variant", p -> new StringValue("red"));
        return runtime;
    }

    private double eval(String code) {
        MoRuntime r = runtime();
        return r.executeDouble(r.compile(MoJava.parse(code)));
    }

    @Test
    @DisplayName("each workload evaluates to its expected value")
    public void workloadsEvaluate() {
        Assertions.assertTrue(Double.isFinite(eval(W1)), "W1 finite");           // ~22.14
        Assertions.assertEquals(3.5, eval(W2));                                   // v.t = 0
        Assertions.assertEquals(1.0, eval(W3));                                   // 1*0.7 + 0.3
        Assertions.assertEquals(1.0, eval(W4));                                   // not baby, health 12 > 10
        Assertions.assertTrue(eval(W5) > 0.0, "W5 accumulates");                  // loop sum
        Assertions.assertEquals(1.0, eval(W6));                                   // mark_variant == 'red'
    }
}
