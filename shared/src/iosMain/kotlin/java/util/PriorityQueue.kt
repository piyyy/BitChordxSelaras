package java.util

/** A binary heap with java.util.PriorityQueue's verbs. */
class PriorityQueue<E>(initialCapacity: Int = 11, private val comparator: Comparator<in E>) : AbstractMutableCollection<E>() {
    constructor(comparator: Comparator<in E>) : this(11, comparator)

    private val heap = ArrayList<E>(initialCapacity)
    override val size: Int get() = heap.size

    fun offer(e: E): Boolean {
        heap.add(e)
        var i = heap.lastIndex
        while (i > 0) {
            val parent = (i - 1) / 2
            if (comparator.compare(heap[i], heap[parent]) >= 0) break
            heap[i] = heap[parent].also { heap[parent] = heap[i] }
            i = parent
        }
        return true
    }

    override fun add(element: E) = offer(element)
    fun peek(): E? = heap.firstOrNull()

    fun poll(): E? {
        if (heap.isEmpty()) return null
        val top = heap[0]
        val last = heap.removeAt(heap.lastIndex)
        if (heap.isNotEmpty()) {
            heap[0] = last
            var i = 0
            while (true) {
                val l = 2 * i + 1
                val r = l + 1
                var m = i
                if (l < heap.size && comparator.compare(heap[l], heap[m]) < 0) m = l
                if (r < heap.size && comparator.compare(heap[r], heap[m]) < 0) m = r
                if (m == i) break
                heap[i] = heap[m].also { heap[m] = heap[i] }
                i = m
            }
        }
        return top
    }

    fun remove(): E = poll() ?: throw NoSuchElementException()
    override fun clear() = heap.clear()
    override fun iterator(): MutableIterator<E> = heap.iterator()
}
