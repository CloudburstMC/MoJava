package org.cloudburstmc.mojava.compiler;

import org.cloudburstmc.mojava.Expression;
import org.cloudburstmc.mojava.ast.*;
import org.cloudburstmc.mojava.ast.BinaryOpExpression.Operator;
import org.cloudburstmc.mojava.runtime.struct.VariableStruct;
import org.cloudburstmc.mojava.runtime.value.DoubleValue;
import org.cloudburstmc.mojava.runtime.value.MoValue;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.objectweb.asm.Opcodes.*;

/**
 * Compiles a parsed Molang AST into a JVM class implementing {@link MoScript}. The emitted
 * bytecode delegates all fiddly semantics to {@link CompilerSupport}; this class is responsible only
 * for control flow, value flow, and constant handling.
 *
 * <p>Discipline that keeps {@code COMPUTE_FRAMES} happy: every node's {@code compile} leaves exactly
 * one value typed as {@code MoValue} on the stack, so the frame merger never has to find a common
 * supertype of two concrete value classes (which would collapse to {@code Object} and break later
 * interface calls).
 */
public final class BytecodeCompiler {

    static final String MOVALUE = "org/cloudburstmc/mojava/runtime/value/MoValue";
    static final String MOSCOPE = "org/cloudburstmc/mojava/runtime/MoScope";
    static final String MOENV = "org/cloudburstmc/mojava/runtime/MoEnvironment";
    static final String VARSTRUCT = "org/cloudburstmc/mojava/runtime/struct/VariableStruct";
    static final String SUPPORT = "org/cloudburstmc/mojava/compiler/CompilerSupport";
    static final String SCRIPT = "org/cloudburstmc/mojava/compiler/MoScript";
    static final String GEN_NAME = "org/cloudburstmc/mojava/compiler/GeneratedMoScript";

    static final String D_MOVALUE = "L" + MOVALUE + ";";
    static final String D_EVAL = "(L" + MOSCOPE + ";L" + MOENV + ";)" + D_MOVALUE;
    static final String D_EVAL_DOUBLE = "(L" + MOSCOPE + ";L" + MOENV + ";)D";
    // Name paths are baked as List<String> constants so the runtime never wraps a String[] per call.
    static final String LIST = "java/util/List";
    static final String D_LIST = "L" + LIST + ";";

    /** Thrown when a node can't be compiled; the caller should fall back to interpretation. */
    public static final class UnsupportedExpression extends RuntimeException {
        UnsupportedExpression(String message) {
            super(message);
        }
    }

    /**
     * Thrown at compile time for an expression that violates Molang's type rules in a way detectable
     * from the source alone — currently, a string literal used anywhere except {@code ==}/{@code !=}.
     * Mirrors the engine's "content error" for cases like {@code 'text' + 1}.
     */
    public static final class ContentError extends RuntimeException {
        ContentError(String message) {
            super(message);
        }
    }

    private final List<Object> constants = new ArrayList<>();

