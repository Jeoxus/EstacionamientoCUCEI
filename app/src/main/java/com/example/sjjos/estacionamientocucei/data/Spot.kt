package com.example.sjjos.estacionamientocucei.data

import java.io.Serializable

data class Spot(
        val lat: Double = 0.0,
        val lng: Double = 0.0,
        val type: String?
) : Serializable