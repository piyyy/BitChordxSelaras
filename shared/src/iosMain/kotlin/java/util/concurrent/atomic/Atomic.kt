@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package java.util.concurrent.atomic

import com.music.bitchord.ioscompat.jvmSynchronized

class AtomicReference<V>(initial: V) {
    private var value: V = initial
    constructor() : this(null as V)

    fun get(): V = jvmSynchronized { value }
    fun set(v: V) = jvmSynchronized { value = v }
    fun lazySet(v: V) = set(v)
    fun getAndSet(v: V): V = jvmSynchronized { val old = value; value = v; old }
    fun compareAndSet(expect: V, update: V): Boolean = jvmSynchronized {
        if (value === expect || value == expect) { value = update; true } else false
    }
    fun updateAndGet(f: (V) -> V): V = jvmSynchronized { value = f(value); value }
    fun getAndUpdate(f: (V) -> V): V = jvmSynchronized { val old = value; value = f(value); old }
    fun accumulateAndGet(x: V, f: (V, V) -> V): V = jvmSynchronized { value = f(value, x); value }
    override fun toString() = get().toString()
}

class AtomicLong(initial: Long = 0L) {
    private var value = initial
    fun get(): Long = jvmSynchronized { value }
    fun set(v: Long) = jvmSynchronized { value = v }
    fun getAndSet(v: Long): Long = jvmSynchronized { val o = value; value = v; o }
    fun incrementAndGet(): Long = addAndGet(1)
    fun decrementAndGet(): Long = addAndGet(-1)
    fun getAndIncrement(): Long = getAndAdd(1)
    fun getAndDecrement(): Long = getAndAdd(-1)
    fun addAndGet(d: Long): Long = jvmSynchronized { value += d; value }
    fun getAndAdd(d: Long): Long = jvmSynchronized { val o = value; value += d; o }
    fun compareAndSet(e: Long, u: Long): Boolean = jvmSynchronized { if (value == e) { value = u; true } else false }
    fun updateAndGet(f: (Long) -> Long): Long = jvmSynchronized { value = f(value); value }
    fun accumulateAndGet(x: Long, f: (Long, Long) -> Long): Long = jvmSynchronized { value = f(value, x); value }
    override fun toString() = get().toString()
}

class AtomicInteger(initial: Int = 0) {
    private var value = initial
    fun get(): Int = jvmSynchronized { value }
    fun set(v: Int) = jvmSynchronized { value = v }
    fun getAndSet(v: Int): Int = jvmSynchronized { val o = value; value = v; o }
    fun incrementAndGet(): Int = addAndGet(1)
    fun decrementAndGet(): Int = addAndGet(-1)
    fun getAndIncrement(): Int = getAndAdd(1)
    fun getAndDecrement(): Int = getAndAdd(-1)
    fun addAndGet(d: Int): Int = jvmSynchronized { value += d; value }
    fun getAndAdd(d: Int): Int = jvmSynchronized { val o = value; value += d; o }
    fun compareAndSet(e: Int, u: Int): Boolean = jvmSynchronized { if (value == e) { value = u; true } else false }
    fun updateAndGet(f: (Int) -> Int): Int = jvmSynchronized { value = f(value); value }
    override fun toString() = get().toString()
}

class AtomicBoolean(initial: Boolean = false) {
    private var value = initial
    fun get(): Boolean = jvmSynchronized { value }
    fun set(v: Boolean) = jvmSynchronized { value = v }
    fun getAndSet(v: Boolean): Boolean = jvmSynchronized { val o = value; value = v; o }
    fun compareAndSet(e: Boolean, u: Boolean): Boolean = jvmSynchronized { if (value == e) { value = u; true } else false }
    override fun toString() = get().toString()
}
