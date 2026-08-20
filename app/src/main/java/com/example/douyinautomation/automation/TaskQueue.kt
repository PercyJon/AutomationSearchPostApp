package com.example.douyinautomation.automation

/**
 * Small FIFO queue shared by task runners that need to execute saved work one item at a time.
 * The queue deliberately owns no Android state, making the sequencing behavior easy to test and
 * reusable for future automation modules.
 */
class SequentialTaskQueue<T> {
    private val pending = ArrayDeque<T>()

    val isEmpty: Boolean
        get() = pending.isEmpty()

    val size: Int
        get() = pending.size

    fun replace(items: Iterable<T>) {
        pending.clear()
        pending.addAll(items)
    }

    fun addAll(items: Iterable<T>) {
        pending.addAll(items)
    }

    fun poll(): T? = pending.removeFirstOrNull()

    fun peek(): T? = pending.firstOrNull()

    fun clear() {
        pending.clear()
    }

    fun asList(): List<T> = pending.toList()
}
