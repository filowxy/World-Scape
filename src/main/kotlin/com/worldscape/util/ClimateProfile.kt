package com.worldscape.util

import com.worldscape.terrain.WorldScapeConstants

data class ClimateProfile @JvmOverloads constructor(
    val temperature: Double,
    val humidity: Double,
    val seasonality: Double = WorldScapeConstants.DEFAULT_SEASONALITY,
    val continentality: Double = WorldScapeConstants.DEFAULT_CONTINENTALITY
) {
    fun distanceTo(other: ClimateProfile): Double =
        ClimateUtils.calculateClimateDistance(
            temperature, humidity, seasonality, continentality,
            other.temperature, other.humidity, other.seasonality, other.continentality
        )

    override fun toString(): String =
        "ClimateProfile[temp=%.2f, humid=%.2f, season=%.2f, cont=%.2f]"
            .format(temperature, humidity, seasonality, continentality)
}