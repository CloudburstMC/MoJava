package org.cloudburstmc.mojava.jmh;

import org.cloudburstmc.mojava.Expression;
import org.cloudburstmc.mojava.MoJava;
import org.cloudburstmc.mojava.compiler.MoScript;
import org.cloudburstmc.mojava.runtime.MoRuntime;
import org.cloudburstmc.mojava.runtime.value.DoubleValue;
import org.cloudburstmc.mojava.runtime.value.StringValue;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * mojava-only workload matrix (W1–W7) from {@code OPTIMIZATIONS.md}. Each workload mirrors a real
 * Bedrock MoLang use case and stresses a different part of the engine, so optimisation gains can be
 * attributed per workload. The cross-engine (mojava / mocha / molang-compiler) comparison lives in
 * {@link MolangBenchmark}; this suite isolates mojava's own before/after.
 *
 * <p>Run with {@code ./gradlew jmh}. The {@code gc} profiler is enabled in {@code build.gradle.kts},
 * so each result reports {@code gc.alloc.rate.norm} — bytes allocated per op — which is the primary
 * signal for the allocation-elimination work (OPT-1/2/3/4).
 *
 * <ul>
 *   <li><b>W1 pureMath</b> — trig/FP animation curve; {@code math.*} inline + result box.</li>
 *   <li><b>W2 var</b> — the shared {@link MolangBenchmark} script; variable map lookups + CSE.</li>
 *   <li><b>W3 query</b> — host-callback heavy; query-call + lambda boxing (OPT-1 can't help, by design).</li>
 *   <li><b>W4 conditional</b> — nested numeric ternary; the unboxed-ternary path (OPT-1).</li>
 *   <li><b>W5 loop</b> — sub-scope + back-edge; stays on the boxed/scope path.</li>
 *   <li><b>W6 stringEq</b> — {@code StringValue.equals} feeding a numeric ternary.</li>
 *   <li><b>W7 compileBurst</b> — parse + codegen of all of the above (content-load throughput).</li>
 * </ul>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class WorkloadBenchmark {

    static final String W1 = "math.sin(q.life_time * 90) * 30 + math.cos(q.life_time * 45)";
    static final String W2 =
            "math.cos(v.t * 38) * 2.0 + v.t * v.t + math.sqrt(v.t * v.t + 1.0) - (v.t > 1.0 ? v.t * 0.5 + 3.0 : v.t - 1.0) / 2.0";
    static final String W3 =
            "query.is_on_ground * query.modified_distance_moved + query.anim_time(0)";
    static final String W4 = "q.is_baby ? 0.5 : (v.health > 10 ? 1.0 : 0.25)";
    static final String W5 = "t.sum = 0; loop(10, { t.sum = t.sum + math.sqrt(t.sum + 1.0); }); t.sum";
    static final String W6 = "q.mark_variant == 'red' ? 1 : 0";

    private MoRuntime runtime;
    private MoScript s1, s2, s3, s4, s5, s6;

    @Setup
    public void setup() {
        runtime = MoJava.createRuntime();
        var env = runtime.getEnvironment();

        // Variables (persist across evals; only temp is cleared).
        env.variable.setDirectly("t", DoubleValue.ZERO);
        env.variable.setDirectly("health", new DoubleValue(12.0));

        // Numeric query host callbacks registered as primitive-double queries (OPT-10) so they skip the
        // Double/DoubleValue boxing. The string query (W6) stays a boxed Function — it returns a
        // StringValue (MoValue.of treats a bare String as the numeric default 1.0).
        env.query.addDoubleFunction("life_time", p -> 0.5);
        env.query.addDoubleFunction("is_on_ground", p -> 1.0);
        env.query.addDoubleFunction("modified_distance_moved", p -> 0.7);
        env.query.addDoubleFunction("anim_time", p -> 0.3);
        env.query.addDoubleFunction("is_baby", p -> 0.0);
        env.query.addFunction("mark_variant", p -> new StringValue("red"));

        s1 = runtime.compile(MoJava.parse(W1));
        s2 = runtime.compile(MoJava.parse(W2));
        s3 = runtime.compile(MoJava.parse(W3));
        s4 = runtime.compile(MoJava.parse(W4));
        s5 = runtime.compile(MoJava.parse(W5));
        s6 = runtime.compile(MoJava.parse(W6));
    }

    // ---- eval: compiled form -> double (the steady-state hot path) ----

    @Benchmark public double W1_pureMath_eval()    { return runtime.executeDouble(s1); }
    @Benchmark public double W2_var_eval()          { return runtime.executeDouble(s2); }
    @Benchmark public double W3_query_eval()        { return runtime.executeDouble(s3); }
    @Benchmark public double W4_conditional_eval()  { return runtime.executeDouble(s4); }
    @Benchmark public double W5_loop_eval()         { return runtime.executeDouble(s5); }
    @Benchmark public double W6_stringEq_eval()     { return runtime.executeDouble(s6); }

    // ---- compile: source string -> executable form ----

    @Benchmark public MoScript W1_pureMath_compile()   { return runtime.compile(MoJava.parse(W1)); }
    @Benchmark public MoScript W2_var_compile()         { return runtime.compile(MoJava.parse(W2)); }
    @Benchmark public MoScript W3_query_compile()       { return runtime.compile(MoJava.parse(W3)); }
    @Benchmark public MoScript W4_conditional_compile() { return runtime.compile(MoJava.parse(W4)); }
    @Benchmark public MoScript W5_loop_compile()        { return runtime.compile(MoJava.parse(W5)); }
    @Benchmark public MoScript W6_stringEq_compile()    { return runtime.compile(MoJava.parse(W6)); }

    // ---- W7: parse + codegen burst across every workload (content-load throughput) ----

    @Benchmark
    public int W7_compileBurst() {
        int sink = 0;
        for (String code : List.of(W1, W2, W3, W4, W5, W6)) {
            sink += runtime.compile(MoJava.parse(code)).hashCode();
        }
        return sink;
    }
}
