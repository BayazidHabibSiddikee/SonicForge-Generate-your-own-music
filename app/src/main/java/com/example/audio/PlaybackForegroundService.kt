package com.example.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

/**
 * Foreground mediaPlayback service that posts a rich playback notification with
 * Previous, Play/Pause, Next, and Stop actions and maintains a partial wake lock
 * so audio keeps playing smoothly in background and sleep mode.
 */
class PlaybackForegroundService : Service() {

  private var wakeLock: PowerManager.WakeLock? = null

  override fun onCreate() {
    super.onCreate()
    createNotificationChannel()
    acquireWakeLockIfNeeded()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val controller = AudioPlayerController.getInstance(applicationContext)

    when (intent?.action) {
      ACTION_TOGGLE_PLAY_PAUSE -> controller.togglePlayPause()
      ACTION_PREVIOUS -> controller.skipPrevious()
      ACTION_NEXT -> controller.skipNext()
      ACTION_STOP -> {
        // Ensure foreground contract is satisfied if started via startForegroundService before stopping
        startForegroundSafely(buildNotification(controller))
        controller.stopPlayback()
        stopForegroundAndSelf()
        return START_NOT_STICKY
      }
    }

    val startedForeground = startForegroundSafely(buildNotification(controller))
    if (!startedForeground) {
      stopForegroundAndSelf()
      return START_NOT_STICKY
    }

    if (controller.state.value.isPlaying) {
      acquireWakeLockIfNeeded()
    } else {
      releaseWakeLock()
    }

    return START_STICKY
  }

  private fun startForegroundSafely(notification: Notification): Boolean {
    return try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ServiceCompat.startForeground(
          this,
          NOTIFICATION_ID,
          notification,
          ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
      } else {
        startForeground(NOTIFICATION_ID, notification)
      }
      true
    } catch (e: Exception) {
      Log.w(TAG, "Unable to enter foreground media playback state", e)
      false
    }
  }

  override fun onDestroy() {
    releaseWakeLock()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun stopForegroundAndSelf() {
    releaseWakeLock()
    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  private fun acquireWakeLockIfNeeded() {
    try {
      if (wakeLock == null) {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SonicForge:PlaybackWakeLock")
          ?.apply { setReferenceCounted(false) }
      }
      if (wakeLock?.isHeld == false) {
        wakeLock?.acquire(10 * 60 * 1000L) // bounded 10-min safety timeout refreshed on play
      }
    } catch (e: Exception) {
      Log.w(TAG, "Unable to acquire WakeLock", e)
    }
  }

  private fun releaseWakeLock() {
    try {
      if (wakeLock?.isHeld == true) {
        wakeLock?.release()
      }
    } catch (e: Exception) {
      Log.w(TAG, "Unable to release WakeLock", e)
    }
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val manager = getSystemService(NotificationManager::class.java) ?: return
      if (manager.getNotificationChannel(CHANNEL_ID) == null) {
        val channel = NotificationChannel(
          CHANNEL_ID,
          getString(R.string.notification_channel_name),
          NotificationManager.IMPORTANCE_LOW,
        ).apply {
          description = getString(R.string.notification_channel_description)
          setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
      }
    }
  }

  private fun buildNotification(controller: AudioPlayerController): Notification {
    val playbackState = controller.state.value
    val track = controller.currentTrack()

    val openAppIntent = Intent(this, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val contentPendingIntent = PendingIntent.getActivity(
      this,
      100,
      openAppIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    val prevIntent = serviceActionIntent(ACTION_PREVIOUS, 101)
    val playPauseIntent = serviceActionIntent(ACTION_TOGGLE_PLAY_PAUSE, 102)
    val nextIntent = serviceActionIntent(ACTION_NEXT, 103)
    val stopIntent = serviceActionIntent(ACTION_STOP, 104)

    val playPauseIcon = if (playbackState.isPlaying) {
      android.R.drawable.ic_media_pause
    } else {
      android.R.drawable.ic_media_play
    }
    val playPauseLabel = if (playbackState.isPlaying) "Pause" else "Play"

    val statusText = if (playbackState.isPlaying) {
      getString(R.string.notification_playing_status)
    } else {
      getString(R.string.notification_paused_status)
    }

    val transposedKey = if (track != null) {
      PitchShiftDsp.transposeKeyLabel(track.musicalKey, playbackState.pitchSemitones)
    } else {
      ""
    }
    val pitchBadge = if (kotlin.math.abs(playbackState.pitchSemitones) >= 0.05f) {
      " · $transposedKey (${PitchShiftDsp.formatSemitoneBadge(playbackState.pitchSemitones)})"
    } else {
      ""
    }

    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.ic_media_play)
      .setContentTitle(track?.title ?: getString(R.string.app_name))
      .setContentText(
        if (track != null) "${track.genre} · ${track.bpm} BPM$pitchBadge · $statusText" else statusText
      )
      .setSubText(getString(R.string.app_name))
      .setContentIntent(contentPendingIntent)
      .setOngoing(playbackState.isPlaying)
      .setOnlyAlertOnce(true)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .addAction(android.R.drawable.ic_media_previous, "Previous", prevIntent)
      .addAction(playPauseIcon, playPauseLabel, playPauseIntent)
      .addAction(android.R.drawable.ic_media_next, "Next", nextIntent)
      .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
      .build()
  }

  private fun serviceActionIntent(action: String, requestCode: Int): PendingIntent {
    val intent = Intent(this, PlaybackForegroundService::class.java).apply {
      this.action = action
    }
    return PendingIntent.getService(
      this,
      requestCode,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }

  companion object {
    private const val TAG = "PlaybackForegroundSvc"
    const val CHANNEL_ID = "sonicforge_playback_channel"
    const val NOTIFICATION_ID = 4201

    const val ACTION_SYNC = "com.example.audio.ACTION_SYNC"
    const val ACTION_TOGGLE_PLAY_PAUSE = "com.example.audio.ACTION_TOGGLE_PLAY_PAUSE"
    const val ACTION_PREVIOUS = "com.example.audio.ACTION_PREVIOUS"
    const val ACTION_NEXT = "com.example.audio.ACTION_NEXT"
    const val ACTION_STOP = "com.example.audio.ACTION_STOP"

    fun syncNotification(context: Context) {
      try {
        val intent = Intent(context, PlaybackForegroundService::class.java).apply {
          action = ACTION_SYNC
        }
        ContextCompat.startForegroundService(context, intent)
      } catch (e: Exception) {
        Log.w(TAG, "Unable to start/sync foreground playback service", e)
      }
    }

    fun stopService(context: Context) {
      try {
        context.stopService(Intent(context, PlaybackForegroundService::class.java))
      } catch (e: Exception) {
        Log.w(TAG, "Unable to stop foreground playback service", e)
      }
    }
  }
}
