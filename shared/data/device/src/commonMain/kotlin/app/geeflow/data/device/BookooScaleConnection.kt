package app.geeflow.data.device

import app.geeflow.data.device.ble.BleScanCoordinator
import app.geeflow.data.device.model.BookooScaleFrame
import dev.bluefalcon.core.BlueFalcon
import dev.bluefalcon.core.BluetoothPeripheral
import dev.bluefalcon.core.BluetoothPeripheralState
import dev.bluefalcon.core.ServiceDiscoveryPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.TimeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

data class BookooScaleDevice(val id: String, val name: String)
data class BookooScaleReading(val frame: BookooScaleFrame, val receivedAt: Instant, val rawPacketHex: String,
    val monotonicMark: TimeSource.Monotonic.ValueTimeMark = TimeSource.Monotonic.markNow())
data class BookooScalePacketEvent(val receivedAt: Instant, val packetHex: String, val decoded: Boolean)
data class BookooScaleState(
    val connected: Boolean = false,
    val device: BookooScaleDevice? = null,
    val reading: BookooScaleReading? = null,
    val lastMessage: String? = null,
    val lastRawPacketHex: String? = null,
)

interface ScaleReadingSource {
    val state: kotlinx.coroutines.flow.StateFlow<BookooScaleState>
    val packets: SharedFlow<BookooScalePacketEvent>
    val readings: Flow<BookooScaleReading> get() = state.map { it.reading }.filterNotNull().distinctUntilChanged()
}

