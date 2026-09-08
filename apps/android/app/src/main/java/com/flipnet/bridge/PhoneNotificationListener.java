package com.flipnet.bridge;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

// Collecte les notifications autorisées.
public final class PhoneNotificationListener extends NotificationListenerService {
    // Mémorise une notification autorisée.
    @Override
    public void onNotificationPosted(StatusBarNotification statusBarNotification) {
        if (statusBarNotification == null || statusBarNotification.isOngoing()) return;
        String packageName = statusBarNotification.getPackageName();
        if (getPackageName().equals(packageName)) return;
        Notification notification = statusBarNotification.getNotification();
        if (notification == null || notification.extras == null) return;
        CharSequence title = notification.extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if (text == null) text = notification.extras.getCharSequence(Notification.EXTRA_TEXT);
        PhoneLink.recordNotification(
                this,
                packageName,
                PhoneLink.applicationLabel(this, packageName),
                title == null ? "" : title.toString(),
                text == null ? "" : text.toString(),
                statusBarNotification.getPostTime());
    }
}
