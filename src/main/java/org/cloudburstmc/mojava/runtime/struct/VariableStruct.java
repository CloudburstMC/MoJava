package org.cloudburstmc.mojava.runtime.struct;

import org.cloudburstmc.mojava.runtime.MoParams;
import org.cloudburstmc.mojava.runtime.value.DoubleValue;
import org.cloudburstmc.mojava.runtime.value.MoValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Two-segment variable/temp values ({@code v.x}, {@code t.y}) are the hot read path. The compiler
 * resolves each name to a stable {@code int} slot (via {@link #slotFor}) at compile time and reads it
 * with an array load instead of a {@code HashMap.get}.
 *
 * <p>The {@code map} stays the canonical store, so {@code getMap()}, {@code for_each}, dynamic keys and
 * nested structs behave exactly as before. {@code slots} is a write-through fast cache: every mutation
 * funnels through {@link #slotSet}, so a slot read never goes stale. (External callers that mutate the
 * map directly via {@code getMap().put(...)} would bypass it — use {@link #setDirectly} instead.)
 */
@Getter
@RequiredArgsConstructor
public class VariableStruct implements MoStruct {

    // Process-global name -> slot table — monotonic and unbounded in principle, but bounded in
    // practice by the distinct variable names ever compiled (tens-to-hundreds); per-struct arrays grow
    // only to the highest slot actually written to that struct.
    private static final ConcurrentHashMap<String, Integer> SLOT_INDEX = new ConcurrentHashMap<>();
    private static final AtomicInteger NEXT_SLOT = new AtomicInteger();
    private static final MoValue[] EMPTY = new MoValue[0];

    /** Stable slot for a variable name, assigned once and shared across all structs/runtimes. */
    public static int slotFor(String name) {
        Integer index = SLOT_INDEX.get(name); // fast path: avoid the computeIfAbsent lambda once interned
        return index != null ? index : SLOT_INDEX.computeIfAbsent(name, k -> NEXT_SLOT.getAndIncrement());
    }

    protected final Map<String, MoValue> map;

    private MoValue[] slots = EMPTY;

    public VariableStruct() {
        this.map = new HashMap<>();
    }

    /** Fast read of a compile-time slot; null when unset (caller maps that to 0.0). */
    public MoValue slotGet(int slot) {
        MoValue[] s = this.slots;
        return slot < s.length ? s[slot] : null;
    }

    /** Write-through: updates both the slot cache and the canonical map. */
    public void slotSet(int slot, String name, MoValue value) {
        MoValue[] s = this.slots;
        if (slot >= s.length) {
            s = Arrays.copyOf(s, Math.max(slot + 1, s.length == 0 ? 8 : s.length * 2));
            this.slots = s;
        }
        s[slot] = value;
        this.map.put(name, value);
    }

    @Override
    public void set(Iterator<String> names, MoValue value) {
        String main = names.next();

        if (names.hasNext() && main != null) {
            Object struct = map.get(main);

            if (!(struct instanceof MoStruct)) {
                struct = new VariableStruct();
            }

            ((MoStruct) struct).set(names, value);

            slotSet(slotFor(main), main, (MoStruct) struct);
        } else {
            slotSet(slotFor(main), main, value);
        }
    }

    public void setDirectly(String name, MoValue value) {
        slotSet(slotFor(name), name, value);
    }

    @Override
    public MoValue get(Iterator<String> names, MoParams params) {
        String main = names.next();

        if (names.hasNext() && main != null) {
            Object struct = map.get(main);

            if (struct instanceof MoStruct) {
                return ((MoStruct) struct).get(names, params);
            }
        }

        return map.getOrDefault(main, new DoubleValue(0.0));
    }

    @Override
    public MoValue getRaw(Iterator<String> names, MoParams params) {
        String main = names.next();

        if (names.hasNext() && main != null) {
            Object struct = map.get(main);

            if (struct instanceof MoStruct) {
                return ((MoStruct) struct).getRaw(names, params);
            }

            return null;
        }

        return map.get(main); // null when absent
    }

    @Override
    public void clear() {
        map.clear();
        Arrays.fill(slots, null); // keep the array (temp is cleared every eval) — just drop the values
    }
}
