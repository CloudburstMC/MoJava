package org.cloudburstmc.mojava.compiler;

import org.cloudburstmc.mojava.runtime.MoEnvironment;
import org.cloudburstmc.mojava.runtime.MoScope;
import org.cloudburstmc.mojava.runtime.value.MoValue;

/**
 * A compiled Molang program. The generated class implements this; its {@link #evaluate} mirrors the
 * top-level evaluation loop of {@code MoRuntime.execute(List)} (final return value or last
 * expression result). The surrounding lifecycle — scope creation, temp clearing, context add/remove
 * — stays owned by {@code MoRuntime}.
 */
public interface MoScript {
    MoValue evaluate(MoScope scope, MoEnvironment environment);

    /**
     * Primitive-double result path. The generated class overrides this with an unboxed body when the
     * whole program is statically numeric and free of control flow, so a number-consuming caller
     * (the common animation/particle case) never allocates the terminal {@code DoubleValue}. The
     * default delegates to {@link #evaluate} and unboxes, so every script still answers correctly.
     */
    default double evaluateDouble(MoScope scope, MoEnvironment environment) {
        return evaluate(scope, environment).asDouble();
    }

    /**
     * False when the program contains no {@code return}/{@code break}/{@code continue}/loop, so it
     * never reads or writes the scope. {@code MoRuntime} then skips allocating a per-eval
     * {@link MoScope} and passes the shared sentinel instead.
     */
    default boolean usesScope() {
        return true;
    }
}
