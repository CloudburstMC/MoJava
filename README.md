# MoJava

A fast [MoLang](https://bedrock.dev/docs/stable/Molang) parser, compiler, and runtime for Java.

MoLang is Minecraft Bedrock's expression language for animations, particles, and render controllers.
MoJava parses MoLang source, **compiles it to JVM bytecode**, and evaluates the result — so a script
that's compiled once and reused runs at hand-written-Java speed.

> Requires **Java 17+**.

## Why MoJava

- **Compiles to JVM bytecode** (via ASM) into a GC-reclaimable hidden class — no tree-walking at eval time.
- **Allocation-free numeric evaluation.** Numeric scripts evaluate straight to a primitive `double`
  with zero heap allocation per call (`executeDouble`), so there's no per-frame GC pressure.
- **`math.*` inlined** to `java.lang.Math`, repeated variable reads cached, and `variable`/`temp`
  reads resolved to slot indices instead of map lookups.

## Getting started

```gradle
repositories {
    maven("https://repo.opencollab.dev/maven-releases/")
}
dependencies {
    implementation("org.cloudburstmc:mojava:0.0.1")
}
```

```java
import org.cloudburstmc.mojava.MoJava;
import org.cloudburstmc.mojava.compiler.MoScript;
import org.cloudburstmc.mojava.runtime.MoRuntime;
import org.cloudburstmc.mojava.runtime.value.DoubleValue;

MoRuntime runtime = MoJava.createRuntime();

// Seed a variable and register a query the script can read
runtime.getEnvironment().variable.setDirectly("speed", new DoubleValue(1.5));
runtime.getEnvironment().query.addDoubleFunction("life_time", params -> 0.5);

// Compile once...
MoScript script = runtime.compile(MoJava.parse("math.sin(q.life_time * 90) * v.speed"));

// ...evaluate as often as you like (allocation-free for numeric scripts)
double value = runtime.executeDouble(script);
```

For a one-off evaluation, skip the caching: `runtime.execute(MoJava.parse(code))` returns a `MoValue`
(call `.asDouble()` or `.asString()` on it).

## Variables and queries

`v.`/`variable.`, `t.`/`temp.`, `q.`/`query.`, and `c.`/`context.` resolve against the runtime's
environment:

```java
var env = runtime.getEnvironment();

// Variables (read/write from scripts; persist across evals)
env.variable.setDirectly("health", new DoubleValue(20));

// Queries — host callbacks the script can read or call
env.query.addFunction("entity_name", params -> new StringValue("creeper")); // any MoValue
env.query.addDoubleFunction("distance", params -> 12.5);                     // numeric fast path
```

Register numeric queries with **`addDoubleFunction`**: it returns a primitive `double`, skipping the
`Double`/`DoubleValue` boxing a generic `Function` incurs, so query reads on the numeric path stay
allocation-free. Use `addFunction` for queries that return strings or structs.

## Building

```bash
./gradlew build      # compile + run tests
./gradlew jmh        # run the benchmark suite (cross-engine + per-workload)
```

## License

Licensed under the [Apache License 2.0](LICENSE).
