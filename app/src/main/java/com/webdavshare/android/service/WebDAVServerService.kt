package com.webdavshare.android.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.webdavshare.android.MainActivity
import com.webdavshare.android.R
import com.webdavshare.android.webdav.WebDAVServer
import kotlinx.coroutines.*
import java.io.IOException

/**
 * Service au premier plan qui maintient le serveur WebDAV actif.
 */
class WebDAVServerService : Service() {

    @Volatile
    private var webdavServer: WebDAVServer? = null

    @Volatile
    private var serverStarting = false

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var port = DEFAULT_PORT

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        port = intent.getIntExtra("PORT", DEFAULT_PORT)
        startForeground(NOTIFICATION_ID, createNotification())
        startWebDAVServer(port)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopWebDAVServer()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startWebDAVServer(requestedPort: Int) {
        synchronized(this) {
            if (webdavServer != null || serverStarting) {
                Log.i(TAG, "Démarrage ignoré : serveur déjà démarré ou en cours de démarrage")
                return
            }
            serverStarting = true
        }

        serviceScope.launch {
            val server = WebDAVServer(this@WebDAVServerService, requestedPort)
            try {
                server.start()
                webdavServer = server
                Log.i(TAG, "Serveur WebDAV opérationnel sur le port $requestedPort")
            } catch (e: IOException) {
                Log.e(TAG, "Impossible de démarrer WebDAV sur le port $requestedPort", e)
                server.stop()
                stopSelf()
            } catch (e: Exception) {
                Log.e(TAG, "Erreur inattendue au démarrage du serveur WebDAV", e)
                server.stop()
                stopSelf()
            } finally {
                serverStarting = false
            }
        }
    }

    private fun stopWebDAVServer() {
        // Arrêt synchrone : ne pas lancer une coroutine qui pourrait être annulée
        // immédiatement par onDestroy().
        val server = webdavServer
        webdavServer = null
        try {
            server?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Erreur pendant l'arrêt du serveur WebDAV", e)
        }
        serverStarting = false
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

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE
            )
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "WebDAVService"
        private const val DEFAULT_PORT = 8080
        private const val CHANNEL_ID = "WebDAVServiceChannel"
        private const val NOTIFICATION_ID = 1
    }
}
