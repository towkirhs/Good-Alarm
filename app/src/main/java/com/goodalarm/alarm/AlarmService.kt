package com.goodalarm.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.goodalarm.data.AppDatabase
import com.goodalarm.data.Item
import com.goodalarm.widget.WidgetRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Plays the alarm (max volume, looping, vibrating) and shows the full-screen alarm.
 * Handles Snooze / Dismiss / Done and the "ignored -> re-ring up to 5 times" rule.
 */
class AlarmService : Service() {

    data class RingState(val id: Long, val title: String, val attempt: Int)

    companion object {
        const val ACTION_RING = "com.goodalarm.RING"
        const val ACTION_SNOOZE = "com.goodalarm.SNOOZE"
        const val ACTION_DISMISS = "com.goodalarm.DISMISS"
        const val ACTION_DONE = "com.goodalarm.DONE"

        private const val CHANNEL_ID = "alarm_ringing"
        private const val NOTIFICATION_ID = 4242

        /** How long one ring lasts before it counts as ignored. */
        private const val RING_DURATION_MS = 60_000L

        private val _ringing = MutableStateFlow<RingState?>(null)

        /** What is ringing right now; the alarm screen watches this. */
        val ringing: StateFlow<RingState?> = _ringing
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val dao by lazy { AppDatabase.get(this).items() }

    private var current: RingState? = null
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var timeoutJob: Job? = null
    private var savedAlarmVolume = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (val action = intent?.action) {
            ACTION_RING -> {
                val id = intent.getLongExtra(AlarmScheduler.EXTRA_ID, -1L)
                val attempt = intent.getIntExtra(AlarmScheduler.EXTRA_ATTEMPT, 0)
                // Android requires the foreground notification right away.
                startForegroundWith(RingState(id, current?.title ?: "Alarm", attempt))
                startRinging(id, attempt)
            }
            ACTION_SNOOZE, ACTION_DISMISS, ACTION_DONE -> userAction(action)
            else -> stopEverything()
        }
        return START_NOT_STICKY
    }

    private fun startRinging(id: Long, attempt: Int) {
        val previous = current
        scope.launch {
            // A second alarm arrived while one was ringing: count the first as ignored.
            if (previous != null && previous.id != id) recordIgnored(previous)
            stopSound()
            timeoutJob?.cancel()

            val item = dao.get(id)
            if (item == null) {
                stopEverything()
                return@launch
            }
            val state = RingState(id, item.title, attempt)
            current = state
            _ringing.value = state
            startForegroundWith(state)
            playSound(item.soundUri)

            timeoutJob = launch {
                delay(RING_DURATION_MS)
                val ignored = current ?: return@launch
                stopSound()
                recordIgnored(ignored)
                stopEverything()
            }
        }
    }

    private fun userAction(action: String) {
        val state = current
        timeoutJob?.cancel()
        stopSound()
        scope.launch {
            if (state != null) {
                val item = dao.get(state.id)
                if (item != null) when (action) {
                    // Snooze doesn't use up one of the 5 rings.
                    ACTION_SNOOZE -> AlarmScheduler.setAt(
                        this@AlarmService, state.id,
                        System.currentTimeMillis() + AlarmScheduler.RERING_MS, state.attempt,
                    )
                    ACTION_DISMISS -> finishItem(item, markDone = false, missed = false)
                    ACTION_DONE -> finishItem(item, markDone = true, missed = false)
                }
            }
            stopEverything()
        }
    }

    /** Nobody answered: ring again in 5 minutes, or give up after the 5th ring. */
    private suspend fun recordIgnored(state: RingState) {
        val item = dao.get(state.id) ?: return
        if (state.attempt + 1 < AlarmScheduler.MAX_RINGS) {
            AlarmScheduler.setAt(
                this, state.id,
                System.currentTimeMillis() + AlarmScheduler.RERING_MS, state.attempt + 1,
            )
        } else {
            finishItem(item, markDone = false, missed = true)
        }
    }

    /** This ring is over. Daily items move to tomorrow; one-time items stay put. */
    private suspend fun finishItem(item: Item, markDone: Boolean, missed: Boolean) {
        if (item.repeatDaily) {
            val after = maxOf(System.currentTimeMillis(), item.timeMillis)
            val next = item.copy(
                timeMillis = AlarmScheduler.nextOccurrence(item.timeMillis, after),
                done = false,
                missed = missed,
            )
            dao.update(next)
            AlarmScheduler.schedule(this, next)
        } else {
            dao.update(item.copy(done = item.done || markDone, missed = missed))
            AlarmScheduler.cancel(this, item.id)
        }
        WidgetRefresher.refresh(this)
    }

    // ---- Sound and vibration ----

    private fun playSound(soundUri: String?) {
        val audio = getSystemService(AudioManager::class.java)
        // Loud on purpose: alarm volume to max while ringing, restored afterwards.
        savedAlarmVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        runCatching {
            audio.setStreamVolume(
                AudioManager.STREAM_ALARM,
                audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0,
            )
        }

        val fallback: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: Settings.System.DEFAULT_ALARM_ALERT_URI
        val chosen: Uri = soundUri?.let(Uri::parse) ?: fallback

        player = tryPlayer(chosen) ?: tryPlayer(fallback)

        vibrator = if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0))
    }

    private fun tryPlayer(uri: Uri): MediaPlayer? = try {
        MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            setDataSource(this@AlarmService, uri)
            isLooping = true
            setWakeMode(this@AlarmService, PowerManager.PARTIAL_WAKE_LOCK)
            prepare()
            start()
        }
    } catch (e: Exception) {
        null
    }

    private fun stopSound() {
        player?.let {
            runCatching { it.stop() }
            it.release()
        }
        player = null
        vibrator?.cancel()
        vibrator = null
        if (savedAlarmVolume >= 0) {
            val volume = savedAlarmVolume
            runCatching {
                getSystemService(AudioManager::class.java)
                    .setStreamVolume(AudioManager.STREAM_ALARM, volume, 0)
            }
            savedAlarmVolume = -1
        }
    }

    // ---- Notification ----

    private fun startForegroundWith(state: RingState) {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Ringing alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(null, null) // the service plays the sound itself
                    enableVibration(false)
                },
            )
        }

        val fullScreen = PendingIntent.getActivity(
            this, 1,
            Intent(this, AlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        fun action(name: String, code: Int) = PendingIntent.getService(
            this, code,
            Intent(this, AlarmService::class.java).setAction(name),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val ringText = if (state.attempt > 0) {
            "Ring ${state.attempt + 1} of ${AlarmScheduler.MAX_RINGS}"
        } else {
            "Tap to open"
        }
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(state.title)
            .setContentText(ringText)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
            .addAction(0, "Snooze 5 min", action(ACTION_SNOOZE, 2))
            .addAction(0, "Dismiss", action(ACTION_DISMISS, 3))
            .addAction(0, "Done", action(ACTION_DONE, 4))
            .build()

        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification,
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
        )
    }

    private fun stopEverything() {
        timeoutJob?.cancel()
        stopSound()
        current = null
        _ringing.value = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopSound()
        _ringing.value = null
        scope.cancel()
        super.onDestroy()
    }
}
