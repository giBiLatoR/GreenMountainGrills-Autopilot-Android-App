package com.gibilator.gmg.net

import com.gibilator.gmg.protocol.Const
import com.gibilator.gmg.protocol.DiscoveredGrill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

/**
 * Broadcast UDP discovery. Port of `api/discovery.py`.
 *
 * Sends a single `UL!` datagram to the broadcast address; every reply whose
 * payload starts with `GMG` is one grill, de-duplicated by source IP.
 *
 * Note: on some Android Wi-Fi stacks a held `WifiManager.MulticastLock` is
 * required to receive broadcast replies — the caller (repository/service)
 * acquires it around this call.
 */
object Discovery {
    suspend fun discover(
        timeoutMs: Int = Const.DEFAULT_DISCOVERY_TIMEOUT_MS,
        broadcastAddr: String = Const.DEFAULT_BROADCAST,
        port: Int = Const.DEFAULT_PORT,
    ): List<DiscoveredGrill> = withContext(Dispatchers.IO) {
        require(timeoutMs > 0) { "timeout must be > 0" }
        val socket = DatagramSocket().apply {
            this.broadcast = true
            soTimeout = 300
        }
        try {
            socket.send(
                DatagramPacket(
                    Const.CMD_SERIAL,
                    Const.CMD_SERIAL.size,
                    InetAddress.getByName(broadcastAddr),
                    port,
                ),
            )
            val seen = LinkedHashMap<String, DiscoveredGrill>()
            val deadline = System.currentTimeMillis() + timeoutMs
            val buf = ByteArray(1024)
            while (System.currentTimeMillis() < deadline) {
                try {
                    val pkt = DatagramPacket(buf, buf.size)
                    socket.receive(pkt)
                    val text = String(buf, 0, pkt.length, Charsets.US_ASCII)
                    if (text.startsWith(Const.SERIAL_PREFIX)) {
                        val ip = pkt.address.hostAddress ?: continue
                        if (!seen.containsKey(ip)) {
                            seen[ip] = DiscoveredGrill(host = ip, serial = text.trim())
                        }
                    }
                } catch (_: SocketTimeoutException) {
                    // keep looping until the deadline
                }
            }
            seen.values.toList()
        } finally {
            socket.close()
        }
    }
}
