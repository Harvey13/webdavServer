package com.harvey13.sftpserver

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var credentials: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    private fun render() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(32, 48, 32, 32)
        }
        layout.addView(TextView(this).apply {
            text = "SftpServer"
            textSize = 28f
        })
        layout.addView(TextView(this).apply {
            text = "Serveur SFTP local pour FoldersCopy"
            textSize = 16f
            setPadding(0, 12, 0, 28)
        })
        status = TextView(this).apply { textSize = 16f }
        layout.addView(status)
        credentials = TextView(this).apply {
            textSize = 15f
            setPadding(0, 24, 0, 24)
        }
        layout.addView(credentials)
        layout.addView(Button(this).apply {
            text = "Démarrer le serveur SFTP"
            setOnClickListener { startSftp() }
        })
        layout.addView(Button(this).apply {
            text = "Arrêter le serveur"
            setOnClickListener {
                stopService(Intent(this@MainActivity, SftpServerService::class.java))
                status.text = "Serveur arrêté"
            }
        })
        layout.addView(TextView(this).apply {
            text = "Accès en lecture seule. Le serveur expose le stockage partagé du téléphone. Gardez le téléphone et le PC sur le même réseau local."
            textSize = 13f
            setPadding(0, 28, 0, 0)
        })
        setContentView(layout)
        refreshCredentials()
    }

    private fun startSftp() {
        if (!Environment.isExternalStorageManager()) {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            status.text = "Autorisez l’accès à tous les fichiers, puis revenez ici."
            return
        }
        val intent = Intent(this, SftpServerService::class.java).setAction(SftpServerService.ACTION_START)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        status.text = "Démarrage du serveur…"
        window.decorView.postDelayed({
            status.text = "Serveur actif (si aucune erreur n’est affichée dans la notification)"
            refreshCredentials()
        }, 1200)
    }

    private fun refreshCredentials() {
        val prefs = getSharedPreferences(SftpServerService.PREFS, MODE_PRIVATE)
        val password = prefs.getString(SftpServerService.PASSWORD_KEY, "") ?: ""
        val ip = SftpServerService.findLocalAddress()
        credentials.text = "Adresse : $ip\nPort : 2222\nUtilisateur : backup\nMot de passe : $password\nDossier partagé : /"
    }
}
