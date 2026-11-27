package com.dynamicisland.redminote8

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

object IslandLiveStore {
    @Volatile var mediaActive: Boolean = false
    @Volatile var mediaPlaying: Boolean = false
    @Volatile var mediaTitle: String = ""
    @Volatile var mediaArtist: String = ""
    @Volatile var mediaPackage: String = ""
    @Volatile var mediaArt: Bitmap? = null
    @Volatile var activeController: MediaController? = null

    @Volatile var notifActive: Boolean = false
    @Volatile var notifAppName: String = ""
    @Volatile var notifTitle: String = ""
    @Volatile var notifText: String = ""
    @Volatile var notifPackage: String = ""
    @Volatile var notifTimestamp: Long = 0L
    @Volatile var notifIsVoice: Boolean = false
    @Volatile var notifContentIntent: PendingIntent? = null
    @Volatile var notifPlayAction: PendingIntent? = null
    @Volatile var notifLargeIcon: Bitmap? = null
    @Volatile var notifVersion: Long = 0L
    @Volatile var listenerConnected: Boolean = false

    @Volatile var secondaryMediaActive: Boolean = false
    @Volatile var secondaryMediaPackage: String = ""
    @Volatile var secondaryMediaTitle: String = ""

    @Volatile var lastNotifMediaPackage: String = ""

    @Volatile var callActive: Boolean = false
    @Volatile var callCaller: String = ""
    @Volatile var callPackage: String = ""
    @Volatile var callStartTime: Long = 0L
    @Volatile var callNotifKey: String = ""
    @Volatile var callContentIntent: PendingIntent? = null
    @Volatile var callEndAction: PendingIntent? = null
    @Volatile var callAnswerAction: PendingIntent? = null
    @Volatile var callIsRinging: Boolean = false
}

class IslandNotificationListener : NotificationListenerService() {

