package com.worldscape.terrain

data class Climate(
    @JvmField val temperature: Double,
    @JvmField val humidity: Double,
    @JvmField val seasonality: Double,
    @JvmField val continentality: Double
) {
    override fun toString(): String =
        "Climate{t=$temperature, h=$humidity, s=$seasonality, c=$continentality}"
}