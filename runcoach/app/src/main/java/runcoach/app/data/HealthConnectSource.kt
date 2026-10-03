package runcoach.app.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsCadenceRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import runcoach.core.IntervalValue
import runcoach.core.RunData
import runcoach.core.TimedValue
import java.time.Duration
import java.time.Instant
import kotlin.reflect.KClass

/** A running session found in Health Connect (written there by Garmin Connect). */
data class RecordedRun(val id: String, val start: Instant, val end: Instant, val title: String?, val data: RunData)

/**
 * Reads runs from Health Connect. Garmin Connect writes workouts to Health Connect once you
 * turn this on in Garmin Connect: More → Settings → Connected Apps → Health Connect.
 */
class HealthConnectSource(private val context: Context) {

    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(SpeedRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(StepsCadenceRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
    )

    val backgroundPermission = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND

    fun isAvailable(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    suspend fun hasPermissions(): Boolean =
        client.permissionController.getGrantedPermissions().containsAll(permissions)

    fun supportsBackgroundRead(): Boolean =
        client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND) ==
            HealthConnectFeatures.FEATURE_STATUS_AVAILABLE

    /** The most recent outdoor or treadmill run in the last [lookback]. */
    suspend fun latestRun(lookback: Duration = Duration.ofDays(14)): RecordedRun? {
        val sessions = read(ExerciseSessionRecord::class, TimeRangeFilter.after(Instant.now().minus(lookback)))
        val session = sessions
            .filter {
                it.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_RUNNING ||
                    it.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL
            }
            .maxByOrNull { it.startTime } ?: return null
        return load(session)
    }

    private suspend fun load(session: ExerciseSessionRecord): RecordedRun {
        val start = session.startTime
        val range = TimeRangeFilter.between(start, session.endTime)
        fun t(i: Instant) = Duration.between(start, i).toMillis() / 1000.0

        val data = RunData(
            durationSec = t(session.endTime),
            heartRate = read(HeartRateRecord::class, range).flatMap { r ->
                r.samples.map { TimedValue(t(it.time), it.beatsPerMinute.toDouble()) }
            },
            speed = read(SpeedRecord::class, range).flatMap { r ->
                r.samples.map { TimedValue(t(it.time), it.speed.inMetersPerSecond) }
            },
            cadence = read(StepsCadenceRecord::class, range).flatMap { r ->
                r.samples.map { TimedValue(t(it.time), it.rate) }
            },
            steps = read(StepsRecord::class, range).map {
                IntervalValue(t(it.startTime), t(it.endTime), it.count.toDouble())
            },
            distance = read(DistanceRecord::class, range).map {
                IntervalValue(t(it.startTime), t(it.endTime), it.distance.inMeters)
            },
        )
        return RecordedRun(session.metadata.id, start, session.endTime, session.title, data)
    }

    private suspend fun <T : Record> read(type: KClass<T>, range: TimeRangeFilter): List<T> {
        val out = mutableListOf<T>()
        var token: String? = null
        do {
            val response = client.readRecords(ReadRecordsRequest(type, range, pageToken = token))
            out += response.records
            token = response.pageToken
        } while (token != null)
        return out
    }
}
