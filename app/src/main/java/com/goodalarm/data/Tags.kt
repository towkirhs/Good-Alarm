package com.goodalarm.data

/** Tags: a few built-in ones plus any you create while editing an item. */
object Tags {
    val defaults = listOf("Home", "Work", "Personal")

    /** Built-in tags first, then your own, without duplicates. */
    fun all(items: List<Item>): List<String> =
        (defaults + items.map { it.tag }.filter { it.isNotBlank() }.sorted()).distinct()

    /** null filter = show everything. */
    fun matches(item: Item, filter: String?): Boolean = filter == null || item.tag == filter
}
