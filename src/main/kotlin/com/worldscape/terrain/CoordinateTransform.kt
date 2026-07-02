package com.worldscape.terrain

data class CoordinateTransform(
    @JvmField val type: String,
    @JvmField val params: Map<String, Any>
) {
    override fun toString(): String =
        "CoordinateTransform{type='$type', params=$params}"
}