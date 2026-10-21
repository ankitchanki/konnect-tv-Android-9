package io.konnecttv.glowlauncher

import android.service.notification.NotificationListenerService

/** Enables Android's media-session access. Notification contents are neither read nor stored. */
class MediaAccessService : NotificationListenerService()
