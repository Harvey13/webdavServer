package com.webdavshare.android.utils

import java.net.NetworkInterface
import java.util.Collections

/**
 * Utilitaires réseau.
 */
object NetworkUtils {

    /**
     * Renvoie une adresse IPv4 utilisable par un client du réseau local.
     * Les interfaces Wi-Fi/Ethernet sont prioritaires afin d'éviter d'afficher
     * l'adresse d'une interface mobile, VPN ou virtuelle.
     */
    fun getLocalIpAddress(): String {
        return try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
                .filter { it.isUp && !it.isLoopback && !it.isVirtual }

            fun addresses(preferred: Boolean): List<String> =
                interfaces
                    .filter { networkInterface ->
                        val name = networkInterface.name.lowercase()
                        val isLan = name.startsWith("wlan") ||
                            name.startsWith("wifi") ||
                            name.startsWith("eth") ||
                            name.startsWith("en")
                        isLan == preferred
                    }
                    .flatMap { networkInterface ->
                        Collections.list(networkInterface.inetAddresses)
                            .filter { address ->
                                address is java.net.Inet4Address &&
                                    !address.isLoopbackAddress &&
                                    !address.isLinkLocalAddress &&
                                    address.isSiteLocalAddress
                            }
                            .map { it.hostAddress }
                    }

            addresses(true).firstOrNull()
                ?: addresses(false).firstOrNull()
                ?: "127.0.0.1"
        } catch (_: Exception) {
            "127.0.0.1"
        }
    }
}
