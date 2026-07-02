package com.worldscape.terrain

data class Combinator(
    @JvmField val type: String,
    @JvmField val terms: List<String>? = null,
    @JvmField val a: String? = null,
    @JvmField val b: String? = null,
    @JvmField val weightA: Double = 0.0,
    @JvmField val source: String? = null,
    @JvmField val factor: Double = 0.0
) {
    override fun toString(): String = "Combinator{type='$type'}"
}