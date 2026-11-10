package android.content

/** The phone's Context, where its UI files only pass one through to a no-op on iOS. */
open class Context

object IosContext : Context()
