package com.example.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.WorkoutType
import com.example.data.remote.CloudDailySteps
import com.example.data.remote.CloudFitnessRepository
import com.example.data.remote.CloudWorkoutSession
import com.example.data.remote.UiState
import com.example.data.repository.ActivityRepository
import com.example.util.MetricUtils
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class StatsPeriod(val label: String) {
    WEEK("Weekly"),
    MONTH("Monthly"),
    YEAR("Yearly")
}

data class PeriodChartBucket(
    val label: String,
    val distanceMeters: Double,
    val workoutsCount: Int
)

data class StatsDashboardData(
    val period: StatsPeriod = StatsPeriod.WEEK,
    val periodOffset: Int = 0,
    val periodTitle: String = "",
    val totalDistanceMeters: Double = 0.0,
    val totalMovingTimeMillis: Long = 0L,
    val averagePaceSecPerKm: Int = 0,
    val bestPaceSecPerKm: Int = 0,
    val averageSpeedKmH: String = "0.0",
    val totalWorkouts: Int = 0,
    val runsCount: Int = 0,
    val walksCount: Int = 0,
    val totalCalories: Int = 0,
    val totalSteps: Long = 0L,
    val chartBuckets: List<PeriodChartBucket> = emptyList()
)

class StatsViewModel(
    cloudRepository: CloudFitnessRepository,
    activityRepository: ActivityRepository,
    currentUserId: String?
) : ViewModel() {

    private val _selectedPeriod = MutableStateFlow(StatsPeriod.WEEK)
    private val _periodOffset = MutableStateFlow(0)

    private val workoutsFlow: Flow<List<CloudWorkoutSession>> = if (currentUserId != null) {
        cloudRepository.observeCloudWorkouts(currentUserId)
    } else {
        activityRepository.observeAllWorkouts().map { list ->
            list.map { entity ->
                CloudWorkoutSession(
                    id = entity.id.toString(),
                    userId = "guest",
                    type = entity.type,
                    startedAtMillis = entity.startedAtMillis,
                    endedAtMillis = entity.endedAtMillis,
                    movingTimeMillis = entity.movingTimeMillis,
                    distanceMeters = entity.distanceMeters,
                    averagePaceSecPerKm = entity.averagePaceSecPerKm.toLong(),
                    caloriesBurned = entity.caloriesBurned.toLong()
                )
            }
        }
    }

    private val dailyStepsFlow: Flow<List<CloudDailySteps>> = if (currentUserId != null) {
        cloudRepository.observeCloudDailySteps(currentUserId)
    } else {
        activityRepository.observeRecentDailySteps(366).map { list ->
            list.map { entity ->
                CloudDailySteps(
                    id = entity.dateKey,
                    userId = "guest",
                    dateKey = entity.dateKey,
                    steps = entity.steps.toLong(),
                    goalSteps = entity.goalSteps.toLong()
                )
            }
        }
    }

    val uiState: StateFlow<UiState<StatsDashboardData>> = combine(
        workoutsFlow,
        dailyStepsFlow,
        _selectedPeriod,
        _periodOffset
    ) { workouts, dailySteps, period, offset ->
        computeStats(workouts, dailySteps, period, offset)
    }
        .map<StatsDashboardData, UiState<StatsDashboardData>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            emit(UiState.Error(rootError.message ?: "Failed to load analytics"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = UiState.Loading
        )

    fun selectPeriod(period: StatsPeriod) {
        _selectedPeriod.value = period
        _periodOffset.value = 0
    }

    fun stepPeriod(delta: Int) {
        val next = (_periodOffset.value + delta).coerceAtMost(0)
        _periodOffset.value = next
    }

    private fun computeStats(
        workouts: List<CloudWorkoutSession>,
        dailySteps: List<CloudDailySteps>,
        period: StatsPeriod,
        offset: Int
    ): StatsDashboardData {
        val bounds = periodBounds(period, offset)
        val filteredWorkouts = workouts.filter {
            it.startedAtMillis in bounds.startMillis..bounds.endMillis
        }
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val filteredSteps = dailySteps.filter { item ->
            val parsed = runCatching { dateFormat.parse(item.dateKey)?.time }.getOrNull()
            parsed != null && parsed in bounds.startMillis..bounds.endMillis
        }

        val totalDist = filteredWorkouts.sumOf { it.distanceMeters }
        val totalTime = filteredWorkouts.sumOf { it.movingTimeMillis }
        val avgPace = MetricUtils.calculateAveragePaceSecPerKm(totalDist, totalTime)
        val bestPace = filteredWorkouts
            .map { it.averagePaceSecPerKm.toInt() }
            .filter { it > 0 }
            .minOrNull() ?: 0
        val avgSpeed = MetricUtils.formatSpeedKmH(totalDist, totalTime)
        val runs = filteredWorkouts.count { WorkoutType.fromRaw(it.type) == WorkoutType.RUN }
        val walks = filteredWorkouts.count { WorkoutType.fromRaw(it.type) == WorkoutType.WALK }
        val calories = filteredWorkouts.sumOf { it.caloriesBurned.toInt() }
        val stepsSum = filteredSteps.sumOf { it.steps }

        val buckets = buildChartBuckets(period, bounds.startMillis, filteredWorkouts)

        return StatsDashboardData(
            period = period,
            periodOffset = offset,
            periodTitle = bounds.title,
            totalDistanceMeters = totalDist,
            totalMovingTimeMillis = totalTime,
            averagePaceSecPerKm = avgPace,
            bestPaceSecPerKm = bestPace,
            averageSpeedKmH = avgSpeed,
            totalWorkouts = filteredWorkouts.size,
            runsCount = runs,
            walksCount = walks,
            totalCalories = calories,
            totalSteps = stepsSum,
            chartBuckets = buckets
        )
    }

    private data class PeriodBounds(
        val startMillis: Long,
        val endMillis: Long,
        val title: String
    )

    private fun periodBounds(period: StatsPeriod, offset: Int): PeriodBounds {
        val cal = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return when (period) {
            StatsPeriod.WEEK -> {
                cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                cal.add(Calendar.WEEK_OF_YEAR, offset)
                val start = cal.timeInMillis
                val endCal = (cal.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_YEAR, 6)
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                val end = endCal.timeInMillis
                val fmt = SimpleDateFormat("MMM d", Locale.getDefault())
                val title = "${fmt.format(cal.time)} – ${fmt.format(endCal.time)}"
                PeriodBounds(start, end, title)
            }
            StatsPeriod.MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.add(Calendar.MONTH, offset)
                val start = cal.timeInMillis
                val endCal = (cal.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                val fmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                PeriodBounds(start, endCal.timeInMillis, fmt.format(cal.time))
            }
            StatsPeriod.YEAR -> {
                cal.set(Calendar.MONTH, Calendar.JANUARY)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.add(Calendar.YEAR, offset)
                val start = cal.timeInMillis
                val endCal = (cal.clone() as Calendar).apply {
                    set(Calendar.MONTH, Calendar.DECEMBER)
                    set(Calendar.DAY_OF_MONTH, 31)
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                val fmt = SimpleDateFormat("yyyy", Locale.getDefault())
                PeriodBounds(start, endCal.timeInMillis, fmt.format(cal.time))
            }
        }
    }

    private fun buildChartBuckets(
        period: StatsPeriod,
        startMillis: Long,
        workouts: List<CloudWorkoutSession>
    ): List<PeriodChartBucket> {
        val cal = Calendar.getInstance()
        return when (period) {
            StatsPeriod.WEEK -> {
                val labels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                val grouped = Array(7) { mutableListOf<CloudWorkoutSession>() }
                for (w in workouts) {
                    cal.timeInMillis = w.startedAtMillis
                    val dow = cal.get(Calendar.DAY_OF_WEEK)
                    val idx = if (dow == Calendar.SUNDAY) 6 else (dow - Calendar.MONDAY).coerceIn(0, 6)
                    grouped[idx].add(w)
                }
                labels.mapIndexed { idx, lbl ->
                    PeriodChartBucket(
                        label = lbl,
                        distanceMeters = grouped[idx].sumOf { it.distanceMeters },
                        workoutsCount = grouped[idx].size
                    )
                }
            }
            StatsPeriod.MONTH -> {
                val labels = listOf("W1", "W2", "W3", "W4", "W5")
                val grouped = Array(5) { mutableListOf<CloudWorkoutSession>() }
                for (w in workouts) {
                    cal.timeInMillis = w.startedAtMillis
                    val dom = cal.get(Calendar.DAY_OF_MONTH)
                    val idx = ((dom - 1) / 7).coerceIn(0, 4)
                    grouped[idx].add(w)
                }
                labels.mapIndexed { idx, lbl ->
                    PeriodChartBucket(
                        label = lbl,
                        distanceMeters = grouped[idx].sumOf { it.distanceMeters },
                        workoutsCount = grouped[idx].size
                    )
                }
            }
            StatsPeriod.YEAR -> {
                val labels = listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")
                val grouped = Array(12) { mutableListOf<CloudWorkoutSession>() }
                for (w in workouts) {
                    cal.timeInMillis = w.startedAtMillis
                    val monthIdx = cal.get(Calendar.MONTH).coerceIn(0, 11)
                    grouped[monthIdx].add(w)
                }
                labels.mapIndexed { idx, lbl ->
                    PeriodChartBucket(
                        label = lbl,
                        distanceMeters = grouped[idx].sumOf { it.distanceMeters },
                        workoutsCount = grouped[idx].size
                    )
                }
            }
        }
    }

    class Factory(
        private val cloudRepository: CloudFitnessRepository,
        private val activityRepository: ActivityRepository,
        private val currentUserId: String?
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return StatsViewModel(cloudRepository, activityRepository, currentUserId) as T
        }
    }
}
