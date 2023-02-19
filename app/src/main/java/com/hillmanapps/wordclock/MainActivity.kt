package com.hillmanapps.wordclock

import android.Manifest
import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.DatePicker
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.accompanist.flowlayout.FlowRow
import com.hillmanapps.wordclock.ui.theme.WordClockBlue
import com.hillmanapps.wordclock.ui.theme.WordClockTheme
import io.mhssn.colorpicker.*
import io.mhssn.colorpicker.ext.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.timerTask

class MainActivity : ComponentActivity() {
    private var wordClock: WordClock? = null

    private lateinit var requestBluetoothScanPermission: ActivityResultLauncher<String>
    private lateinit var requestBluetoothConnectPermission: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val wordClock = WordClock(this)
        this.wordClock = wordClock

        setContent {
            WordClockView(this, wordClock)
        }

        val permissionCallback =
            ActivityResultCallback<Boolean> { granted ->
                if (granted == true) {
                    Timer().schedule(timerTask {
                        connectWordClock()
                    }, 200)
                } else {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                    })
                }
            }

        requestBluetoothScanPermission =
            registerForActivityResult(ActivityResultContracts.RequestPermission(), permissionCallback)

        requestBluetoothConnectPermission =
            registerForActivityResult(ActivityResultContracts.RequestPermission(), permissionCallback)
    }

    override fun onResume() {
        super.onResume()

        connectWordClock()
    }

    override fun onPause() {
        super.onPause()

        wordClock?.disconnect()
    }
    
    private fun connectWordClock() {
        if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
            requestBluetoothScanPermission.launch(Manifest.permission.BLUETOOTH_SCAN)
        } else if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            requestBluetoothConnectPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            wordClock?.connect()
        }
    }
}

