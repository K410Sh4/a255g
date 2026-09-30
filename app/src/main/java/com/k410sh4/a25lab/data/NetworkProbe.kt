package com.k410sh4.a25lab.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.k410sh4.a25lab.model.NetworkState

class NetworkProbe(context: Context) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)

    fun snapshot(): NetworkState {
        val network = manager.activeNetwork ?: return NetworkState()
        val caps = manager.getNetworkCapabilities(network) ?: return NetworkState()
        val transports = buildList {
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add("Wi‑Fi")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add("Celular")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) add("Bluetooth")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add("Ethernet")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add("VPN")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_USB)) add("USB")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_SATELLITE)) add("Satélite")
        }
        return NetworkState(
            connected = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            transports = transports,
            downstreamKbps = caps.linkDownstreamBandwidthKbps,
            upstreamKbps = caps.linkUpstreamBandwidthKbps,
            validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
        )
    }
}
