package java.util.concurrent

import com.music.bitchord.ioscompat.jvmSynchronized

/** A HashMap behind the process-wide lock, with the ConcurrentMap verbs BitChord uses. */
class ConcurrentHashMap<K, V>(initialCapacity: Int = 16) : MutableMap<K, V> {
    private val map = LinkedHashMap<K, V>(initialCapacity)

    override val size: Int get() = jvmSynchronized { map.size }
    override fun isEmpty() = jvmSynchronized { map.isEmpty() }
    override fun containsKey(key: K) = jvmSynchronized { map.containsKey(key) }
    override fun containsValue(value: V) = jvmSynchronized { map.containsValue(value) }
    override fun get(key: K): V? = jvmSynchronized { map[key] }
    override fun put(key: K, value: V): V? = jvmSynchronized { map.put(key, value) }
    override fun remove(key: K): V? = jvmSynchronized { map.remove(key) }
    override fun putAll(from: Map<out K, V>) = jvmSynchronized { map.putAll(from) }
    override fun clear() = jvmSynchronized { map.clear() }
    override val keys: MutableSet<K> get() = jvmSynchronized { LinkedHashSet(map.keys) }
    override val values: MutableCollection<V> get() = jvmSynchronized { ArrayList(map.values) }
    override val entries: MutableSet<MutableMap.MutableEntry<K, V>> get() = jvmSynchronized { LinkedHashMap(map).entries }

    fun putIfAbsent(key: K, value: V): V? = jvmSynchronized { map[key] ?: run { map[key] = value; null } }
    fun computeIfAbsent(key: K, f: (K) -> V): V = jvmSynchronized { map.getOrPut(key) { f(key) } }
    fun compute(key: K, f: (K, V?) -> V?): V? = jvmSynchronized {
        val v = f(key, map[key]); if (v == null) map.remove(key) else map[key] = v; v
    }
    fun merge(key: K, value: V, f: (V, V) -> V?): V? = jvmSynchronized {
        val old = map[key]; val v = if (old == null) value else f(old, value)
        if (v == null) map.remove(key) else map[key] = v; v
    }
    fun remove(key: K, value: V): Boolean = jvmSynchronized { if (map[key] == value) { map.remove(key); true } else false }
    fun replace(key: K, value: V): V? = jvmSynchronized { if (map.containsKey(key)) map.put(key, value) else null }
    fun getOrDefault(key: K, default: V): V = jvmSynchronized { map[key] ?: default }
    override fun toString() = jvmSynchronized { map.toString() }

    companion object {
        fun <T> newKeySet(): MutableSet<T> = SynchronizedSet()
    }
}

class SynchronizedSet<T> : AbstractMutableSet<T>() {
    private val set = LinkedHashSet<T>()
    override val size: Int get() = jvmSynchronized { set.size }
    override fun add(element: T): Boolean = jvmSynchronized { set.add(element) }
    override fun remove(element: T): Boolean = jvmSynchronized { set.remove(element) }
    override fun contains(element: T): Boolean = jvmSynchronized { set.contains(element) }
    override fun iterator(): MutableIterator<T> = jvmSynchronized { LinkedHashSet(set) }.iterator()
}

class CopyOnWriteArrayList<T> : MutableList<T> by ArrayList()

enum class TimeUnit(private val nanos: Long) {
    NANOSECONDS(1), MICROSECONDS(1_000), MILLISECONDS(1_000_000), SECONDS(1_000_000_000),
    MINUTES(60_000_000_000), HOURS(3_600_000_000_000), DAYS(86_400_000_000_000);

    fun toNanos(d: Long) = d * nanos
    fun toMicros(d: Long) = d * nanos / 1_000
    fun toMillis(d: Long) = d * nanos / 1_000_000
    fun toSeconds(d: Long) = d * nanos / 1_000_000_000
    fun toMinutes(d: Long) = d * nanos / 60_000_000_000
    fun toHours(d: Long) = d * nanos / 3_600_000_000_000
    fun toDays(d: Long) = d * nanos / 86_400_000_000_000
}

class CancellationException(message: String? = null) : kotlin.coroutines.cancellation.CancellationException(message)
class TimeoutException(message: String? = null) : Exception(message)
class ExecutionException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)
