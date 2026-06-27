package org.cloudburstmc.mojava;

import java.util.HashMap;
import java.util.Map;

/**
 * A parsed Molang AST node. Nodes are pure data — the compiler ({@code compiler.BytecodeCompiler})
 * reads them via their getters and emits bytecode. There is no tree-walking interpreter in the
 * library.
 */
public interface Expression {
    String getOriginalString();
    void setOriginalString(String string);
    Map<String, Object> attributes = new HashMap<>();

    default Map<String, Object> getAttributes() {
        return attributes;
    }
}