@SuppressLint("SimpleDateFormat")
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WordClockView(context: Context, wordClock: WordClock) {
    val timeFormat = if (DateFormat.is24HourFormat(context)) {
        SimpleDateFormat("HH:mm:ss")
    } else {
        SimpleDateFormat("h:mm:ss a")
    }

    var wordClockConnected by remember {
        mutableStateOf(false)
    }

    var wordClockBirthdaysUpdating by remember {
        mutableStateOf(false)
    }

    var time by remember {
        mutableStateOf(timeFormat.format(Date()))
    }

    LaunchedEffect(Unit) {
        while(true) {
            delay(500)
            time = timeFormat.format(Date())
        }
    }

    var color by remember {
        mutableStateOf(Color.White)
    }

    var showDialog by remember {
        mutableStateOf(false)
    }

    val dateFormat = SimpleDateFormat("MMMM d")
    var wordClockBirthdays by remember {
        mutableStateOf(listOf<Date>())
    }

    var birthday by remember {
        mutableStateOf(Date())
    }

    val calendar = Calendar.getInstance()

    var year = calendar.get(Calendar.YEAR)
    var month = calendar.get(Calendar.MONTH)
    var day = calendar.get(Calendar.DAY_OF_MONTH)

    calendar.time = Date()

    val datePicker = DatePickerDialog(
        context,
        { _: DatePicker, pickedYear: Int, pickedMonth: Int, pickedDay: Int ->
            year = pickedYear
            month = pickedMonth
            day = pickedDay

            calendar.set(Calendar.YEAR, pickedYear)
            calendar.set(Calendar.MONTH, pickedMonth)
            calendar.set(Calendar.DAY_OF_MONTH, pickedDay)
            birthday = calendar.time
        }, year, month, day
    )

    wordClock.listener = object: WordClockListener {
        override fun connectionUpdated(connected: Boolean) {
            wordClockConnected = connected
        }

        override fun birthdaysUpdating(updating: Boolean) {
            wordClockBirthdaysUpdating = updating
        }

        override fun birthdaysUpdated(birthdays: List<Date>) {
            wordClockBirthdays = birthdays
        }
    }

    WordClockTheme {
        Surface(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(16.dp)
            ) {
                Spacer(modifier = Modifier.size(8.dp))
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        text = "Word Clock connected:",
                        modifier = Modifier
                            .padding(end = 8.dp),
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Image(
                        modifier = Modifier
                            .size(24.dp, 24.dp),
                        painter = painterResource(id = if (wordClockConnected) R.drawable.ic_check else R.drawable.ic_cancel),
                        contentDescription = "Word Clock connection status",
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.secondary)
                    )
                }
                Spacer(modifier = Modifier.size(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    Text(
                        text = time,
                        fontSize = 28.sp,
                        modifier = Modifier
                            .align(Alignment.CenterVertically),
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.weight(1.0f))
                    Button(
                        enabled = wordClockConnected,
                        onClick = { wordClock.setTime() },
                        colors = ButtonDefaults.buttonColors(backgroundColor = WordClockBlue)
                    ) {
                        Row(
                            modifier = Modifier
                                .width(140.dp)
                        ) {
                            Spacer(modifier = Modifier.weight(1.0f))
                            Text(
                                text = "Set time",
                                modifier = Modifier
                                    .padding(end = 8.dp),
                                color = Color.White
                            )
                            Image(
                                modifier = Modifier
                                    .size(24.dp, 24.dp),
                                painter = painterResource(id = R.drawable.ic_clock),
                                contentDescription = "Set time",
                                colorFilter = ColorFilter.tint(Color.White)
                            )
                            Spacer(modifier = Modifier.weight(1.0f))
                        }
                    }
                }
                Spacer(modifier = Modifier.size(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    ColorPickerDialog(
                        show = showDialog,
                        type = ColorPickerType.SimpleRing(),
                        properties = DialogProperties(),
                        onDismissRequest = {
                            showDialog = false
                        },
                        onPickedColor = {
                            showDialog = false
                            color = it
                        },
                    )
                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                    ) {
                        Text(
                            text = "LED color:",
                            fontSize = 18.sp,
                            modifier = Modifier
                                .align(Alignment.CenterVertically),
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        OutlinedButton(
                            enabled = wordClockConnected,
                            onClick = {
                                showDialog = true
                            },
                            modifier = Modifier
                                .size(34.dp)
                                .align(Alignment.CenterVertically),
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(2.dp, Color.LightGray),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.weight(1.0f))
                    Button(
                        enabled = wordClockConnected,
                        onClick = { wordClock.setColor(color) },
                        colors = ButtonDefaults.buttonColors(backgroundColor = WordClockBlue)
                    ) {
                        Row(
                            modifier = Modifier
                                .width(140.dp)
                        ) {
                            Spacer(modifier = Modifier.weight(1.0f))
                            Text(
                                text = "Set color",
                                modifier = Modifier
                                    .padding(end = 8.dp),
                                color = Color.White
                            )
                            Image(
                                modifier = Modifier
                                    .size(24.dp, 24.dp),
                                painter = painterResource(id = R.drawable.ic_led),
                                contentDescription = "Set color",
                                colorFilter = ColorFilter.tint(Color.White)
                            )
                            Spacer(modifier = Modifier.weight(1.0f))
                        }
                    }
                }
                Spacer(modifier = Modifier.size(16.dp))
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally),
                ) {
                    Text(
                        text = "Birthdays",
                        fontSize = 32.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    if (wordClockBirthdaysUpdating) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(32.dp)
                                .align(Alignment.CenterVertically),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.size(8.dp))

                if (wordClockBirthdays.isEmpty()) {
                    Text(
                        text = "no birthdays",
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally),
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                FlowRow {
                    for (i in wordClockBirthdays.indices) {
                        Column {
                            Row {
                                Row(
                                    modifier = Modifier
                                        .border(
                                            border = BorderStroke(2.dp, Color.LightGray),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .padding(PaddingValues(start = 8.dp))
                                ) {
                                    Text(
                                        text = dateFormat.format(wordClockBirthdays[i]),
                                        fontSize = 14.sp,
                                        modifier = Modifier
                                            .align(Alignment.CenterVertically),
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    IconButton(
                                        enabled = wordClockConnected,
                                        onClick = { wordClock.removeBirthday(wordClockBirthdays[i]) },
                                        modifier = Modifier
                                            .then(Modifier.size(34.dp)),
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_remove),
                                            "remove",
                                            modifier = Modifier
                                                .padding(0.dp),
                                            tint = Color.Gray
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.size(8.dp))
                            }
                            Spacer(modifier = Modifier.size(8.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.size(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    TextButton(
                        enabled = wordClockConnected,
                        onClick = { datePicker.show() },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color.Transparent,
                            contentColor = WordClockBlue
                        )
                    ) {
                        Text(
                            text = dateFormat.format(birthday),
                            fontSize = 16.sp,
                            modifier = Modifier
                                .align(Alignment.CenterVertically)
                        )
                    }
                    Spacer(modifier = Modifier.weight(1.0f))
                    Button(
                        enabled = wordClockConnected,
                        onClick = { wordClock.addBirthday(birthday) },
                        colors = ButtonDefaults.buttonColors(backgroundColor = WordClockBlue)
                    ) {
                        Row(
                            modifier = Modifier
                                .width(140.dp)
                        ) {
                            Spacer(modifier = Modifier.weight(1.0f))
                            Text(
                                text = "Add birthday",
                                modifier = Modifier
                                    .padding(end = 8.dp),
                                color = Color.White
                            )
                            Image(
                                modifier = Modifier
                                    .size(24.dp, 24.dp),
                                painter = painterResource(id = R.drawable.ic_cake),
                                contentDescription = "Add birthday",
                                colorFilter = ColorFilter.tint(Color.White)
                            )
                            Spacer(modifier = Modifier.weight(1.0f))
                        }
                    }
                }
            }
        }
    }
}

fun Context.hasPermission(permissionType: String): Boolean {
    return ContextCompat.checkSelfPermission(this, permissionType) == PackageManager.PERMISSION_GRANTED
}