    private var mediaSessionManager: MediaSessionManager? = null
    private var currentController: MediaController? = null

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        bindBestController(controllers)
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            syncMediaState(currentController)
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            syncMediaState(currentController)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        IslandLiveStore.listenerConnected = true
        try {
            mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val comp = ComponentName(this, IslandNotificationListener::class.java)
            mediaSessionManager?.addOnActiveSessionsChangedListener(sessionListener, comp)
            bindBestController(mediaSessionManager?.getActiveSessions(comp))
        } catch (_: Exception) {
        }
    }

    override fun onListenerDisconnected() {
        IslandLiveStore.listenerConnected = false
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        IslandLiveStore.listenerConnected = false
        try {
            currentController?.unregisterCallback(controllerCallback)
            mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionListener)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    private fun bindBestController(controllers: List<MediaController>?) {
        try {
            currentController?.unregisterCallback(controllerCallback)
        } catch (_: Exception) {
        }

        if (controllers.isNullOrEmpty()) {
            currentController = null
            IslandLiveStore.activeController = null
            IslandLiveStore.mediaActive = false
            IslandLiveStore.mediaPlaying = false
            notifyIslandService(DynamicIslandService.ACTION_REFRESH)
            return
        }

        val playing = controllers.firstOrNull {
            val st = it.playbackState?.state
            st == PlaybackState.STATE_PLAYING || st == PlaybackState.STATE_BUFFERING
        } ?: controllers.first()

        currentController = playing
        IslandLiveStore.activeController = playing
        playing.registerCallback(controllerCallback)
        syncMediaState(playing)
    }

    private fun syncMediaState(controller: MediaController?) {
        if (controller == null) {
            IslandLiveStore.mediaActive = false
            IslandLiveStore.mediaPlaying = false
            notifyIslandService(DynamicIslandService.ACTION_REFRESH)
            return
        }

        val state = controller.playbackState?.state ?: PlaybackState.STATE_NONE
        val active = state == PlaybackState.STATE_PLAYING ||
            state == PlaybackState.STATE_BUFFERING ||
            state == PlaybackState.STATE_PAUSED
        val meta = controller.metadata
        val pkg = controller.packageName ?: ""

        IslandLiveStore.mediaActive = active
        IslandLiveStore.mediaPlaying = (state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING)
        if (pkg.isNotBlank()) {
            IslandLiveStore.mediaPackage = pkg
            IslandLiveStore.lastNotifMediaPackage = pkg
        }
        IslandLiveStore.mediaTitle = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?.takeIf { it.isNotBlank() } ?: getFriendlyAppName(pkg)
        IslandLiveStore.mediaArtist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?.takeIf { it.isNotBlank() }
            ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)?.takeIf { it.isNotBlank() }
            ?: getFriendlyAppName(pkg)
        IslandLiveStore.mediaArt = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)

        notifyIslandService(DynamicIslandService.ACTION_REFRESH)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName == packageName) return
        val notif = sbn.notification ?: return
        val extras = notif.extras ?: return
        val pkg = sbn.packageName ?: return

        val token: MediaSession.Token? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION)
        }

        if (token != null || notif.category == Notification.CATEGORY_TRANSPORT) {
            IslandLiveStore.lastNotifMediaPackage = pkg
            if (token != null) {
                try {
                    val ctrl = MediaController(this, token)
                    bindBestController(listOf(ctrl))
                    return
                } catch (_: Exception) {
                }
            }
            val mTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
            val mText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
            if (mTitle.isNotEmpty() || mText.isNotEmpty()) {
                IslandLiveStore.mediaActive = true
                IslandLiveStore.mediaPlaying = true
                IslandLiveStore.mediaPackage = pkg
                IslandLiveStore.mediaTitle = mTitle.ifBlank { getFriendlyAppName(pkg) }
                IslandLiveStore.mediaArtist = mText.ifBlank { getFriendlyAppName(pkg) }
                notifyIslandService(DynamicIslandService.ACTION_REFRESH)
                return
            }
        }

        val isCallNotif = notif.category == Notification.CATEGORY_CALL ||
            (notif.actions?.any { act ->
                val t = act.title?.toString()?.lowercase().orEmpty()
                t.contains("إنهاء") || t.contains("انهاء") || t.contains("رفض") ||
                    t.contains("رد") || t.contains("إجابة") || t.contains("قبول") ||
                    t.contains("answer") || t.contains("accept") ||
                    t.contains("hang up") || t.contains("end call") || t.contains("decline") ||
                    t == "end" || t.contains("إغلاق المكالمة")
            } == true)

        if (isCallNotif) {
            val cTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
            val cText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
            var answerPending: PendingIntent? = null
            var endPending: PendingIntent? = null
            notif.actions?.forEach { act ->
                val t = act.title?.toString()?.lowercase().orEmpty()
                if (t.contains("رد") || t.contains("إجابة") || t.contains("اجابة") ||
                    t.contains("قبول") || t.contains("answer") || t.contains("accept")
                ) {
                    answerPending = act.actionIntent
                } else if (t.contains("إنهاء") || t.contains("انهاء") || t.contains("رفض") ||
                    t.contains("hang up") || t.contains("end") || t.contains("decline") ||
                    t.contains("reject") || t.contains("dismiss") || t.contains("إغلاق")
                ) {
                    endPending = act.actionIntent
                }
            }
            val lowerCombined = "$cTitle $cText".lowercase()
            val ringing = answerPending != null ||
                lowerCombined.contains("مكالمة واردة") ||
                lowerCombined.contains("incoming") ||
                lowerCombined.contains("يتصل بك") ||
                lowerCombined.contains("calling")
            val wasRinging = IslandLiveStore.callIsRinging
            val sameCall = IslandLiveStore.callActive && IslandLiveStore.callNotifKey == sbn.key
            IslandLiveStore.callActive = true
            IslandLiveStore.callIsRinging = ringing
            IslandLiveStore.callNotifKey = sbn.key ?: ""
            IslandLiveStore.callPackage = pkg
            IslandLiveStore.callCaller = cTitle.ifBlank { cText.ifBlank { getFriendlyAppName(pkg) } }
            if (!sameCall || (wasRinging && !ringing)) {
                IslandLiveStore.callStartTime = if (!ringing && notif.`when` > 0L && notif.`when` <= System.currentTimeMillis()) {
                    notif.`when`
                } else {
                    System.currentTimeMillis()
                }
            }
            IslandLiveStore.callContentIntent = notif.contentIntent
            IslandLiveStore.callAnswerAction = answerPending
            IslandLiveStore.callEndAction = endPending
            notifyIslandService(
                if (ringing) DynamicIslandService.ACTION_CALL_RINGING
                else DynamicIslandService.ACTION_CALL_UPDATE
            )
            return
        }

        if ((notif.flags and Notification.FLAG_ONGOING_EVENT) != 0) return
        if ((notif.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        var title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.mapNotNull { it?.toString()?.trim() }
            ?.filter { it.isNotEmpty() }
            ?.takeLast(2)
            ?.joinToString(" • ")
            .orEmpty()
        val shortText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()

        // Parse MessagingStyle EXTRA_MESSAGES (WhatsApp, Messenger, Telegram, SMS!)
        var msgStyleText = ""
        var msgStyleSender = ""
        try {
            @Suppress("DEPRECATION")
            val msgArr = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            if (msgArr != null && msgArr.isNotEmpty()) {
                val lastBundle = msgArr.last() as? android.os.Bundle
                if (lastBundle != null) {
                    msgStyleText = lastBundle.getCharSequence("text")?.toString()?.trim().orEmpty()
                    msgStyleSender = lastBundle.getCharSequence("sender")?.toString()?.trim().orEmpty()
                }
            }
        } catch (_: Exception) {
        }

        if (title.isBlank() && msgStyleSender.isNotBlank()) title = msgStyleSender
        val finalText = when {
            msgStyleText.isNotEmpty() -> msgStyleText
            bigText.isNotEmpty() -> bigText
            lines.isNotEmpty() -> lines
            else -> shortText
        }
        if (title.isEmpty() && finalText.isEmpty()) return

        val combinedLower = "$title $finalText".lowercase()
        val isVoice = combinedLower.contains("رسالة صوتية") ||
            combinedLower.contains("مقطع صوتي") ||
            combinedLower.contains("تسجيل صوتي") ||
            combinedLower.contains("voice message") ||
            combinedLower.contains("voice note") ||
            combinedLower.contains("audio") ||
            combinedLower.contains("ptt") ||
            combinedLower.contains("🎤") ||
            combinedLower.contains("🎙")

        var playPending: PendingIntent? = null
        notif.actions?.forEach { act ->
            val actTitle = act.title?.toString()?.lowercase().orEmpty()
            if (actTitle.contains("تشغيل") || actTitle.contains("استماع") ||
                actTitle.contains("play") || actTitle.contains("listen")
            ) {
                playPending = act.actionIntent
            }
        }

        var largeBmp: Bitmap? = null
        try {
            val iconObj = notif.getLargeIcon()
            if (iconObj != null) {
                val d = iconObj.loadDrawable(this)
                if (d is BitmapDrawable) largeBmp = d.bitmap
            }
        } catch (_: Exception) {
        }

        IslandLiveStore.notifActive = true
        IslandLiveStore.notifPackage = pkg
        IslandLiveStore.notifAppName = getFriendlyAppName(pkg)
        IslandLiveStore.notifTitle = title.ifBlank { IslandLiveStore.notifAppName }
        IslandLiveStore.notifText = if (isVoice && finalText.isBlank()) "رسالة صوتية 🎙️ — اضغط استماع" else finalText
        IslandLiveStore.notifTimestamp = System.currentTimeMillis()
        IslandLiveStore.notifIsVoice = isVoice
        IslandLiveStore.notifContentIntent = notif.contentIntent
        IslandLiveStore.notifPlayAction = playPending
        IslandLiveStore.notifLargeIcon = largeBmp
        IslandLiveStore.notifVersion = IslandLiveStore.notifVersion + 1L

        notifyIslandService(DynamicIslandService.ACTION_NEW_NOTIF)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn == null) return
        var changed = false
        if (IslandLiveStore.callActive &&
            (sbn.key == IslandLiveStore.callNotifKey || sbn.packageName == IslandLiveStore.callPackage)
        ) {
            IslandLiveStore.callActive = false
            IslandLiveStore.callNotifKey = ""
            IslandLiveStore.callContentIntent = null
            IslandLiveStore.callEndAction = null
            IslandLiveStore.callAnswerAction = null
            IslandLiveStore.callIsRinging = false
            changed = true
        }
        val removedNotif = sbn.notification
        val wasMediaNotif = removedNotif != null && (
            removedNotif.category == Notification.CATEGORY_TRANSPORT ||
                (removedNotif.extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true)
            )
        if (IslandLiveStore.mediaActive &&
            sbn.packageName == IslandLiveStore.mediaPackage &&
            (wasMediaNotif || !IslandLiveStore.mediaPlaying)
        ) {
            IslandLiveStore.activeController = null
            IslandLiveStore.mediaActive = false
            IslandLiveStore.mediaPlaying = false
            IslandLiveStore.mediaTitle = ""
            IslandLiveStore.mediaArtist = ""
            IslandLiveStore.mediaPackage = ""
            IslandLiveStore.mediaArt = null
            changed = true
        }
        if (changed) notifyIslandService(DynamicIslandService.ACTION_REFRESH)
    }

    private fun getFriendlyAppName(pkg: String?): String {
        if (pkg.isNullOrBlank()) return "تطبيق"
        return try {
            val ai = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(ai).toString()
        } catch (_: Exception) {
            pkg.substringAfterLast('.')
        }
    }

    private fun notifyIslandService(action: String) {
        if (!DynamicIslandService.isRunning) return
        sendBroadcast(Intent(action).setPackage(packageName))
    }
}
