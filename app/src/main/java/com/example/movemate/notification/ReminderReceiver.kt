package com.example.movemate.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_STEP_REMINDER = "com.example.movemate.ACTION_STEP_REMINDER"
        const val ACTION_FOOD_REMINDER = "com.example.movemate.ACTION_FOOD_REMINDER"
        const val ACTION_SLEEP_REMINDER = "com.example.movemate.ACTION_SLEEP_REMINDER"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_STEP_REMINDER -> NotificationHelper.showNotification(
                context,
                "Step Goal Reminder",
                "Have you hit your step goal today? Keep moving!",
                NotificationHelper.NOTIF_ID_STEP_REMINDER
            )
            ACTION_FOOD_REMINDER -> NotificationHelper.showNotification(
                context,
                "Log Your Meals",
                "Don't forget to log what you've eaten today.",
                NotificationHelper.NOTIF_ID_FOOD_REMINDER
            )
            ACTION_SLEEP_REMINDER -> NotificationHelper.showNotification(
                context,
                "Bedtime Reminder",
                "Time to wind down and get a good night's sleep!",
                NotificationHelper.NOTIF_ID_SLEEP_REMINDER
            )
        }
    }
}
