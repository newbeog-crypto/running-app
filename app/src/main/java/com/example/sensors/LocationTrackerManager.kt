package com.example.sensors

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.example.data.model.RoutePoint
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

enum class GpsSignalState {
    IDLE,
    SEARCHING,
    ACTIVE_FIX,
    PERMISSION_REQUIRED
}

data class LocationFix(
    val point: RoutePoint,
    val speedMetersPerSec: Float,
    val accuracyMeters: Float
)

class LocationTrackerManager(context: Context) {

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context.applicationContext)

    @SuppressLint("MissingPermission")
    fun observeLocationFixes(): Flow<LocationFix> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2_000L)
            .setMinUpdateIntervalMillis(1_000L)
            .setMinUpdateDistanceMeters(2.5f)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (location in result.locations) {
                    if (location.hasAccuracy() && location.accuracy > 55f) continue
                    trySend(
                        LocationFix(
                            point = RoutePoint(
                                latitude = location.latitude,
                                longitude = location.longitude,
                                timestampMillis = System.currentTimeMillis()
                            ),
                            speedMetersPerSec = if (location.hasSpeed()) location.speed else 0f,
                            accuracyMeters = if (location.hasAccuracy()) location.accuracy else 10f
                        )
                    )
                }
            }
        }

        fusedClient.lastLocation.addOnSuccessListener { lastLoc ->
            if (lastLoc != null && (!lastLoc.hasAccuracy() || lastLoc.accuracy <= 60f)) {
                trySend(
                    LocationFix(
                        point = RoutePoint(
                            latitude = lastLoc.latitude,
                            longitude = lastLoc.longitude,
                            timestampMillis = System.currentTimeMillis()
                        ),
                        speedMetersPerSec = if (lastLoc.hasSpeed()) lastLoc.speed else 0f,
                        accuracyMeters = if (lastLoc.hasAccuracy()) lastLoc.accuracy else 15f
                    )
                )
            }
        }

        fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())

        awaitClose {
            fusedClient.removeLocationUpdates(callback)
        }
    }
}
