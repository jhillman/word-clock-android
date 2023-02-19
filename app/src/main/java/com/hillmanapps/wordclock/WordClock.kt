package com.hillmanapps.wordclock

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.graphics.Color
import com.welie.blessed.*
import io.mhssn.colorpicker.ext.toHex
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.timerTask

interface WordClockListener {
    fun connectionUpdated(connected: Boolean)
    fun birthdaysUpdating(updating: Boolean)
    fun birthdaysUpdated(birthdays: List<Date>)
}

class WordClock constructor(context: Context) {
    companion object {
        private const val WORDCLOCK_ADDRESS = "A0:6C:65:CF:A0:8F"
        private const val WORDCLOCK_SERVICE_UUID = "0000ffe0-0000-1000-8000-00805f9b34fb"
        private const val WORDCLOCK_CHARACTERISTIC_UUID = "0000ffe1-0000-1000-8000-00805f9b34fb"
    }

    private var wordClockPeripheral: BluetoothPeripheral? = null
    private var wordClockCharacteristic: BluetoothGattCharacteristic? = null

    private val birthdayCountRegex: Regex = Regex("""Birthday count: (?<count>\d+)""")
    private val birthdayRegex = Regex("""Birthday (?<number>\d+)/(?<count>\d+): (?<month>\d+)/(?<day>\d+)""")

    private var birthday: Date? = null
    private var color: Color? = null
    private var birthdays = ArrayList<Date>()
    private val calendar = Calendar.getInstance()

    private val peripheralCallback: BluetoothPeripheralCallback = object: BluetoothPeripheralCallback() {
        @SuppressLint("SimpleDateFormat")
        override fun onCharacteristicUpdate(peripheral: BluetoothPeripheral, value: ByteArray?,
                                            characteristic: BluetoothGattCharacteristic, status: GattStatus) {
            super.onCharacteristicUpdate(peripheral, value, characteristic, status)

            value?.decodeToString()?.let { response ->
                if (response.startsWith("Set the date & time")) {
                    Timer().schedule(timerTask {
                        wordClockCharacteristic?.let { characteristic ->
                            wordClockPeripheral?.writeCharacteristic(
                                characteristic,
                                SimpleDateFormat("yyyy/M/d,HH:mm:ss\n").format(Date()).toByteArray(),
                                WriteType.WITHOUT_RESPONSE)
                        }
                    }, 200)
                } else if (response.startsWith("Set the color")) {
                    color?.toHex(hexPrefix = true, includeAlpha = false)?.let { color ->
                        Timer().schedule(timerTask {
                            wordClockCharacteristic?.let { characteristic ->
                                wordClockPeripheral?.writeCharacteristic(
                                    characteristic,
                                    "$color\n".toByteArray(),
                                    WriteType.WITHOUT_RESPONSE)
                            }
                        }, 200)
                    }
                } else if (response.startsWith("Enter birthday")) {
                    birthday?.let { birthday ->
                        Timer().schedule(timerTask {
                            wordClockCharacteristic?.let { characteristic ->
                                wordClockPeripheral?.writeCharacteristic(
                                    characteristic,
                                    SimpleDateFormat("M/d\n").format(birthday).toByteArray(),
                                    WriteType.WITHOUT_RESPONSE)
                            }

                            Timer().schedule(timerTask {
                                listBirthdays()
                            }, 200)
                        }, 200)
                    }
                } else {
                    birthdayCountRegex.find(response)?.let { result ->
                        result.groups["count"]?.value?.toInt()?.let { count ->
                            if (count == 0) {
                                listener?.birthdaysUpdating(false)
                            }
                        }
                    }

                    birthdayRegex.find(response)?.let { result ->
                        val number = result.groups["number"]?.value?.toInt()
                        val count = result.groups["count"]?.value?.toInt()
                        val month = result.groups["month"]?.value?.toInt()
                        val day = result.groups["day"]?.value?.toInt()

                        number?.let { count?.let { month?.let { day?.let {
                            if (number == 1) {
                                birthdays.clear()
                            }

                            calendar.set(Calendar.MONTH, month - 1)
                            calendar.set(Calendar.DAY_OF_MONTH, day)

                            birthdays.add(calendar.time)

                            if (number == count) {
                                birthdays.sort()

                                listener?.birthdaysUpdated(birthdays)
                                listener?.birthdaysUpdating(false)
                            }
                        }}}}
                    }
                }
            }
        }
    }

    private val callback: BluetoothCentralManagerCallback = object: BluetoothCentralManagerCallback() {
        override fun onDiscoveredPeripheral(peripheral: BluetoothPeripheral, scanResult: ScanResult) {
            super.onDiscoveredPeripheral(peripheral, scanResult)

            central.stopScan()
            central.connectPeripheral(peripheral, peripheralCallback)
        }

        override fun onConnectedPeripheral(peripheral: BluetoothPeripheral) {
            super.onConnectedPeripheral(peripheral)

            wordClockPeripheral = peripheral

            wordClockCharacteristic = peripheral.getCharacteristic(
                UUID.fromString(WORDCLOCK_SERVICE_UUID),
                UUID.fromString(WORDCLOCK_CHARACTERISTIC_UUID))

            wordClockCharacteristic?.let { characteristic ->
                peripheral.setNotify(characteristic, true)
            }

            listener?.connectionUpdated(true)
            listBirthdays()
        }
    }

    private val central = BluetoothCentralManager(context, callback, Handler(Looper.getMainLooper()))

    var listener: WordClockListener? = null

    fun connect() {
        central.scanForPeripheralsWithAddresses(arrayOf(WORDCLOCK_ADDRESS))
    }

    fun disconnect() {
        central.stopScan()

        wordClockPeripheral?.let { peripheral ->
            central.cancelConnection(peripheral)
            wordClockPeripheral = null
            wordClockCharacteristic = null
        }

        listener?.connectionUpdated(false)
    }

    fun setTime() {
        wordClockCharacteristic?.let { characteristic ->
            wordClockPeripheral?.writeCharacteristic(characteristic, "settime\n".toByteArray(), WriteType.WITHOUT_RESPONSE)
        }
    }

    fun setColor(color: Color) {
        this.color = color

        wordClockCharacteristic?.let { characteristic ->
            wordClockPeripheral?.writeCharacteristic(characteristic, "setcolor\n".toByteArray(), WriteType.WITHOUT_RESPONSE)
        }
    }

    fun listBirthdays() {
        listener?.birthdaysUpdating(true)

        wordClockCharacteristic?.let { characteristic ->
            wordClockPeripheral?.writeCharacteristic(characteristic, "listbdays\n".toByteArray(), WriteType.WITHOUT_RESPONSE)
        }
    }

    fun addBirthday(birthday: Date) {
        this.birthday = birthday
        listener?.birthdaysUpdating(true)

        wordClockCharacteristic?.let { characteristic ->
            wordClockPeripheral?.writeCharacteristic(characteristic, "addbday\n".toByteArray(), WriteType.WITHOUT_RESPONSE)
        }
    }

    fun removeBirthday(birthday: Date) {
        this.birthday = birthday
        listener?.birthdaysUpdating(true)

        wordClockCharacteristic?.let { characteristic ->
            wordClockPeripheral?.writeCharacteristic(characteristic, "removebday\n".toByteArray(), WriteType.WITHOUT_RESPONSE)
        }
    }
}