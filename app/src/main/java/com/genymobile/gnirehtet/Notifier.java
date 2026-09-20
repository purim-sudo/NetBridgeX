[Reading 100 lines from start (total: 100 lines, 0 remaining)]

package com.genymobile.gnirehtet;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;

/* JADX INFO: loaded from: classes2.dex */
public class Notifier {
    private static final String CHANNEL_ID = "Gnirehtet";
    private static final int NOTIFICATION_ID = 42;
    private final Service context;
    private boolean failure;
    private boolean foreground;

    public Notifier(Service context) {
        this.context = context;
    }

    private Notification createNotification(boolean failed) {
        Notification.Builder builder = createNotificationBuilder().setContentTitle(this.context.getString(R.string.app_name)).setOngoing(true).setOnlyAlertOnce(true).setCategory("service");
        if (failed) {
            builder.setContentText(this.context.getString(R.string.relay_disconnected));
            builder.setSmallIcon(R.drawable.ic_report_problem_24dp);
        } else {
            builder.setContentText(this.context.getString(R.string.relay_connected));
            builder.setSmallIcon(R.drawable.ic_public_24dp);
        }
        builder.addAction(createStopAction());
        return builder.build();
    }

    private Notification.Builder createNotificationBuilder() {
        if (Build.VERSION.SDK_INT >= 26) {
            return new Notification.Builder(this.context, CHANNEL_ID);
        }
        return new Notification.Builder(this.context);
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, this.context.getString(R.string.app_name), 2);
        channel.setDescription(this.context.getString(R.string.app_name));
        channel.setShowBadge(false);
        getNotificationManager().createNotificationChannel(channel);
    }

    public void start() {
        this.failure = false;
        if (Build.VERSION.SDK_INT >= 26) {
            createNotificationChannel();
        }
        Notification notification = createNotification(false);
        if (Build.VERSION.SDK_INT >= 34) {
            this.context.startForeground(NOTIFICATION_ID, notification, 1073741824);
        } else {
            this.context.startForeground(NOTIFICATION_ID, notification);
        }
        this.foreground = true;
    }

    public void stop() {
        if (!this.foreground) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 24) {
            this.context.stopForeground(1);
        } else {
            this.context.stopForeground(true);
        }
        this.foreground = false;
    }

    public void setFailure(boolean failed) {
        if (this.failure != failed) {
            this.failure = failed;
            if (!canPostNotifications()) {
                return;
            }
            getNotificationManager().notify(NOTIFICATION_ID, createNotification(failed));
        }
    }

    private boolean canPostNotifications() {
        return Build.VERSION.SDK_INT < 33 || this.context.checkSelfPermission("android.permission.POST_NOTIFICATIONS") == 0;
    }

    private Notification.Action createStopAction() {
        Intent stopIntent = new Intent(this.context, (Class<?>) GnirehtetActivity.class).setAction(GnirehtetActivity.ACTION_GNIREHTET_STOP);
        int flags = Build.VERSION.SDK_INT >= 23 ? 1073741824 | 67108864 : 1073741824;
        PendingIntent pendingIntent = PendingIntent.getActivity(this.context, 0, stopIntent, flags);
        return new Notification.Action.Builder(R.drawable.ic_close_24dp, this.context.getString(R.string.stop_vpn), pendingIntent).build();
    }

    private NotificationManager getNotificationManager() {
        return (NotificationManager) this.context.getSystemService("notification");
    }
}

[executed on device: DESKTOP-1RJKODI (291d4b53-196c-4a98-a307-e51f238e5e74)]