/** Owns an independent direct GATT connection to a BOOKOO Mini or Ultra scale. */
@Single(binds = [ScaleReadingSource::class])
class BookooScaleConnection(
    private val blueFalcon: BlueFalcon,
    private val scanCoordinator: BleScanCoordinator,
    private val scope: CoroutineScope,
) : ScaleReadingSource {
    private val mutex = Mutex()
    private val mutableDevices = MutableStateFlow<List<BookooScaleDevice>>(emptyList())
    private val mutableState = MutableStateFlow(BookooScaleState())
    private val mutablePackets = MutableSharedFlow<BookooScalePacketEvent>(replay = 128, extraBufferCapacity = 256)
    private val mutableReadings = MutableSharedFlow<BookooScaleReading>(extraBufferCapacity = 256)
    private var peripheralById = emptyMap<String, BluetoothPeripheral>()
    private var scanJob: Job? = null
    private var notificationJob: Job? = null
    private var connectedPeripheral: BluetoothPeripheral? = null
    val devices = mutableDevices.asStateFlow()
    override val state = mutableState.asStateFlow()
    override val packets = mutablePackets.asSharedFlow()
    override val readings: Flow<BookooScaleReading> = mutableReadings.asSharedFlow()

    fun startScan() {
        if (scanJob?.isActive == true) return
        scanJob = scope.launch {
            scanCoordinator.scanning {
                withTimeoutOrNull(SCAN_WINDOW_MILLIS) {
                    blueFalcon.peripherals.collect { peripherals ->
                        val scales = peripherals.filter { candidateName(it) }
                        peripheralById = (peripheralById + scales.associateBy { it.uuid })
                        mutableDevices.value = scales.map { BookooScaleDevice(it.uuid, it.name ?: "BOOKOO scale") }
                    }
                }
            }
        }
    }

    suspend fun connect(id: String, ownerScope: CoroutineScope = scope) = mutex.withLock {
        disconnectLocked()
        val peripheral = peripheralById[id]
            ?: blueFalcon.peripherals.value.firstOrNull { it.uuid == id }
            ?: error("Scale is no longer visible. Scan again.")
        var serviceUpdate: Job? = null
        connectedPeripheral = peripheral
        try {
            blueFalcon.connect(peripheral)
            withTimeout(CONNECT_TIMEOUT_MILLIS) {
                while (blueFalcon.connectionState(peripheral) != BluetoothPeripheralState.Connected) delay(50)
            }
            serviceUpdate = ownerScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                blueFalcon.serviceDiscoveryUpdates.first {
                    it.peripheral.uuid == peripheral.uuid && it.phase == ServiceDiscoveryPhase.ServicesDiscovered
                }
            }
            blueFalcon.discoverServices(peripheral)
            withTimeout(CHARACTERISTIC_TIMEOUT_MILLIS) { requireNotNull(serviceUpdate).join() }
            val service = peripheral.services.firstOrNull { it.uuid.toString().endsWith(SERVICE_UUID, true) }
                ?: error("Connected scale does not expose BOOKOO service 0x0FFE")
            blueFalcon.discoverCharacteristics(peripheral, service)
            val characteristicsReady = withTimeoutOrNull(CHARACTERISTIC_TIMEOUT_MILLIS) {
                while (true) {
                    val weight = peripheral.characteristics.firstOrNull { it.uuid.toString().endsWith(WEIGHT_UUID, true) }
                    val command = peripheral.characteristics.firstOrNull { it.uuid.toString().endsWith(COMMAND_UUID, true) }
                    if (weight != null && command != null) return@withTimeoutOrNull weight
                    delay(50)
                }
                null
            } ?: error("BOOKOO weight characteristic is unavailable")
            val command = peripheral.characteristics.first { it.uuid.toString().endsWith(COMMAND_UUID, true) }
            blueFalcon.notifyCharacteristic(peripheral, characteristicsReady, notify = true)
            mutableState.value = BookooScaleState(connected = true, device = BookooScaleDevice(id, peripheral.name ?: "BOOKOO scale"))
            notificationJob = ownerScope.launch {
                blueFalcon.engine.characteristicNotifications
                    .mapNotNull { notification ->
                        if (notification.peripheral.uuid == peripheral.uuid && notification.characteristic.uuid == characteristicsReady.uuid) {
                            notification.value
                        } else null
                    }
                    .collect { bytes ->
                        val frame = BookooScaleFrame.decode(bytes)
                        val rawHex = bytes.toUpperHex()
                        mutablePackets.tryEmit(BookooScalePacketEvent(Clock.System.now(), rawHex, frame != null))
                        val reading = frame?.let { BookooScaleReading(it, Clock.System.now(), rawHex) }
                        mutableState.value = if (reading != null) {
                            state.value.copy(
                                reading = reading,
                                lastMessage = null,
                                lastRawPacketHex = rawHex,
                            )
                        } else {
                            state.value.copy(
                                lastMessage = "Ignored invalid or unsupported scale packet",
                                lastRawPacketHex = rawHex,
                            )
                        }
                        reading?.let { mutableReadings.emit(it) }
                    }
            }
            // Keep a reference for symmetry with service discovery and to make the expected UUID explicit.
            check(command.uuid.toString().endsWith(COMMAND_UUID, true))
        } catch (error: Throwable) {
            withContext(NonCancellable) {
                serviceUpdate?.cancelAndJoin()
                notificationJob?.cancelAndJoin()
                notificationJob = null
                runCatching { blueFalcon.disconnect(peripheral) }
                connectedPeripheral = null
                mutableState.value = BookooScaleState(lastMessage = error.message ?: "Scale connection failed")
            }
            throw error
        }
    }

    suspend fun disconnect() = mutex.withLock { disconnectLocked() }

    private suspend fun disconnectLocked() {
        notificationJob?.cancelAndJoin()
        notificationJob = null
        connectedPeripheral?.let { runCatching { blueFalcon.disconnect(it) } }
        connectedPeripheral = null
        mutableState.value = BookooScaleState()
    }

    private fun candidateName(peripheral: BluetoothPeripheral): Boolean {
        val name = peripheral.name ?: return false
        return name.contains("bookoo", ignoreCase = true) || name.contains("themis", ignoreCase = true)
    }

    private companion object {
        const val SERVICE_UUID = "00000ffe-0000-1000-8000-00805f9b34fb"
        const val COMMAND_UUID = "0000ff12-0000-1000-8000-00805f9b34fb"
        const val WEIGHT_UUID = "0000ff11-0000-1000-8000-00805f9b34fb"
        const val SCAN_WINDOW_MILLIS = 15_000L
        const val CONNECT_TIMEOUT_MILLIS = 10_000L
        const val CHARACTERISTIC_TIMEOUT_MILLIS = 5_000L
    }
}

private fun ByteArray.toUpperHex(): String = buildString(size * 2) {
    val digits = "0123456789ABCDEF"
    for (byte in this@toUpperHex) {
        val value = byte.toInt() and 0xff
        append(digits[value ushr 4])
        append(digits[value and 0x0f])
    }
}