    // Bootstrap for loading a constant from the hidden class's class data via a dynamic constant (condy).
    // MethodHandles.classDataAt(lookup, name, type, index) has exactly the condy bootstrap shape and
    // casts the element to the condy's declared type, so it doubles as the per-constant CHECKCAST.
    private static final Handle CLASS_DATA_BSM = new Handle(
            H_INVOKESTATIC,
            "java/lang/invoke/MethodHandles",
            "classDataAt",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;I)Ljava/lang/Object;",
            false);

    public static MoScript compile(List<Expression> expressions) {
        BytecodeCompiler compiler = new BytecodeCompiler();
        byte[] bytes = compiler.generate(expressions);
        return compiler.instantiate(bytes);
    }

    /** Emits the class bytes for a script without loading them. Intended for tests/verification. */
    public static byte[] compileToBytes(List<Expression> expressions) {
        return new BytecodeCompiler().generate(expressions);
    }

    private int constant(Object value) {
        // Dedup by value (DoubleValue/StringValue/List all have value equality) so a literal or name
        // path used many times collapses to one class-data slot and one pooled condy.
        int existing = constants.indexOf(value);
        if (existing >= 0) {
            return existing;
        }
        constants.add(value);
        return constants.size() - 1;
    }

    private byte[] generate(List<Expression> expressions) {
        for (Expression expr : expressions) {
            validateStrings(expr); // reject statically-detectable string misuse before emitting anything
        }
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES) {
            @Override
            protected String getCommonSuperClass(String type1, String type2) {
                try {
                    return super.getCommonSuperClass(type1, type2);
                } catch (Throwable t) {
                    // The generated class isn't loadable during its own frame computation; it never
                    // appears in a value merge thanks to the MoValue-typing discipline.
                    return "java/lang/Object";
                }
            }
        };
        cw.visit(V17, ACC_PUBLIC | ACC_FINAL | ACC_SUPER, GEN_NAME, null, "java/lang/Object", new String[]{SCRIPT});
        // Constants live in the hidden class's class data and load via condy (see emitLoadConstant), so
        // there is no per-instance constants field or constructor argument.

        MethodVisitor ctor = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(ALOAD, 0);
        ctor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitInsn(RETURN);
        ctor.visitMaxs(0, 0);
        ctor.visitEnd();

        // A program with no return/break/continue/loop never touches the scope, so it can skip the
        // per-expression return polling (OPT-2) and qualifies for the unboxed result path (OPT-1).
        boolean fastPath = true;
        for (Expression expr : expressions) {
            if (hasControlFlow(expr)) {
                fastPath = false;
                break;
            }
        }

        // evaluate(MoScope, MoEnvironment)
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "evaluate", D_EVAL, null, null);
        mv.visitCode();
        GenContext ctx = new GenContext(mv, GEN_NAME, fastPath);
        if (fastPath) {
            emitTopLevelFast(expressions, ctx);
        } else {
            emitTopLevel(expressions, ctx);
        }
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        if (fastPath) {
            // usesScope() -> false: let MoRuntime reuse the shared scope instead of allocating.
            MethodVisitor us = cw.visitMethod(ACC_PUBLIC, "usesScope", "()Z", null, null);
            us.visitCode();
            us.visitInsn(ICONST_0);
            us.visitInsn(IRETURN);
            us.visitMaxs(0, 0);
            us.visitEnd();

            // evaluateDouble(...) only when the result is statically numeric, so it returns a real
            // double with no terminal DoubleValue allocation.
            Expression last = expressions.isEmpty() ? null : expressions.get(expressions.size() - 1);
            if (last != null && numeric(last)) {
                MethodVisitor dv = cw.visitMethod(ACC_PUBLIC, "evaluateDouble", D_EVAL_DOUBLE, null, null);
                dv.visitCode();
                emitTopLevelDouble(expressions, new GenContext(dv, GEN_NAME, true));
                dv.visitMaxs(0, 0);
                dv.visitEnd();
            }
        }

        cw.visitEnd();
        return cw.toByteArray();
    }

    private MoScript instantiate(byte[] bytes) {
        try {
            // Constants ride along as the hidden class's class data; condy reads them back via classDataAt.
            MethodHandles.Lookup lookup = MethodHandles.lookup()
                    .defineHiddenClassWithClassData(bytes, List.copyOf(constants), true);
            Class<?> cls = lookup.lookupClass();
            MethodHandle ctor = lookup.findConstructor(cls, MethodType.methodType(void.class));
            return (MoScript) ctor.invoke();
        } catch (Throwable t) {
            throw new RuntimeException("Failed to define/instantiate compiled Molang script", t);
        }
    }

    // ---- top level: mirrors MoRuntime.execute(List): final return value, else last result ----

    private void emitTopLevel(List<Expression> expressions, GenContext ctx) {
        MethodVisitor mv = ctx.mv;
        int resultSlot = ctx.alloc();
        mv.visitFieldInsn(GETSTATIC, "org/cloudburstmc/mojava/runtime/value/DoubleValue", "ZERO",
                "Lorg/cloudburstmc/mojava/runtime/value/DoubleValue;");
        mv.visitTypeInsn(CHECKCAST, MOVALUE);
        mv.visitVarInsn(ASTORE, resultSlot);

        Label finish = new Label();
        for (Expression expr : expressions) {
            compile(expr, ctx);
            mv.visitVarInsn(ASTORE, resultSlot);
            mv.visitVarInsn(ALOAD, ctx.scopeSlot);
            mv.visitMethodInsn(INVOKEVIRTUAL, MOSCOPE, "getReturnValue", "()" + D_MOVALUE, false);
            mv.visitJumpInsn(IFNONNULL, finish);
        }

        mv.visitLabel(finish);
        mv.visitVarInsn(ALOAD, ctx.scopeSlot);
        mv.visitMethodInsn(INVOKEVIRTUAL, MOSCOPE, "getReturnValue", "()" + D_MOVALUE, false);
        mv.visitInsn(DUP);
        Label useReturn = new Label();
        mv.visitJumpInsn(IFNONNULL, useReturn);
        mv.visitInsn(POP);
        mv.visitVarInsn(ALOAD, resultSlot);
        mv.visitInsn(ARETURN);
        mv.visitLabel(useReturn);
        mv.visitInsn(ARETURN);
    }

    // ---- fast path (no control flow): no scope, no return polling ----

    // Boxed result: run each expression, the last one's value is the result. No return can fire
    // (gated by hasControlFlow), so there is nothing to poll.
    private void emitTopLevelFast(List<Expression> expressions, GenContext ctx) {
        MethodVisitor mv = ctx.mv;
        if (expressions.isEmpty()) {
            pushZero(ctx);
            mv.visitInsn(ARETURN);
            return;
        }
        int last = expressions.size() - 1;
        for (int i = 0; i < last; i++) {
            compile(expressions.get(i), ctx);
            mv.visitInsn(POP); // earlier expressions run only for their side effects
        }
        compile(expressions.get(last), ctx);
        mv.visitInsn(ARETURN);
    }

    // Primitive-double result: same as the fast boxed path, but the final value stays unboxed.
    private void emitTopLevelDouble(List<Expression> expressions, GenContext ctx) {
        MethodVisitor mv = ctx.mv;
        int last = expressions.size() - 1;
        for (int i = 0; i < last; i++) {
            compile(expressions.get(i), ctx);
            mv.visitInsn(POP);
        }
        compileDouble(expressions.get(last), ctx);
        mv.visitInsn(DRETURN);
    }

    // True when the AST contains a return/break/continue/loop/for-each anywhere — i.e. anything that
    // reads or writes the scope. Mirrors the structural walk in validateStrings.
    private static boolean hasControlFlow(Expression e) {
        if (e == null) {
            return false;
        }
        if (e instanceof ReturnExpression || e instanceof BreakExpression
                || e instanceof ContinueExpression || e instanceof LoopExpression
                || e instanceof ForEachExpression) {
            return true;
        }
        if (e instanceof BinaryOpExpression b) {
            return hasControlFlow(b.getLeft()) || hasControlFlow(b.getRight());
        }
        if (e instanceof UnaryMinusExpression u) {
            return hasControlFlow(u.getExpression());
        }
        if (e instanceof UnaryPlusExpression u) {
            return hasControlFlow(u.getExpression());
        }
        if (e instanceof BooleanNotExpression n) {
            return hasControlFlow(n.getExpression());
        }
        if (e instanceof TernaryExpression t) {
            return hasControlFlow(t.getCondition()) || hasControlFlow(t.getThenExpr())
                    || hasControlFlow(t.getElseExpr());
        }
        if (e instanceof ArrayAccessExpression a) {
            return hasControlFlow(a.getArray()) || hasControlFlow(a.getIndex());
        }
        if (e instanceof FuncCallExpression c) {
            if (hasControlFlow(c.getName())) {
                return true;
            }
            for (Expression arg : c.getArgs()) {
                if (hasControlFlow(arg)) {
                    return true;
                }
            }
            return false;
        }
        if (e instanceof AssignExpression a) {
            return hasControlFlow(a.getExpr()) || hasControlFlow(a.getVariable());
        }
        if (e instanceof StatementExpression s) {
            for (Expression child : s.getExpressions()) {
                if (hasControlFlow(child)) {
                    return true;
                }
            }
            return false;
        }
        return false; // leaves
    }

    // ---- compile-time spec check: strings are usable only with == and != ----

    // Walks the AST and rejects a statically-known string used where Molang requires a number. Dynamic
    // strings (variables/queries) can't be caught here; those resolve to 0.0 at runtime instead.
    private static void validateStrings(Expression e) {
        if (e == null) {
            return;
        }
        if (e instanceof BinaryOpExpression b) {
            Operator op = b.getOperator();
            if (op != Operator.EQUAL && op != Operator.NOT_EQUAL
                    && op != Operator.COALESCE && op != Operator.ARROW) {
                requireNumeric(b.getLeft(), op.name());
                requireNumeric(b.getRight(), op.name());
            }
            validateStrings(b.getLeft());
            validateStrings(b.getRight());
        } else if (e instanceof UnaryMinusExpression u) {
            requireNumeric(u.getExpression(), "unary -");
            validateStrings(u.getExpression());
        } else if (e instanceof UnaryPlusExpression u) {
            validateStrings(u.getExpression());
        } else if (e instanceof BooleanNotExpression n) {
            requireNumeric(n.getExpression(), "!");
            validateStrings(n.getExpression());
        } else if (e instanceof TernaryExpression t) {
            requireNumeric(t.getCondition(), "?:");
            validateStrings(t.getCondition());
            validateStrings(t.getThenExpr());
            validateStrings(t.getElseExpr());
        } else if (e instanceof LoopExpression l) {
            requireNumeric(l.getCount(), "loop count");
            validateStrings(l.getCount());
            validateStrings(l.getBody());
        } else if (e instanceof ForEachExpression f) {
            validateStrings(f.getArray());
            validateStrings(f.getBody());
        } else if (e instanceof ArrayAccessExpression a) {
            requireNumeric(a.getIndex(), "array index");
            validateStrings(a.getArray());
            validateStrings(a.getIndex());
        } else if (e instanceof FuncCallExpression c) {
            validateStrings(c.getName());
            for (Expression arg : c.getArgs()) {
                validateStrings(arg); // function arguments may legitimately be strings
            }
        } else if (e instanceof AssignExpression a) {
            validateStrings(a.getExpr()); // the assigned value may be a string
            validateStrings(a.getVariable());
        } else if (e instanceof StatementExpression s) {
            for (Expression child : s.getExpressions()) {
                validateStrings(child);
            }
        } else if (e instanceof ReturnExpression r) {
            validateStrings(r.getExpression()); // a script may return a string
        }
        // leaves (Number/String/Boolean/Name/This/Break/Continue) carry no numeric-position children
    }

    private static void requireNumeric(Expression e, String position) {
        if (isStaticString(e)) {
            throw new ContentError("String operand is not allowed for '" + position
                    + "': Molang strings support only == and !=");
        }
    }

    private static boolean isStaticString(Expression e) {
        if (e instanceof StringExpression) {
            return true;
        }
        if (e instanceof UnaryPlusExpression u) {
            return isStaticString(u.getExpression());
        }
        return false;
    }

    // ---- node dispatch: each leaves one MoValue-typed value on the stack ----

    private void compile(Expression e, GenContext ctx) {
        if (e instanceof NumberExpression || e instanceof StringExpression || e instanceof BooleanExpression) {
            emitLiteral(e, ctx);
        } else if (e instanceof NameExpression) {
            emitName((NameExpression) e, ctx);
        } else if (e instanceof ThisExpression) {
            ctx.mv.visitVarInsn(ALOAD, ctx.envSlot);
            ctx.mv.visitTypeInsn(CHECKCAST, MOVALUE);
        } else if (e instanceof FuncCallExpression) {
            if (mathInline((FuncCallExpression) e) != null) {
                boxDouble(e, ctx); // inlined standard math.* -> primitive then box
            } else {
                emitFuncCall((FuncCallExpression) e, ctx);
            }
        } else if (e instanceof ArrayAccessExpression) {
            emitArrayGet((ArrayAccessExpression) e, ctx);
        } else if (e instanceof AssignExpression) {
            emitAssignExpression((AssignExpression) e, ctx);
        } else if (e instanceof UnaryPlusExpression) {
            compile(((UnaryPlusExpression) e).getExpression(), ctx); // identity
        } else if (e instanceof UnaryMinusExpression) {
            boxDouble(e, ctx); // Tier 2: -x is pure numeric
        } else if (e instanceof BooleanNotExpression) {
            compile(((BooleanNotExpression) e).getExpression(), ctx);
            invokeSupport(ctx, "not", "(" + D_MOVALUE + ")" + D_MOVALUE);
        } else if (e instanceof BinaryOpExpression) {
            BinaryOpExpression b = (BinaryOpExpression) e;
            switch (b.getOperator()) {
                case AND: emitShortCircuit(b, ctx, true); break;
                case OR: emitShortCircuit(b, ctx, false); break;
                case COALESCE: emitCoalesce(b, ctx); break;
                case ARROW: emitArrow(b, ctx); break;
                default: emitBinaryOp(b, ctx);
            }
        } else if (e instanceof TernaryExpression) {
            emitTernary((TernaryExpression) e, ctx);
        } else if (e instanceof StatementExpression) {
            emitStatement((StatementExpression) e, ctx);
        } else if (e instanceof LoopExpression) {
            emitLoop((LoopExpression) e, ctx);
        } else if (e instanceof ForEachExpression) {
            emitForEach((ForEachExpression) e, ctx);
        } else if (e instanceof ReturnExpression) {
            emitReturn((ReturnExpression) e, ctx);
        } else if (e instanceof BreakExpression) {
            emitFlagAndZero(ctx, "setBreak");
        } else if (e instanceof ContinueExpression) {
            emitFlagAndZero(ctx, "setContinue");
        } else {
            throw new UnsupportedExpression("Cannot compile " + e.getClass().getSimpleName());
        }
    }

    private void emitLiteral(Expression e, GenContext ctx) {
        emitLoadConstant(ctx, constant(literalValue(e)), MOVALUE);
    }

    private static MoValue literalValue(Expression e) {
        if (e instanceof NumberExpression) {
            return ((NumberExpression) e).getNumber();
        }
        if (e instanceof StringExpression) {
            return ((StringExpression) e).getString();
        }
        return ((BooleanExpression) e).isValue() ? DoubleValue.ONE : DoubleValue.ZERO;
    }

    private void emitName(NameExpression e, GenContext ctx) {
        String[] names = names(e);
        String field = fastVarField(names);
        if (field != null) {
            ctx.mv.visitVarInsn(ALOAD, ctx.envSlot);
            ctx.mv.visitFieldInsn(GETFIELD, MOENV, field, "L" + VARSTRUCT + ";");
            emitIntConst(ctx.mv, VariableStruct.slotFor(names[1]));
            invokeSupport(ctx, "varGet", "(L" + VARSTRUCT + ";I)" + D_MOVALUE);
            return;
        }
        ctx.mv.visitVarInsn(ALOAD, ctx.envSlot);
        emitLoadConstant(ctx, nameConst(e), LIST);
        invokeSupport(ctx, "getValue", "(L" + MOENV + ";" + D_LIST + ")" + D_MOVALUE);
        ctx.clearVarCache(); // a query/context getter may mutate variables
    }

    // The two-segment variable/temp structs are public fields on the environment; reading them
    // directly skips the split/iterator/getStruct dispatch.
    private static String fastVarField(String[] names) {
        if (names.length != 2) {
            return null;
        }
        if (names[0].equals("variable")) {
            return "variable";
        }
        if (names[0].equals("temp")) {
            return "temp";
        }
        return null;
    }

    private void emitFuncCall(FuncCallExpression e, GenContext ctx) {
        MethodVisitor mv = ctx.mv;
        Expression name = e.getName();
        if (name instanceof NameExpression) {
            mv.visitVarInsn(ALOAD, ctx.envSlot);
            emitLoadConstant(ctx, nameConst((NameExpression) name), LIST);
            emitArgArray(e.getArgs(), ctx);
            invokeSupport(ctx, "call", "(L" + MOENV + ";" + D_LIST + "[" + D_MOVALUE + ")" + D_MOVALUE);
        } else {
            mv.visitVarInsn(ALOAD, ctx.envSlot);
            compile(name, ctx);
            mv.visitMethodInsn(INVOKEINTERFACE, MOVALUE, "asString", "()Ljava/lang/String;", true);
            emitArgArray(e.getArgs(), ctx);
            invokeSupport(ctx, "callDynamic", "(L" + MOENV + ";Ljava/lang/String;[" + D_MOVALUE + ")" + D_MOVALUE);
        }
        ctx.clearVarCache(); // a called function may mutate variables
    }

    private void emitArgArray(Expression[] args, GenContext ctx) {
        MethodVisitor mv = ctx.mv;
        emitIntConst(mv, args.length);
        mv.visitTypeInsn(ANEWARRAY, MOVALUE);
        for (int i = 0; i < args.length; i++) {
            mv.visitInsn(DUP);
            emitIntConst(mv, i);
            compile(args[i], ctx);
            mv.visitInsn(AASTORE);
        }
    }

    private void emitArrayGet(ArrayAccessExpression e, GenContext ctx) {
        MethodVisitor mv = ctx.mv;
        Expression array = e.getArray();
        if (array instanceof NameExpression) {
            mv.visitVarInsn(ALOAD, ctx.envSlot);
            emitLoadConstant(ctx, nameConst((NameExpression) array), LIST);
            compile(e.getIndex(), ctx);
            invokeSupport(ctx, "arrayGet", "(L" + MOENV + ";" + D_LIST + D_MOVALUE + ")" + D_MOVALUE);
        } else {
            mv.visitVarInsn(ALOAD, ctx.envSlot);
            compile(array, ctx);
            mv.visitMethodInsn(INVOKEINTERFACE, MOVALUE, "asString", "()Ljava/lang/String;", true);
            compile(e.getIndex(), ctx);
            invokeSupport(ctx, "arrayGetDynamic", "(L" + MOENV + ";Ljava/lang/String;" + D_MOVALUE + ")" + D_MOVALUE);
        }
        ctx.clearVarCache(); // the base path may resolve through a query getter with side effects
    }

    private void emitAssignExpression(AssignExpression e, GenContext ctx) {
        MethodVisitor mv = ctx.mv;
        compile(e.getExpr(), ctx);
        int valueSlot = ctx.alloc();
        mv.visitVarInsn(ASTORE, valueSlot);
        emitAssignTo(e.getVariable(), valueSlot, ctx);
        ctx.clearVarCache(); // the written variable's cached value is now stale
        mv.visitVarInsn(ALOAD, valueSlot); // assignment evaluates to the assigned value
    }

    // Stores the MoValue in valueSlot into the given assignable target.
    private void emitAssignTo(Expression target, int valueSlot, GenContext ctx) {
        MethodVisitor mv = ctx.mv;
        if (target instanceof NameExpression) {
            String[] names = names((NameExpression) target);
            String field = fastVarField(names);
            if (field != null) {
                mv.visitVarInsn(ALOAD, ctx.envSlot);
                mv.visitFieldInsn(GETFIELD, MOENV, field, "L" + VARSTRUCT + ";");
                emitIntConst(mv, VariableStruct.slotFor(names[1]));
                mv.visitLdcInsn(names[1]); // name keeps the canonical map in sync
                mv.visitVarInsn(ALOAD, valueSlot);
                invokeSupport(ctx, "varSet", "(L" + VARSTRUCT + ";ILjava/lang/String;" + D_MOVALUE + ")V");
                return;
            }
            mv.visitVarInsn(ALOAD, ctx.envSlot);
            emitLoadConstant(ctx, nameConst((NameExpression) target), LIST);
            mv.visitVarInsn(ALOAD, valueSlot);
            invokeSupport(ctx, "setValue", "(L" + MOENV + ";" + D_LIST + D_MOVALUE + ")V");
        } else if (target instanceof ArrayAccessExpression) {
            ArrayAccessExpression arr = (ArrayAccessExpression) target;
            Expression base = arr.getArray();
            if (base instanceof NameExpression) {
                mv.visitVarInsn(ALOAD, ctx.envSlot);
                emitLoadConstant(ctx, nameConst((NameExpression) base), LIST);
                compile(arr.getIndex(), ctx);
                mv.visitVarInsn(ALOAD, valueSlot);
                invokeSupport(ctx, "arraySet", "(L" + MOENV + ";" + D_LIST + D_MOVALUE + D_MOVALUE + ")V");
            } else {
                mv.visitVarInsn(ALOAD, ctx.envSlot);
                compile(base, ctx);
                mv.visitMethodInsn(INVOKEINTERFACE, MOVALUE, "asString", "()Ljava/lang/String;", true);
                compile(arr.getIndex(), ctx);
                mv.visitVarInsn(ALOAD, valueSlot);
                invokeSupport(ctx, "arraySetDynamic", "(L" + MOENV + ";Ljava/lang/String;" + D_MOVALUE + D_MOVALUE + ")V");
            }
        } else {
            throw new UnsupportedExpression("Cannot assign to " + target.getClass().getSimpleName());
        }
    }

    private void emitBinaryOp(BinaryOpExpression e, GenContext ctx) {
        // Equality keeps the boxed cross-type semantics of MoValue.equals.
        if (e.getOperator() == Operator.EQUAL) {
            compile(e.getLeft(), ctx);
            compile(e.getRight(), ctx);
            invokeSupport(ctx, "equal", "(" + D_MOVALUE + D_MOVALUE + ")" + D_MOVALUE);
            return;
        }
        if (e.getOperator() == Operator.NOT_EQUAL) {
            compile(e.getLeft(), ctx);
            compile(e.getRight(), ctx);
            invokeSupport(ctx, "notEqual", "(" + D_MOVALUE + D_MOVALUE + ")" + D_MOVALUE);
            return;
        }
        // '+' is string-polymorphic: only take the numeric path when both sides are definitely numeric.
        if (e.getOperator() == Operator.ADD && !(numeric(e.getLeft()) && numeric(e.getRight()))) {
            compile(e.getLeft(), ctx);
            compile(e.getRight(), ctx);
            invokeSupport(ctx, "add", "(" + D_MOVALUE + D_MOVALUE + ")" + D_MOVALUE);
            return;
        }
        // Arithmetic and relational ops compute in primitive double (Tier 2), boxing once at the end.
        boxDouble(e, ctx);
    }

    // ---- Tier 2: primitive-double fast path ----

    // True when a node always yields a number (never a string/struct), so it can stay unboxed.
    private static boolean numeric(Expression e) {
        if (e instanceof NumberExpression || e instanceof BooleanExpression) return true;
        if (e instanceof BooleanNotExpression) return true;
        if (e instanceof UnaryMinusExpression) return true;
        if (e instanceof UnaryPlusExpression) return numeric(((UnaryPlusExpression) e).getExpression());
        if (e instanceof TernaryExpression) {
            TernaryExpression t = (TernaryExpression) e;
            // A null then-arm re-evaluates the condition as its value; a null else-arm yields 0.
            Expression then = t.getThenExpr() != null ? t.getThenExpr() : t.getCondition();
            return numeric(then) && (t.getElseExpr() == null || numeric(t.getElseExpr()));
        }
        if (e instanceof FuncCallExpression) return mathInline((FuncCallExpression) e) != null;
        if (e instanceof BinaryOpExpression) {
            BinaryOpExpression b = (BinaryOpExpression) e;
            switch (b.getOperator()) {
                case ADD: return numeric(b.getLeft()) && numeric(b.getRight()); // else might be string concat
                case COALESCE:
                case ARROW: return false; // may yield a string/struct/entity
                default: return true; // arithmetic, relational, equality, &&/|| are always numeric
            }
        }
        return false;
    }

    private static final Map<String, Integer> MATH_ARITY = new HashMap<>();
    static {
        for (String f : new String[]{"abs", "sqrt", "floor", "ceil", "exp", "ln", "round", "trunc",
                "sin", "cos", "acos", "asin", "atan", "d2r", "r2d"}) MATH_ARITY.put(f, 1);
        for (String f : new String[]{"pow", "mod", "min", "max", "atan2"}) MATH_ARITY.put(f, 2);
        MATH_ARITY.put("clamp", 3);
        MATH_ARITY.put("pi", 0);
    }

    // Returns the function name if this is a standard math.* call we inline to primitive Math.*, else null.
    private static String mathInline(FuncCallExpression e) {
        if (!(e.getName() instanceof NameExpression)) {
            return null;
        }
        List<String> ns = ((NameExpression) e.getName()).getNames();
        if (ns.size() != 2 || !ns.get(0).equals("math")) {
            return null;
        }
        String fn = ns.get(1);
        Integer arity = MATH_ARITY.get(fn);
        return (arity != null && arity == e.getArgs().length) ? fn : null;
    }

    // Emits the exact MoMath semantics as primitive double ops (degrees for trig, etc.).
    private void emitMathInline(String fn, Expression[] args, GenContext ctx) {
        MethodVisitor mv = ctx.mv;
        switch (fn) {
            case "pi": mv.visitLdcInsn(Math.PI); return;
            case "abs": compileDouble(args[0], ctx); math(ctx, "abs", "(D)D"); return;
            case "sqrt": compileDouble(args[0], ctx); math(ctx, "sqrt", "(D)D"); return;
            case "floor": compileDouble(args[0], ctx); math(ctx, "floor", "(D)D"); return;
            case "ceil": compileDouble(args[0], ctx); math(ctx, "ceil", "(D)D"); return;
            case "exp": compileDouble(args[0], ctx); math(ctx, "exp", "(D)D"); return;
            case "ln": compileDouble(args[0], ctx); math(ctx, "log", "(D)D"); return;
            case "round": compileDouble(args[0], ctx); math(ctx, "round", "(D)J"); mv.visitInsn(L2D); return;
            case "trunc": compileDouble(args[0], ctx); mv.visitInsn(D2L); mv.visitInsn(L2D); return;
            // MoMath sin/cos use exactly `x * PI / 180` — NOT Math.toRadians (`x / 180 * PI`),
            // which rounds differently (1 ULP). Replicate the exact operation order.
            case "sin": compileDouble(args[0], ctx); degToRad(ctx); math(ctx, "sin", "(D)D"); return;
            case "cos": compileDouble(args[0], ctx); degToRad(ctx); math(ctx, "cos", "(D)D"); return;
            case "acos": compileDouble(args[0], ctx); math(ctx, "acos", "(D)D"); math(ctx, "toDegrees", "(D)D"); return;
            case "asin": compileDouble(args[0], ctx); math(ctx, "asin", "(D)D"); math(ctx, "toDegrees", "(D)D"); return;
            case "atan": compileDouble(args[0], ctx); math(ctx, "atan", "(D)D"); math(ctx, "toDegrees", "(D)D"); return;
            case "d2r": compileDouble(args[0], ctx); math(ctx, "toRadians", "(D)D"); return;
            case "r2d": compileDouble(args[0], ctx); math(ctx, "toDegrees", "(D)D"); return;
            case "pow": compileDouble(args[0], ctx); compileDouble(args[1], ctx); math(ctx, "pow", "(DD)D"); return;
            case "mod": compileDouble(args[0], ctx); compileDouble(args[1], ctx); mv.visitInsn(DREM); return;
            case "min": compileDouble(args[0], ctx); compileDouble(args[1], ctx); math(ctx, "min", "(DD)D"); return;
            case "max": compileDouble(args[0], ctx); compileDouble(args[1], ctx); math(ctx, "max", "(DD)D"); return;
            case "atan2": compileDouble(args[0], ctx); compileDouble(args[1], ctx); math(ctx, "atan2", "(DD)D"); math(ctx, "toDegrees", "(D)D"); return;
            // clamp(v, min, max) = min(max(v, min), max) — Math.min is symmetric so arg order is fine.
            case "clamp": compileDouble(args[0], ctx); compileDouble(args[1], ctx); math(ctx, "max", "(DD)D");
                          compileDouble(args[2], ctx); math(ctx, "min", "(DD)D"); return;
            default: throw new UnsupportedExpression("math." + fn);
        }
    }

    private void math(GenContext ctx, String name, String descriptor) {
        ctx.mv.visitMethodInsn(INVOKESTATIC, "java/lang/Math", name, descriptor, false);
    }

    // x * Math.PI / 180, matching MoMath's sin/cos exactly (operation order matters for ULP parity).
    private void degToRad(GenContext ctx) {
        ctx.mv.visitLdcInsn(Math.PI);
        ctx.mv.visitInsn(DMUL);
        ctx.mv.visitLdcInsn(180.0);
        ctx.mv.visitInsn(DDIV);
    }

    // Leaves a primitive double on the stack.
    private void compileDouble(Expression e, GenContext ctx) {
        MethodVisitor mv = ctx.mv;
        if (e instanceof NumberExpression || e instanceof BooleanExpression) {
            mv.visitLdcInsn(literalValue(e).asDouble());
        } else if (e instanceof UnaryPlusExpression) {
            compileDouble(((UnaryPlusExpression) e).getExpression(), ctx);
        } else if (e instanceof UnaryMinusExpression) {
            compileDouble(((UnaryMinusExpression) e).getExpression(), ctx);
            mv.visitInsn(DNEG);
        } else if (e instanceof BinaryOpExpression && numericBinaryDouble((BinaryOpExpression) e, ctx)) {
            // handled inside numericBinaryDouble
        } else if (e instanceof FuncCallExpression && mathInline((FuncCallExpression) e) != null) {
            emitMathInline(mathInline((FuncCallExpression) e), ((FuncCallExpression) e).getArgs(), ctx);
        } else if (e instanceof TernaryExpression) {
            compileTernaryDouble((TernaryExpression) e, ctx); // unboxed ternary keeps the numeric path intact
        } else if (e instanceof NameExpression && fastVarField(names((NameExpression) e)) != null) {
            emitCachedVarDouble((NameExpression) e, ctx); // CSE: read v.x/t.x once, reuse
        } else if (e instanceof NameExpression) {
            // Non-fast name (query/context/deep variable) straight to double: a registered double-query
            // skips both the Double autobox and the DoubleValue. Same value as getValue(...).asDouble().
            mv.visitVarInsn(ALOAD, ctx.envSlot);
            emitLoadConstant(ctx, nameConst((NameExpression) e), LIST);
            invokeSupport(ctx, "getValueDouble", "(L" + MOENV + ";" + D_LIST + ")D");
            ctx.clearVarCache(); // a query/context getter may mutate variables
        } else {
            // Boxed producer (query call, ternary, equality, &&, etc.): coerce to double.
            compile(e, ctx);
            mv.visitMethodInsn(INVOKEINTERFACE, MOVALUE, "asDouble", "()D", true);
        }
    }

    // Reads a two-segment variable/temp value as a primitive double, caching it in a local so repeated
    // reads of the same path within one straight-line region collapse to a single map lookup. The cache
    // is invalidated by writes/calls/env-switches and scoped at branches, so a load only ever runs where
    // the slot is definitely assigned.
    private void emitCachedVarDouble(NameExpression e, GenContext ctx) {
        MethodVisitor mv = ctx.mv;
        String[] names = names(e);
        String field = fastVarField(names);
        String key = field + '.' + names[1];
        Integer slot = ctx.varCache.get(key);
        if (slot != null) {
            mv.visitVarInsn(DLOAD, slot);
            return;
        }
        mv.visitVarInsn(ALOAD, ctx.envSlot);
        mv.visitFieldInsn(GETFIELD, MOENV, field, "L" + VARSTRUCT + ";");
        emitIntConst(mv, VariableStruct.slotFor(names[1]));
        invokeSupport(ctx, "varGetDouble", "(L" + VARSTRUCT + ";I)D");
        int fresh = ctx.allocDouble();
        mv.visitInsn(DUP2);
        mv.visitVarInsn(DSTORE, fresh);
        ctx.varCache.put(key, fresh);
    }

    // Emits the primitive-double form of arithmetic/relational binary ops and returns true. Returns
    // false (emitting nothing) for ops that must use the boxed path (==, !=, &&, ||, ??, ->).
    private boolean numericBinaryDouble(BinaryOpExpression e, GenContext ctx) {
        MethodVisitor mv = ctx.mv;
        switch (e.getOperator()) {
            case ADD:
                if (!(numeric(e.getLeft()) && numeric(e.getRight()))) return false; // possible string concat
                compileDouble(e.getLeft(), ctx); compileDouble(e.getRight(), ctx); mv.visitInsn(DADD); return true;
            case SUBTRACT:
                compileDouble(e.getLeft(), ctx); compileDouble(e.getRight(), ctx); mv.visitInsn(DSUB); return true;
            case MULTIPLY:
                compileDouble(e.getLeft(), ctx); compileDouble(e.getRight(), ctx); mv.visitInsn(DMUL); return true;
            case DIVIDE:
                compileDouble(e.getLeft(), ctx); compileDouble(e.getRight(), ctx); mv.visitInsn(DDIV); return true;
            case GREATER: emitDoubleCompare(ctx, e, DCMPL, IFGT); return true;
            case GREATER_OR_EQUAL: emitDoubleCompare(ctx, e, DCMPL, IFGE); return true;
            case SMALLER: emitDoubleCompare(ctx, e, DCMPG, IFLT); return true;
            case SMALLER_OR_EQUAL: emitDoubleCompare(ctx, e, DCMPG, IFLE); return true;
            default: return false;
        }
    }

    // NaN-correct double comparison: DCMPL for >,>= and DCMPG for <,<= so NaN yields false (matching Java).
    private void emitDoubleCompare(GenContext ctx, BinaryOpExpression e, int dcmp, int ifInsn) {
        MethodVisitor mv = ctx.mv;
        compileDouble(e.getLeft(), ctx);
        compileDouble(e.getRight(), ctx);
        mv.visitInsn(dcmp);
        Label trueLabel = new Label();
        Label end = new Label();
        mv.visitJumpInsn(ifInsn, trueLabel);
        mv.visitInsn(DCONST_0);
        mv.visitJumpInsn(GOTO, end);
        mv.visitLabel(trueLabel);
        mv.visitInsn(DCONST_1);
        mv.visitLabel(end);
    }

    // Unboxed ternary: condition == 1.0 selects the then-arm (matching isOne, including the NaN case
    // via DCMPL). Both arms are emitted as primitive doubles, so no DoubleValue is allocated. The
    // var-cache snapshot/restore mirrors emitTernary so arm-local slots are only loaded where assigned.
    private void compileTernaryDouble(TernaryExpression e, GenContext ctx) {
        MethodVisitor mv = ctx.mv;
        compileDouble(e.getCondition(), ctx);
        mv.visitLdcInsn(1.0);
        mv.visitInsn(DCMPL); // 0 when equal; -1 for NaN
        Label elseBranch = new Label();
        Label end = new Label();
        mv.visitJumpInsn(IFNE, elseBranch); // != 1.0 -> else
        Map<String, Integer> beforeArms = ctx.snapshotVarCache();

        if (e.getThenExpr() != null) {
            compileDouble(e.getThenExpr(), ctx);
        } else {
            compileDouble(e.getCondition(), ctx); // interpreter re-evaluates the condition
        }
        mv.visitJumpInsn(GOTO, end);

        mv.visitLabel(elseBranch);
        ctx.restoreVarCache(beforeArms);
        if (e.getElseExpr() != null) {
            compileDouble(e.getElseExpr(), ctx);
        } else {
            mv.visitInsn(DCONST_0);
        }
        mv.visitLabel(end);
        ctx.restoreVarCache(beforeArms);
    }

    // new DoubleValue(<primitive double from compileDouble>), typed as MoValue.
    private void boxDouble(Expression e, GenContext ctx) {
        MethodVisitor mv = ctx.mv;
        mv.visitTypeInsn(NEW, "org/cloudburstmc/mojava/runtime/value/DoubleValue");
        mv.visitInsn(DUP);
        compileDouble(e, ctx);
        mv.visitMethodInsn(INVOKESPECIAL, "org/cloudburstmc/mojava/runtime/value/DoubleValue", "<init>", "(D)V", false);
        mv.visitTypeInsn(CHECKCAST, MOVALUE);
    }

    // && / || preserving the interpreter's short-circuit (Java && / ||) over "not equal to zero".
    private void emitShortCircuit(BinaryOpExpression e, GenContext ctx, boolean and) {
        MethodVisitor mv = ctx.mv;
        Label shortCircuit = new Label();
        Label rightFalse = new Label();
        Label end = new Label();

        compile(e.getLeft(), ctx);
        invokeSupport(ctx, "notZero", "(" + D_MOVALUE + ")Z");
        Map<String, Integer> beforeRight = ctx.snapshotVarCache(); // left always runs; right is conditional
        // && : left == 0 short-circuits to false. || : left != 0 short-circuits to true.
        mv.visitJumpInsn(and ? IFEQ : IFNE, shortCircuit);

        compile(e.getRight(), ctx);
        invokeSupport(ctx, "notZero", "(" + D_MOVALUE + ")Z");
        mv.visitJumpInsn(IFEQ, rightFalse);
        pushBool(ctx, true); // right is non-zero -> result true for both && and ||
        mv.visitJumpInsn(GOTO, end);

        mv.visitLabel(rightFalse);
        pushBool(ctx, false);
        mv.visitJumpInsn(GOTO, end);

        mv.visitLabel(shortCircuit);
        pushBool(ctx, !and); // && short-circuits to false, || short-circuits to true
        mv.visitLabel(end);
        ctx.restoreVarCache(beforeRight); // drop right-operand entries at the merge
    }

    private void emitCoalesce(BinaryOpExpression e, GenContext ctx) {
        MethodVisitor mv = ctx.mv;
        Expression left = e.getLeft();
        if (left instanceof NameExpression) {
            mv.visitVarInsn(ALOAD, ctx.envSlot);
            emitLoadConstant(ctx, nameConst((NameExpression) left), LIST);
            invokeSupport(ctx, "getRaw", "(L" + MOENV + ";" + D_LIST + ")" + D_MOVALUE);
            ctx.clearVarCache(); // getRaw may resolve through a query getter with side effects
        } else {
            compile(left, ctx);
        }
        Map<String, Integer> beforeRight = ctx.snapshotVarCache(); // left always runs; right is conditional
        mv.visitInsn(DUP);
        Label has = new Label();
        mv.visitJumpInsn(IFNONNULL, has);
        mv.visitInsn(POP);
        compile(e.getRight(), ctx);
        mv.visitLabel(has);
        ctx.restoreVarCache(beforeRight); // drop right-operand entries at the merge
    }

    private void emitArrow(BinaryOpExpression e, GenContext ctx) {
        MethodVisitor mv = ctx.mv;
        compile(e.getLeft(), ctx);
        Map<String, Integer> beforeArm = ctx.snapshotVarCache(); // left always runs
        mv.visitInsn(DUP);
        mv.visitTypeInsn(INSTANCEOF, MOENV);
        Label notEnv = new Label();
        Label end = new Label();
        mv.visitJumpInsn(IFEQ, notEnv);

        mv.visitTypeInsn(CHECKCAST, MOENV);
        int newEnv = ctx.alloc();
        mv.visitVarInsn(ASTORE, newEnv);
        int savedEnv = ctx.envSlot;
        ctx.envSlot = newEnv;
        ctx.clearVarCache(); // the body resolves variables against a different env
        compile(e.getRight(), ctx);
        ctx.envSlot = savedEnv;
        mv.visitJumpInsn(GOTO, end);

        mv.visitLabel(notEnv);
        mv.visitInsn(POP); // discard the non-env left value
        pushZero(ctx);
        mv.visitLabel(end);
        ctx.restoreVarCache(beforeArm); // env restored; drop body entries, old-env slots valid again
    }

    private void emitTernary(TernaryExpression e, GenContext ctx) {
        MethodVisitor mv = ctx.mv;
        compile(e.getCondition(), ctx);
        invokeSupport(ctx, "isOne", "(" + D_MOVALUE + ")Z");
        Label elseBranch = new Label();
        Label end = new Label();
        mv.visitJumpInsn(IFEQ, elseBranch);
        Map<String, Integer> beforeArms = ctx.snapshotVarCache(); // condition reads dominate both arms

        if (e.getThenExpr() != null) {
            compile(e.getThenExpr(), ctx);
        } else {
            compile(e.getCondition(), ctx); // interpreter re-evaluates the condition
        }
        mv.visitJumpInsn(GOTO, end);

        mv.visitLabel(elseBranch);
        ctx.restoreVarCache(beforeArms); // then-arm-only entries aren't assigned on this path
        if (e.getElseExpr() != null) {
            compile(e.getElseExpr(), ctx);
        } else {
            pushZero(ctx);
        }
        mv.visitLabel(end);
        ctx.restoreVarCache(beforeArms); // drop arm-local entries at the merge
    }

    private void emitStatement(StatementExpression e, GenContext ctx) {
        MethodVisitor mv = ctx.mv;
        if (ctx.fastPath) {
            // No control flow possible: run each child for its side effects; a block with no return
            // evaluates to 0 (same as the polling path when nothing is ever set).
            for (Expression child : e.getExpressions()) {
                compile(child, ctx);
                mv.visitInsn(POP);
            }
            pushZero(ctx);
            return;
        }
        Label end = new Label();
        for (Expression child : e.getExpressions()) {
            compile(child, ctx);
            mv.visitInsn(POP);
            // Stop on any of return/break/continue — one combined flag check instead of three polls.
            mv.visitVarInsn(ALOAD, ctx.scopeSlot);
            mv.visitMethodInsn(INVOKEVIRTUAL, MOSCOPE, "isInterrupted", "()Z", false);
            mv.visitJumpInsn(IFNE, end);
        }
        mv.visitLabel(end);
        emitReturnValueOrZero(ctx, ctx.scopeSlot);
    }

    private void emitLoop(LoopExpression e, GenContext ctx) {
        MethodVisitor mv = ctx.mv;
        compile(e.getCount(), ctx);
        mv.visitMethodInsn(INVOKEINTERFACE, MOVALUE, "asDouble", "()D", true);
        mv.visitInsn(D2I);
        emitIntConst(mv, 1024);
        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Math", "min", "(II)I", false);
        int loopVar = ctx.alloc();
        mv.visitVarInsn(ISTORE, loopVar);

        int subScope = ctx.alloc();
        newMoScope(ctx, subScope);

        Label top = new Label();
        Label end = new Label();
        Label hasReturn = new Label();
        Label done = new Label();

        mv.visitLabel(top);
        mv.visitVarInsn(ILOAD, loopVar);
        mv.visitJumpInsn(IFLE, end);

        int savedScope = ctx.scopeSlot;
        ctx.scopeSlot = subScope;
        ctx.clearVarCache(); // body re-runs per iteration; don't reuse pre-loop slots across the back-edge
        compile(e.getBody(), ctx);
        ctx.scopeSlot = savedScope;
        mv.visitInsn(POP);

        mv.visitIincInsn(loopVar, -1); // loop-- after the body, matching the interpreter

        mv.visitVarInsn(ALOAD, subScope);
        mv.visitMethodInsn(INVOKEVIRTUAL, MOSCOPE, "getReturnValue", "()" + D_MOVALUE, false);
        mv.visitInsn(DUP);
        mv.visitJumpInsn(IFNONNULL, hasReturn);
        mv.visitInsn(POP);
        mv.visitVarInsn(ALOAD, subScope);
        mv.visitMethodInsn(INVOKEVIRTUAL, MOSCOPE, "isBreak", "()Z", false);
        mv.visitJumpInsn(IFNE, end);
        mv.visitJumpInsn(GOTO, top);

        mv.visitLabel(end);
        pushZero(ctx);
        mv.visitJumpInsn(GOTO, done);
        mv.visitLabel(hasReturn); // sub-scope return value is the loop's result (not propagated outward)
        mv.visitLabel(done);
        ctx.clearVarCache(); // the body may have written variables an unknown number of times
    }

    private void emitForEach(ForEachExpression e, GenContext ctx) {
        MethodVisitor mv = ctx.mv;
        compile(e.getArray(), ctx);
        mv.visitInsn(DUP);
        mv.visitTypeInsn(INSTANCEOF, VARSTRUCT);
        Label notStruct = new Label();
        Label zero = new Label();
        Label done = new Label();
        Label hasReturn = new Label();
        mv.visitJumpInsn(IFEQ, notStruct);

        // iterator over a defensive copy of the struct's values
        mv.visitTypeInsn(CHECKCAST, VARSTRUCT);
        mv.visitMethodInsn(INVOKEVIRTUAL, VARSTRUCT, "getMap", "()Ljava/util/Map;", false);
        mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "values", "()Ljava/util/Collection;", true);
        mv.visitTypeInsn(NEW, "java/util/ArrayList");
        mv.visitInsn(DUP_X1);
        mv.visitInsn(SWAP);
        mv.visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList", "<init>", "(Ljava/util/Collection;)V", false);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "iterator", "()Ljava/util/Iterator;", false);
        int iter = ctx.alloc();
        mv.visitVarInsn(ASTORE, iter);

        int scope2 = ctx.alloc();
        newMoScope(ctx, scope2);
        int valueSlot = ctx.alloc();

        Label top = new Label();
        mv.visitLabel(top);
        mv.visitVarInsn(ALOAD, iter);
        mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z", true);
        mv.visitJumpInsn(IFEQ, zero);

        mv.visitVarInsn(ALOAD, iter);
        mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;", true);
        mv.visitTypeInsn(CHECKCAST, MOVALUE);
        mv.visitVarInsn(ASTORE, valueSlot);

        int savedScope = ctx.scopeSlot;
        ctx.scopeSlot = scope2;
        ctx.clearVarCache(); // loop variable + body re-run per iteration; start each fresh
        emitAssignTo(e.getVariable(), valueSlot, ctx);
        compile(e.getBody(), ctx);
        ctx.scopeSlot = savedScope;
        mv.visitInsn(POP);

        mv.visitVarInsn(ALOAD, scope2);
        mv.visitMethodInsn(INVOKEVIRTUAL, MOSCOPE, "getReturnValue", "()" + D_MOVALUE, false);
        mv.visitInsn(DUP);
        mv.visitJumpInsn(IFNONNULL, hasReturn);
        mv.visitInsn(POP);
        mv.visitVarInsn(ALOAD, scope2);
        mv.visitMethodInsn(INVOKEVIRTUAL, MOSCOPE, "isBreak", "()Z", false);
        mv.visitJumpInsn(IFNE, zero);
        mv.visitJumpInsn(GOTO, top);

        mv.visitLabel(notStruct);
        mv.visitInsn(POP); // discard the non-struct array value
        mv.visitLabel(zero);
        pushZero(ctx);
        mv.visitJumpInsn(GOTO, done);
        mv.visitLabel(hasReturn); // for-each return value is the result
        mv.visitLabel(done);
        ctx.clearVarCache(); // the body may have written variables an unknown number of times
    }

    private void emitReturn(ReturnExpression e, GenContext ctx) {
        MethodVisitor mv = ctx.mv;
        compile(e.getExpression(), ctx);
        mv.visitInsn(DUP);
        mv.visitVarInsn(ALOAD, ctx.scopeSlot);
        mv.visitInsn(SWAP);
        mv.visitMethodInsn(INVOKEVIRTUAL, MOSCOPE, "setReturnValue", "(" + D_MOVALUE + ")V", false);
    }

    private void emitFlagAndZero(GenContext ctx, String setter) {
        MethodVisitor mv = ctx.mv;
        mv.visitVarInsn(ALOAD, ctx.scopeSlot);
        mv.visitInsn(ICONST_1);
        mv.visitMethodInsn(INVOKEVIRTUAL, MOSCOPE, setter, "(Z)V", false);
        pushZero(ctx);
    }

    // ---- small emitters ----

    private void emitReturnValueOrZero(GenContext ctx, int scopeSlot) {
        MethodVisitor mv = ctx.mv;
        mv.visitVarInsn(ALOAD, scopeSlot);
        mv.visitMethodInsn(INVOKEVIRTUAL, MOSCOPE, "getReturnValue", "()" + D_MOVALUE, false);
        mv.visitInsn(DUP);
        Label has = new Label();
        mv.visitJumpInsn(IFNONNULL, has);
        mv.visitInsn(POP);
        pushZero(ctx);
        mv.visitLabel(has);
    }

    private void newMoScope(GenContext ctx, int slot) {
        MethodVisitor mv = ctx.mv;
        mv.visitTypeInsn(NEW, MOSCOPE);
        mv.visitInsn(DUP);
        mv.visitMethodInsn(INVOKESPECIAL, MOSCOPE, "<init>", "()V", false);
        mv.visitVarInsn(ASTORE, slot);
    }

    private void invokeSupport(GenContext ctx, String name, String descriptor) {
        ctx.mv.visitMethodInsn(INVOKESTATIC, SUPPORT, name, descriptor, false);
    }

    private void pushZero(GenContext ctx) {
        ctx.mv.visitFieldInsn(GETSTATIC, "org/cloudburstmc/mojava/runtime/value/DoubleValue", "ZERO",
                "Lorg/cloudburstmc/mojava/runtime/value/DoubleValue;");
        ctx.mv.visitTypeInsn(CHECKCAST, MOVALUE);
    }

    private void pushBool(GenContext ctx, boolean b) {
        String field = b ? "ONE" : "ZERO";
        ctx.mv.visitFieldInsn(GETSTATIC, "org/cloudburstmc/mojava/runtime/value/DoubleValue", field,
                "Lorg/cloudburstmc/mojava/runtime/value/DoubleValue;");
        ctx.mv.visitTypeInsn(CHECKCAST, MOVALUE);
    }

    // Loads constant #index from class data via condy, typed as the given internal name. classDataAt
    // casts to that type, so the result is already correctly typed — no field load, AALOAD or CHECKCAST.
    private void emitLoadConstant(GenContext ctx, int index, String typeInternal) {
        ctx.mv.visitLdcInsn(new ConstantDynamic("_", "L" + typeInternal + ";", CLASS_DATA_BSM, index));
    }

    private static String[] names(NameExpression e) {
        return e.getNames().toArray(new String[0]);
    }

    // An immutable List<String> name-path constant. Baked once at compile time so the runtime
    // resolution helpers don't wrap a String[] in Arrays.asList on every call.
    private int nameConst(NameExpression e) {
        return constant(List.copyOf(e.getNames()));
    }

    private static void emitIntConst(MethodVisitor mv, int value) {
        if (value >= -1 && value <= 5) {
            mv.visitInsn(ICONST_0 + value);
        } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            mv.visitIntInsn(BIPUSH, value);
        } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            mv.visitIntInsn(SIPUSH, value);
        } else {
            mv.visitLdcInsn(value);
        }
    }
}
