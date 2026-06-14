package com.gibilator.gmg.net

import com.gibilator.gmg.protocol.Const
import com.gibilator.gmg.protocol.GmgConnectionException
import com.gibilator.gmg.protocol.GmgGrillInfo
import com.gibilator.gmg.protocol.GmgProtocolException
import com.gibilator.gmg.protocol.GmgServerModeException
import com.gibilator.gmg.protocol.GmgSnapshot
import com.gibilator.gmg.protocol.Protocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

/**
 * Async UDP client for one GMG controller. Port of `api/client.py`.
 *
 * The controller is single-threaded: one request in flight at a time, guarded by
 * [mutex] (replaces the Python asyncio.Lock). Each request opens its own socket.
 */
class GmgClient(
    val host: String,
    val port: Int = Const.DEFAULT_PORT,
    private val requestTimeoutMs: Int = Const.DEFAULT_REQUEST_TIMEOUT_MS,
    private val maxRetries: Int = Const.DEFAULT_MAX_RETRIES,
) {
    init {
        require(maxRetries >= 1) { "maxRetries must be >= 1" }
        require(requestTimeoutMs > 0) { "requestTimeoutMs must be > 0" }
    }

    var serial: String? = null; private set
    var firmware: String? = null; private set
    var model: String = "Unknown"; private set
    var modelId: Int? = null; private set

    private val mutex = Mutex()

    private suspend fun request(payload: ByteArray, expectStatus: Boolean): ByteArray =
        withContext(Dispatchers.IO) {
            val addr = InetAddress.getByName(host)
            var lastExc: Exception? = null
            for (attempt in 1..maxRetries) {
                var socket: DatagramSocket? = null
                try {
                    socket = DatagramSocket().apply { soTimeout = requestTimeoutMs }
                    socket.send(DatagramPacket(payload, payload.size, addr, port))
                    val buf = ByteArray(1024)
                    val reply = DatagramPacket(buf, buf.size)
                    socket.receive(reply)
                    val data = buf.copyOf(reply.length)
                    if (expectStatus && !Protocol.isStatusFrame(data)) {
                        lastExc = GmgProtocolException("unexpected reply (${data.size} bytes)")
                        continue
                    }
                    return@withContext data
                } catch (e: SocketTimeoutException) {
                    lastExc = e
                } catch (e: GmgProtocolException) {
                    lastExc = e
                } catch (e: Exception) {
                    lastExc = e
                } finally {
                    socket?.close()
                }
            }
            when (lastExc) {
                is GmgProtocolException -> throw lastExc
                is SocketTimeoutException, null -> throw GmgServerModeException(
                    "no reply from $host:$port after $maxRetries attempts",
                    lastExc,
                )
                else -> throw GmgConnectionException("socket error talking to $host:$port: $lastExc", lastExc)
            }
        }

    /** Send `UR001!` and return the parsed snapshot. */
    suspend fun poll(): GmgSnapshot {
        val data = mutex.withLock { request(Const.CMD_STATUS, expectStatus = true) }
        val snapshot = Protocol.parseStatusFrame(data)
        modelId = snapshot.grillType
        model = Protocol.modelNameFor(snapshot.grillType)
        return snapshot
    }

    /** Identify the grill: serial, firmware, current snapshot. */
    suspend fun probe(): GmgGrillInfo {
        val (serialReply, firmwareReply, statusReply) = mutex.withLock {
            Triple(
                request(Const.CMD_SERIAL, expectStatus = false),
                request(Const.CMD_FIRMWARE, expectStatus = false),
                request(Const.CMD_STATUS, expectStatus = true),
            )
        }
        if (!serialReply.decodeToString().startsWith(Const.SERIAL_PREFIX)) {
            throw GmgProtocolException("serial reply did not start with ${Const.SERIAL_PREFIX}")
        }
        val ser = serialReply.toString(Charsets.US_ASCII).trim()
        val fw = firmwareReply.toString(Charsets.US_ASCII).trim()
        val snapshot = Protocol.parseStatusFrame(statusReply)
        serial = ser
        firmware = fw
        modelId = snapshot.grillType
        model = Protocol.modelNameFor(snapshot.grillType)
        return GmgGrillInfo(host, ser, fw, model, snapshot.grillType, snapshot)
    }

    suspend fun setGrillTemp(fahrenheit: Int) {
        val payload = Protocol.encodeSetGrillTemp(fahrenheit)
        mutex.withLock { request(payload, expectStatus = true) }
    }

    suspend fun setProbeTarget(probe: Int, fahrenheit: Int) {
        val payload = Protocol.encodeSetProbeTarget(probe, fahrenheit)
        mutex.withLock { request(payload, expectStatus = true) }
    }

    suspend fun powerOn() {
        mutex.withLock { request(Const.CMD_POWER_ON, expectStatus = true) }
    }

    suspend fun powerOff() {
        mutex.withLock { request(Const.CMD_POWER_OFF, expectStatus = true) }
    }

    suspend fun coldSmoke() {
        mutex.withLock { request(Const.CMD_COLD_SMOKE, expectStatus = true) }
    }
}
