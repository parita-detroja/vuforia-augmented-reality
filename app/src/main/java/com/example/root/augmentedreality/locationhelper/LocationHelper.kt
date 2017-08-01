package com.example.root.augmentedreality.locationhelper

import android.location.Location

/**
 * Created by root on 5/7/17.
 * coverts WGS84 to ECEF and ECEF to ENU.
 */
class LocationHelper
{
    companion object
    {
        private val WGS84_A: Double = 6378137.0
        private val WGS84_E2: Double = 0.00669437999014

        fun WSG84toECEF(location: Location): FloatArray
        {
            val radLat: Double = location.latitude
            val radLon: Double = location.longitude

            val cos_lat: Float = Math.cos(radLat).toFloat()
            val sin_lat: Float = Math.sin(radLat).toFloat()
            val cos_lon: Float = Math.cos(radLon).toFloat()
            val sin_lon: Float = Math.sin(radLon).toFloat()

            val N: Float = (WGS84_A / Math.sqrt( 1.0 - WGS84_E2 * sin_lat * sin_lat)).toFloat()

            val x: Float = ((N + location.altitude) * cos_lat * cos_lon).toFloat()
            val y: Float = ((N + location.altitude) * cos_lat * sin_lon).toFloat()
            val z: Float = ((N * (1.0 - WGS84_E2) + location.altitude) * sin_lat).toFloat()

            return floatArrayOf(x,y,z)
        }

        fun ECEFtoENU(currentLocation: Location, ecefCurrentLocation: FloatArray, ecefPOI: FloatArray) : FloatArray
        {

            val radLat: Double = Math.toRadians(currentLocation.latitude)
            val radLon: Double = Math.toRadians(currentLocation.longitude)

            val cos_lat: Float = Math.cos(radLat).toFloat()
            val sin_lat: Float = Math.sin(radLat).toFloat()
            val cos_lon: Float = Math.cos(radLon).toFloat()
            val sin_lon: Float = Math.sin(radLon).toFloat()

            val dx: Float = ecefCurrentLocation[0] - ecefPOI[0]
            val dy: Float = ecefCurrentLocation[1] - ecefPOI[1]
            val dz: Float = ecefCurrentLocation[2] - ecefPOI[2]

            val east: Float = -sin_lon * dx + cos_lon * dy
            val north: Float = -sin_lat * cos_lon * dx - sin_lat * sin_lon * dy + cos_lat * dz
            val up: Float = cos_lat * cos_lon * dx + cos_lat * sin_lon * dy + sin_lat * dz

            return floatArrayOf(east, north, up, 1f)

        }
    }

}