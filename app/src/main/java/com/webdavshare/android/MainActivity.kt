package com.webdavshare.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.webdavshare.android.databinding.ActivityMainBinding
import com.webdavshare.android.service.WebDAVServerService
import com.webdavshare.android.utils.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isServerRunning = false

    // Pour demander les permissions de stockage
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            // Toutes les permissions ont été accordées
            showToast("Permissions accordées")
        } else {
            showToast("Les permissions sont nécessaires pour le fonctionnement de l'application")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViews()
        checkPermissions()
    }

    private fun setupViews() {
        // Initialiser l'interface utilisateur
        updateServerStatusUI(false)

        // Configurer le bouton de démarrage/arrêt du serveur
        binding.toggleServerButton.setOnClickListener {
            if (isServerRunning) {
                stopWebDAVServer()
            } else {
                startWebDAVServer()
            }
        }

        // Afficher l'information sur l'accès complet au stockage
        binding.sharedFolderTextView.text = getString(R.string.shared_folder, "Stockage complet du téléphone")
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+: Demander MANAGE_EXTERNAL_STORAGE
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        } else {
            // Android 10 et inférieur: Demander READ/WRITE_EXTERNAL_STORAGE
            val permissions = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            
            if (!permissions.all { 
                    ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED 
                }) {
                requestPermissionLauncher.launch(permissions)
            }
        }
    }

    private fun startWebDAVServer() {
        if (!hasStoragePermission()) {
            showToast("Autorise l'accès aux fichiers avant de démarrer le serveur")
            checkPermissions()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val ipAddress = NetworkUtils.getLocalIpAddress()
                val port = 8080 // Port par défaut
                
                val serviceIntent = Intent(this@MainActivity, WebDAVServerService::class.java).apply {
                    putExtra("PORT", port)
                }
                
                ContextCompat.startForegroundService(this@MainActivity, serviceIntent)
                
                // Mettre à jour l'interface
                launch(Dispatchers.Main) {
                    updateServerStatusUI(true, ipAddress, port)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(Dispatchers.Main) {
                    showToast(getString(R.string.error_starting_server, e.message))
                }
            }
        }
    }

    private fun stopWebDAVServer() {
        val serviceIntent = Intent(this, WebDAVServerService::class.java)
        stopService(serviceIntent)
        updateServerStatusUI(false)
    }

    private fun updateServerStatusUI(isRunning: Boolean, ipAddress: String? = null, port: Int = 8080) {
        isServerRunning = isRunning
        
        if (isRunning) {
            binding.serverStatusTextView.text = getString(R.string.server_status, getString(R.string.server_running))
            binding.serverStatusTextView.setTextColor(ContextCompat.getColor(this, R.color.status_running))
            binding.toggleServerButton.text = getString(R.string.stop_server)
            
            binding.ipAddressTextView.text = getString(R.string.ip_address, ipAddress)
            binding.portTextView.text = getString(R.string.port, port.toString())
            binding.webdavUrlTextView.text = getString(R.string.webdav_url, "http://$ipAddress:$port")
            
            binding.ipAddressTextView.visibility = View.VISIBLE
            binding.portTextView.visibility = View.VISIBLE
            binding.webdavUrlTextView.visibility = View.VISIBLE
        } else {
            binding.serverStatusTextView.text = getString(R.string.server_status, getString(R.string.server_stopped))
            binding.serverStatusTextView.setTextColor(ContextCompat.getColor(this, R.color.status_stopped))
            binding.toggleServerButton.text = getString(R.string.start_server)
            
            binding.ipAddressTextView.visibility = View.GONE
            binding.portTextView.visibility = View.GONE
            binding.webdavUrlTextView.visibility = View.GONE
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isServerRunning) {
            stopWebDAVServer()
        }
    }
}
