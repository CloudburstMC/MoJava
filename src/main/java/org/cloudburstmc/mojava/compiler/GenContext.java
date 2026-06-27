package org.cloudburstmc.mojava.compiler;

import org.objectweb.asm.MethodVisitor;

import java.util.HashMap;
import java.util.Map;

/** Per-method codegen state: the writer, the local-slot allocator, and the currently-bound scope/env. */
final class GenContext {
    final MethodVisitor mv;
    final String className;

    // When true, the program has no control flow, so the scope is never touched: skip all
    // getReturnValue/isBreak/isContinue polling and emit no scope references.
    final boolean fastPath;

    // slot 0 = this, 1 = MoScope, 2 = MoEnvironment
    int scopeSlot = 1;
    int envSlot = 2;
    private int nextLocal = 3;

    // Common-subexpression cache: "variable.t"/"temp.x" -> local double slot holding the value already
    // read this evaluation. Reused for repeated pure reads; cleared/scoped by the compiler at any write,
    // call, env switch, or control-flow merge so a slot is only loaded where it's definitely assigned.
    Map<String, Integer> varCache = new HashMap<>();

    GenContext(MethodVisitor mv, String className, boolean fastPath) {
        this.mv = mv;
        this.className = className;
        this.fastPath = fastPath;
    }

    int alloc() {
        return nextLocal++;
    }

    int allocDouble() {
        int slot = nextLocal;
        nextLocal += 2; // a double occupies two local slots
        return slot;
    }

    Map<String, Integer> snapshotVarCache() {
        return new HashMap<>(varCache);
    }

    void restoreVarCache(Map<String, Integer> snapshot) {
        varCache = new HashMap<>(snapshot);
    }

    void clearVarCache() {
        varCache.clear();
    }
}
