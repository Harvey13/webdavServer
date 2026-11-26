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
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.sftp.server.SftpFileSystemAccessor
import org.apache.sshd.sftp.server.SftpSubsystemFactory
import org.apache.sshd.sftp.server.SftpSubsystemProxy
import org.apache.sshd.sftp.server.FileHandle
import org.apache.sshd.sftp.server.DirectoryHandle
import java.io.IOException
import java.nio.channels.SeekableByteChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.FileAttribute
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Set
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

        val readOnlyAccessor = object : SftpFileSystemAccessor {
            override fun resolveLocalFilePath(
                subsystem: SftpSubsystemProxy,
                rootDir: Path,
                remotePath: String
            ): Path {
                val relative = remotePath.removePrefix("/")
                val resolved = rootDir.resolve(relative).normalize()
                if (!resolved.startsWith(rootDir.normalize())) throw IOException("Chemin hors du stockage partagé.")
                var current = rootDir
                for (part in rootDir.relativize(resolved)) {
                    current = current.resolve(part)
                    if (Files.isSymbolicLink(current)) throw IOException("Les liens symboliques ne sont pas accessibles.")
                }
                return resolved
            }

            override fun openFile(
                subsystem: SftpSubsystemProxy,
                fileHandle: FileHandle,
                file: Path,
                handle: String,
                options: Set<out OpenOption>,
                vararg attrs: FileAttribute<*>
            ): SeekableByteChannel {
                if (options.any { it != StandardOpenOption.READ }) {
                    throw IOException("Serveur en lecture seule.")
                }
                return Files.newByteChannel(file, setOf(StandardOpenOption.READ), *attrs)
            }

            override fun createDirectory(subsystem: SftpSubsystemProxy, path: Path) {
                throw IOException("Serveur en lecture seule.")
            }

            override fun removeFile(subsystem: SftpSubsystemProxy, path: Path, isDirectory: Boolean) {
                throw IOException("Serveur en lecture seule.")
            }

            override fun renameFile(subsystem: SftpSubsystemProxy, oldPath: Path, newPath: Path, opts: java.util.Collection<java.nio.file.CopyOption>) {
                throw IOException("Serveur en lecture seule.")
            }

            override fun copyFile(subsystem: SftpSubsystemProxy, src: Path, dst: Path, opts: java.util.Collection<java.nio.file.CopyOption>) {
                throw IOException("Serveur en lecture seule.")
            }

            override fun createLink(subsystem: SftpSubsystemProxy, link: Path, existing: Path, symLink: Boolean) {
                throw IOException("Serveur en lecture seule.")
            }

            override fun setFileAttribute(subsystem: SftpSubsystemProxy, file: Path, view: String, attribute: String, value: Any, vararg options: LinkOption) {
                throw IOException("Serveur en lecture seule.")
            }

            override fun setFilePermissions(subsystem: SftpSubsystemProxy, file: Path, perms: Set<java.nio.file.attribute.PosixFilePermission>, vararg options: LinkOption) {
                throw IOException("Serveur en lecture seule.")
            }

            override fun setFileAccessControl(subsystem: SftpSubsystemProxy, file: Path, acl: java.util.List<java.nio.file.attribute.AclEntry>, vararg options: LinkOption) {
                throw IOException("Serveur en lecture seule.")
            }

            override fun setFileOwner(subsystem: SftpSubsystemProxy, file: Path, value: java.security.Principal, vararg options: LinkOption) {
                throw IOException("Serveur en lecture seule.")
            }

            override fun setGroupOwner(subsystem: SftpSubsystemProxy, file: Path, value: java.nio.file.attribute.GroupPrincipal, vararg options: LinkOption) {
                throw IOException("Serveur en lecture seule.")
            }
        }

        val factory = SftpSubsystemFactory.Builder()
            .withFileSystemAccessor(readOnlyAccessor)
            .build()
        server.subsystemFactories = listOf(factory)
        server.start()
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
