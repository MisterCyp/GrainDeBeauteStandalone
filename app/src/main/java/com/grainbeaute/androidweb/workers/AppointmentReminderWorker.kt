package com.grainbeaute.androidweb.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.grainbeaute.androidweb.R

class AppointmentReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val practitionerName = inputData.getString("practitioner_name")
        val daysBefore = inputData.getInt("days_before", 7)
        val appointmentDate = inputData.getString("appointment_date") ?: ""

        val title = "Rappel dermatologue"
        val body = if (!practitionerName.isNullOrBlank()) {
            "Votre RDV chez $practitionerName est dans $daysBefore jours ($appointmentDate). Pensez à photographier vos grains avant la consultation."
        } else {
            "Votre rendez-vous dermatologique est dans $daysBefore jours ($appointmentDate)."
        }

        showNotification(title, body)

        return Result.success()
    }

    private fun showNotification(title: String, body: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "appointment_reminders"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Rappels de rendez-vous", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Utilisation de l'icône existante
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1, notification)
    }
}
