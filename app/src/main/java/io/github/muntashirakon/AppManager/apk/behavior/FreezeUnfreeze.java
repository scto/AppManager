// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.behavior;

import android.annotation.UserIdInt;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.UserHandleHidden;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.NotificationUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.Utils;

public final class FreezeUnfreeze {
    @IntDef(flag = true, value = {
            FLAG_ON_UNFREEZE_OPEN_APP,
            FLAG_ON_OPEN_APP_NO_TASK,
            FLAG_FREEZE_ON_SCREEN_OFF,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface FreezeFlags {
    }

    public static final int FLAG_ON_UNFREEZE_OPEN_APP = 1 << 0;
    public static final int FLAG_ON_OPEN_APP_NO_TASK = 1 << 1;
    public static final int FLAG_FREEZE_ON_SCREEN_OFF = 1 << 2;

    public static class ShortcutInfo {
        public final String shortcutId;
        @NonNull
        public final String packageName;
        @UserIdInt
        public final int userId;
        @FreezeFlags
        public final int flags;

        private Bitmap icon;
        private String label;

        public ShortcutInfo(@NonNull String packageName, int userId, int flags) {
            this.shortcutId = "freeze:u=" + userId + ",p=" + packageName;
            this.packageName = packageName;
            this.userId = userId;
            this.flags = flags;
        }

        public Bitmap getIcon() {
            return icon;
        }

        public void setIcon(Bitmap icon) {
            this.icon = icon;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }
    }

    static final String EXTRA_PACKAGE_NAME = "pkg";
    static final String EXTRA_USER_ID = "user";
    static final String EXTRA_FLAGS = "flags";

    @NonNull
    public static Intent getShortcutIntent(@NonNull Context context, @NonNull ShortcutInfo shortcutInfo) {
        Intent intent = new Intent(context, FreezeUnfreezeActivity.class);
        intent.putExtra(EXTRA_PACKAGE_NAME, shortcutInfo.packageName);
        intent.putExtra(EXTRA_USER_ID, shortcutInfo.userId);
        intent.putExtra(EXTRA_FLAGS, shortcutInfo.flags);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    @Nullable
    public static ShortcutInfo getShortcutInfo(@NonNull Intent intent) {
        String packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME);
        if (packageName == null) {
            return null;
        }
        int userId = intent.getIntExtra(EXTRA_USER_ID, UserHandleHidden.myUserId());
        int flags = intent.getIntExtra(EXTRA_FLAGS, 0);
        return new ShortcutInfo(packageName, userId, flags);
    }

    static void launchApp(@NonNull FragmentActivity activity, @NonNull ShortcutInfo shortcutInfo) {
        if (shortcutInfo.userId != UserHandleHidden.myUserId()) {
            return;
        }
        Intent launchIntent = Utils.isTv(activity) ? activity.getPackageManager().getLeanbackLaunchIntentForPackage(shortcutInfo.packageName)
                : activity.getPackageManager().getLaunchIntentForPackage(shortcutInfo.packageName);
        if (launchIntent == null) {
            return;
        }
        if ((shortcutInfo.flags & FLAG_ON_OPEN_APP_NO_TASK) != 0) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        }
        int notificationId = -1;
        try {
            activity.startActivity(launchIntent);
            PendingIntent pendingIntent = PendingIntent.getActivity(activity, 0,
                    getShortcutIntent(activity, shortcutInfo), Intent.FLAG_ACTIVITY_NEW_TASK);
            notificationId = NotificationUtils.displayFreezeUnfreezeNotification(activity, builder -> builder
                    .setAutoCancel(true)
                    .setDefaults(Notification.DEFAULT_ALL)
                    .setWhen(System.currentTimeMillis())
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setTicker(activity.getText(R.string.freeze))
                    .setContentTitle(shortcutInfo.getLabel())
                    .setContentText(activity.getString(R.string.tap_to_freeze_app))
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .build());
        } catch (Throwable th) {
            UIUtils.displayLongToast(th.getLocalizedMessage());
            return;
        }
        if ((shortcutInfo.flags & FLAG_FREEZE_ON_SCREEN_OFF) != 0) {
            // TODO: 29/11/22 Run a foreground service that would put all the configured packages back to freeze
        }
    }
}
