package org.cloudburstmc.mojava.runtime;

import org.cloudburstmc.mojava.Expression;
import org.cloudburstmc.mojava.compiler.BytecodeCompiler;
import org.cloudburstmc.mojava.compiler.MoScript;
import org.cloudburstmc.mojava.runtime.struct.ArrayStruct;
import org.cloudburstmc.mojava.runtime.struct.ContextStruct;
import org.cloudburstmc.mojava.runtime.struct.QueryStruct;
import org.cloudburstmc.mojava.runtime.struct.VariableStruct;
import org.cloudburstmc.mojava.runtime.value.MoValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MoRuntime {
    private final MoEnvironment environment = new MoEnvironment();
    private final Map<String, MoValue> noContext = new HashMap<>();

    public MoRuntime() {
        environment.setStruct("math", MoMath.LIBRARY);
        environment.setStruct("temp", new VariableStruct());
        environment.setStruct("variable", new VariableStruct());
        environment.setStruct("array", new ArrayStruct());
        environment.setStruct("query", new QueryStruct(new HashMap<>()));
    }

    // Convenience entry points: each compiles the AST to bytecode, then runs it. Performance-sensitive
    // callers should compile() once and reuse the MoScript across executions.

    public MoValue execute(Expression expression) {
        return execute(List.of(expression), noContext);
    }

    public MoValue execute(Expression expression, Map<String, MoValue> context) {
        return execute(List.of(expression), context);
    }

    public MoValue execute(List<Expression> expressions) {
        return execute(expressions, noContext);
    }

    public MoValue execute(List<Expression> expressions, Map<String, MoValue> context) {
        return execute(compile(expressions), context);
    }

    public MoEnvironment getEnvironment() {
        return environment;
    }

    // ---- compiled execution (the only execution path) ----

    public MoScript compile(List<Expression> expressions) {
        return BytecodeCompiler.compile(expressions);
    }

    public MoValue execute(MoScript script) {
        return execute(script, noContext);
    }

    public MoValue execute(MoScript script, Map<String, MoValue> context) {
        // Control-flow-free scripts never touch the scope, so reuse the shared sentinel instead of
        // allocating one per eval.
        var scope = script.usesScope() ? new MoScope() : MoScope.SHARED;
        if (!context.isEmpty()) {
            environment.setStruct("context", new ContextStruct(context));
        }
        // The compiled script already resolves "final return value, else last result" internally.
        MoValue result = script.evaluate(scope, environment);
        environment.temp.clear();
        environment.removeStruct("context");
        return scope.getReturnValue() != null ? scope.getReturnValue() : result;
    }

    // ---- primitive-double execution: no terminal DoubleValue allocation for numeric scripts ----

    public double executeDouble(MoScript script) {
        return executeDouble(script, noContext);
    }

    public double executeDouble(MoScript script, Map<String, MoValue> context) {
        var scope = script.usesScope() ? new MoScope() : MoScope.SHARED;
        if (!context.isEmpty()) {
            environment.setStruct("context", new ContextStruct(context));
        }
        // evaluateDouble already folds in the return-value path (the generated body for numeric
        // scripts, or evaluate().asDouble() for the rest).
        double result = script.evaluateDouble(scope, environment);
        environment.temp.clear();
        environment.removeStruct("context");
        return result;
    }
}
