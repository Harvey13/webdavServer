package com.webdavshare.android.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.webdavshare.android.MainActivity
import com.webdavshare.android.R
import com.webdavshare.android.webdav.WebDAVServer
import kotlinx.coroutines.*
import java.io.IOException

/**
 * Service en premier plan qui gère le serveur WebDAV
 */
class WebDAVServerService : Service() {

    private var webdavServer: WebDAVServer? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var port = 8080

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            port = it.getIntExtra("PORT", 8080)
            
            startForeground(NOTIFICATION_ID, createNotification())
            startWebDAVServer(port)
        }
        
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopWebDAVServer()
        serviceScope.cancel()
    }

    private fun startWebDAVServer(port: Int) {
        serviceScope.launch {
            try {
                webdavServer = WebDAVServer(this@WebDAVServerService, port)
                webdavServer?.start()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun stopWebDAVServer() {
        serviceScope.launch {
            webdavServer?.stop()
            webdavServer = null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "WebDAV Service Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Gère le service WebDAV en arrière-plan"
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE
            )
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentIntent(pendingIntent)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "WebDAVServiceChannel"
        private const val NOTIFICATION_ID = 1
    }
}
