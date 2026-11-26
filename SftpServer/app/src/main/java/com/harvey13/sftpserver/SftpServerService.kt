package com.harvey13.sftpserver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory
import org.apache.sshd.common.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.common.config.keys.KeyUtils
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.sftp.server.SftpSubsystemFactory
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlin.concurrent.thread

class SftpServerService : Service() {
    private var sshServer: SshServer? = null
    @Volatile private var starting = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopServer()
            stopSelf()
            return START_NOT_STICKY
        }
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, notification("Initialisation du serveur SFTP…"))
        if (!starting && sshServer == null) {
            getOrCreatePassword()
            starting = true
            thread(name = "SftpServerStart") {
                try {
                    startServer()
                    updateNotification("SFTP actif sur le port 2222")
                } catch (error: Exception) {
                    updateNotification("Erreur SFTP : ${error.message ?: "inconnue"}")
                    stopServer()
                } finally {
                    starting = false
                }
            }
        }
        return START_STICKY
    }

    private fun startServer() {
        val root = Environment.getExternalStorageDirectory().toPath().toAbsolutePath().normalize()
        if (!Files.isDirectory(root)) throw IOException("Stockage partagé introuvable.")
        val password = getOrCreatePassword()
        val keyFile = java.io.File(filesDir, "sftp-hostkey.ser").toPath()

        val server = SshServer.setUpDefaultServer()
        server.host = "0.0.0.0"
        server.port = PORT
        server.keyPairProvider = SimpleGeneratorHostKeyProvider(keyFile)
        server.fileSystemFactory = VirtualFileSystemFactory(root)
        server.passwordAuthenticator = PasswordAuthenticator { username, supplied, _ ->
            username == USERNAME && constantTimeEquals(password, supplied ?: "")
        }

        val readOnlyAccessor = ReadOnlySftpFileSystemAccessor()

        val factory = SftpSubsystemFactory.Builder()
            .withFileSystemAccessor(readOnlyAccessor)
            .build()
        server.subsystemFactories = listOf(factory)
        server.start()
        val publicKey = server.keyPairProvider.loadKeys().iterator().next().public
        val fingerprint = KeyUtils.getRawFingerprint(publicKey).joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(FINGERPRINT_KEY, fingerprint).apply()
        sshServer = server
    }

    private fun stopServer() {
        try {
            sshServer?.stop(true)
        } catch (_: Exception) {
        } finally {
            sshServer = null
        }
    }

    private fun getOrCreatePassword(): String {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val existing = prefs.getString(PASSWORD_KEY, null)
        if (!existing.isNullOrBlank()) return existing
        val bytes = ByteArray(18)
        SecureRandom().nextBytes(bytes)
        val generated = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        prefs.edit().putString(PASSWORD_KEY, generated).apply()
        return generated
    }

    private fun constantTimeEquals(expected: String, actual: String): Boolean {
        return MessageDigest.isEqual(expected.toByteArray(Charsets.UTF_8), actual.toByteArray(Charsets.UTF_8))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Serveur SFTP", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun notification(message: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle("SftpServer")
            .setContentText(message)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(message: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(message))
    }

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.harvey13.sftpserver.START"
        const val ACTION_STOP = "com.harvey13.sftpserver.STOP"
        const val PREFS = "sftp_server"
        const val PASSWORD_KEY = "password"
        const val FINGERPRINT_KEY = "host_fingerprint"
        const val PORT = 2222
        const val USERNAME = "backup"
        private const val CHANNEL_ID = "sftp_server"
        private const val NOTIFICATION_ID = 2222

        fun findLocalAddress(): String {
            return try {
                java.net.NetworkInterface.getNetworkInterfaces().toList()
                    .flatMap { it.inetAddresses.toList() }
                    .firstOrNull { !it.isLoopbackAddress && it is java.net.Inet4Address }
                    ?.hostAddress ?: "IP non détectée"
            } catch (_: Exception) { "IP non détectée" }
        }
    }
}
