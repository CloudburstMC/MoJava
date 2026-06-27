package org.cloudburstmc.mojava.runtime;

import java.util.function.Function;

/**
 * Opt-in query registration that returns a primitive {@code double}. A plain
 * {@code Function<MoParams, Object>} boxes every numeric result twice — once to {@code Double}
 * (autobox) and once to {@code DoubleValue}. Registering a query as a {@code DoubleQueryFunction}
 * (via {@code QueryStruct.addDoubleFunction}) lets the engine skip the {@code Double} autobox always,
 * and the {@code DoubleValue} too whenever the result feeds the numeric compiled path.
 *
 * <p>It extends {@link Function} so it still works anywhere a generic query is expected; the default
 * {@link #apply} only boxes for callers that don't take the primitive fast path.
 */
@FunctionalInterface
public interface DoubleQueryFunction extends Function<MoParams, Object> {

    double applyAsDouble(MoParams params);

    @Override
    default Object apply(MoParams params) {
        return applyAsDouble(params);
    }
}
