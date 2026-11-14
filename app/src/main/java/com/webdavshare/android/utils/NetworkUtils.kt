package com.webdavshare.android.utils

import java.net.NetworkInterface
import java.util.*

/**
 * Utilitaires pour obtenir des informations réseau
 */
object NetworkUtils {
    
    /**
     * Obtient l'adresse IP locale de l'appareil
     *
     * @return L'adresse IP locale ou "127.0.0.1" si non trouvée
     */
    fun getLocalIpAddress(): String {
        try {
            val networkInterfaces = NetworkInterface.getNetworkInterfaces()
            while (networkInterfaces.hasMoreElements()) {
                val networkInterface = networkInterfaces.nextElement()
                
                // Filtrer les interfaces non actives
                if (!networkInterface.isUp) continue
                
                // Ignorer les interfaces virtuelles et loopback
                if (networkInterface.isLoopback || networkInterface.isVirtual) continue
                
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    
                    // Ignorer les adresses IPv6 et les adresses de loopback
                    if (address.isLoopbackAddress || address.hostAddress.contains(":")) continue
                    
                    return address.hostAddress
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return "127.0.0.1" // Adresse de repli si aucune adresse n'est trouvée
    }
}
