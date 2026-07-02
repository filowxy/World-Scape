package com.worldscape.terrain

data class NoisePrimitive(
    @JvmField val id: String?,
    @JvmField val name: String,
    @JvmField val params: Map<String, Any>,
    @JvmField val amplitude: Double
) {
    override fun toString(): String =
        "NoisePrimitive{id='$id', name='$name', amplitude=$amplitude}"
}