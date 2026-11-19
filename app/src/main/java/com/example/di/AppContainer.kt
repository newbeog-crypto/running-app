package com.example.di

import android.content.Context
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.remote.CloudFitnessRepository
import com.example.data.repository.ActivityRepository
import com.example.sensors.LocationTrackerManager
import com.example.sensors.StepCounterManager
import com.example.service.TrackingSessionController
import com.google.firebase.firestore.FirebaseFirestore

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database = AppDatabase.getInstance(appContext)
    private val preferences = UserPreferencesRepository(appContext)
    private val databaseId = appContext.getString(R.string.firestore_database_id)
    private val firestore = FirebaseFirestore.getInstance(databaseId)

    val userPreferences = preferences
    val cloudRepository = CloudFitnessRepository(db = firestore)

    val activityRepository = ActivityRepository(
        dailyStepsDao = database.dailyStepsDao(),
        workoutSessionDao = database.workoutSessionDao(),
        preferences = preferences
    )

    val stepCounterManager = StepCounterManager(appContext)
    val locationTrackerManager = LocationTrackerManager(appContext)

    val trackingController = TrackingSessionController(
        appContext = appContext,
        activityRepository = activityRepository,
        cloudRepository = cloudRepository,
        preferences = preferences,
        stepCounterManager = stepCounterManager,
        locationTrackerManager = locationTrackerManager
    )
}
