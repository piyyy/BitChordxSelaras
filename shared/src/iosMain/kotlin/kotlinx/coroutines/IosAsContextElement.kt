package kotlinx.coroutines

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** JVM-only in kotlinx.coroutines; on iOS the value is just set (see JThreadLocal) and the element is inert. */
fun <T> com.music.bitchord.ioscompat.JThreadLocal<T>.asContextElement(value: T = get()): CoroutineContext.Element {
    set(value)
    return InertThreadLocalElement
}

private object InertThreadLocalElement : AbstractCoroutineContextElement(Key) {
    object Key : CoroutineContext.Key<InertThreadLocalElement>
}
