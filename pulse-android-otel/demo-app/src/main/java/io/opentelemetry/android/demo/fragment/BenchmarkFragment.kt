package io.opentelemetry.android.demo.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import com.pulse.android.sdk.PulseSDK
import io.opentelemetry.android.demo.SelectableText
import io.opentelemetry.android.demo.LauncherButton
import io.opentelemetry.android.demo.theme.DemoAppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

class BenchmarkFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return ComposeView(inflater.context, null).apply {
            setContent {
                DemoAppTheme {
                    BenchMarkScreen()
                }
            }
        }
    }
}

private const val RPS_VALUE_TEXT = "100"
private const val LOG_COUNT_VALUE_TEXT = "10000"

@Composable
fun BenchMarkScreen() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        val scope = rememberCoroutineScope()
        var threadsCountText by remember { mutableStateOf("5") }
        var logsCountText by remember { mutableStateOf(LOG_COUNT_VALUE_TEXT) }
        var rpsText by remember { mutableStateOf(RPS_VALUE_TEXT) }
        var isRunning by remember { mutableStateOf(false) }
        var elapsedMs by remember { mutableStateOf(0L) }
        var startTime by remember { mutableStateOf(0L) }

        Column(
            modifier = Modifier.verticalScroll(state = rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            SelectableText(
                fontSize = 40.sp,
                text =
                    buildAnnotatedString {
                        withStyle(style = SpanStyle(color = Color(0xFFF5A800))) {
                            append("Benchmark")
                        }
                        toAnnotatedString()
                    },
            )

            OutlinedTextField(
                value = threadsCountText,
                onValueChange = { threadsCountText = it.filter { ch -> ch.isDigit() } },
                label = { Text("Number of threads") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(75.dp),
            )

            OutlinedTextField(
                value = logsCountText,
                onValueChange = { logsCountText = it.filter { ch -> ch.isDigit() } },
                label = { Text("Total request count") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(75.dp),
            )

            OutlinedTextField(
                value = rpsText,
                onValueChange = { rpsText = it.filter { ch -> ch.isDigit() } },
                label = { Text("Events per second (RPS)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(75.dp),
            )

            LauncherButton(
                text = if (isRunning) "Running..." else "Start",
                onClick = {
                    if (isRunning) return@LauncherButton
                    val threads = threadsCountText.toIntOrNull()?.takeIf { it > 0 } ?: 5
                    val totalLogs = logsCountText.toLongOrNull()?.takeIf { it > 0L } ?: LOG_COUNT_VALUE_TEXT.toLong()
                    val rps = rpsText.toIntOrNull()?.takeIf { it > 0 } ?: RPS_VALUE_TEXT.toInt()
                    isRunning = true
                    startTime = System.currentTimeMillis()
                    elapsedMs = 0L
                    launchBenchmark(scope, threads, totalLogs, rps) {
                        isRunning = false
                        elapsedMs = 0L
                    }
                },
                enabled = !isRunning,
            )

            LauncherButton(
                text = "Cancel",
                onClick = {
                    canelBenchmark()
                },
                enabled = isRunning,
            )

            LaunchedEffect(startTime) {
                if (startTime > 0) {
                    while (isRunning) {
                        elapsedMs = System.currentTimeMillis() - startTime
                        delay(10) // Update every 10ms for smooth display
                    }
                }
            }

            if (isRunning) {
                Text(
                    text = "Emitting events ($elapsedMs ms passed)... Please wait.",
                    color = Color(0xFF425CC7),
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
    }
}

private var job: Job? = null
private fun launchBenchmark(
    scope: CoroutineScope,
    threads: Int,
    totalLogs: Long,
    rps: Int,
    onComplete: () -> Unit,
) {
    val fixedPool = Executors.newFixedThreadPool(threads.coerceAtLeast(1))
    val dispatcher = fixedPool.asCoroutineDispatcher()
    job = scope.launch(Dispatchers.Default) {
        try {
            coroutineScope {
                val firedEvent = MutableStateFlow(0)
                val safeThreads = threads.coerceAtLeast(1)
                val base = totalLogs / safeThreads
                val remainder = (totalLogs % safeThreads).toInt()
                
                // Calculate delay between events to achieve desired RPS
                // If rps is 0 or negative, fire as fast as possible (no delay)
                val delayMs = if (rps > 0) {
                    // Total events per second across all threads = rps
                    // Delay per event = 1000ms / (rps / threads) = 1000 * threads / rps
                    (1000.0 * safeThreads / rps).toLong().coerceAtLeast(1)
                } else {
                    0L
                }
                
                repeat(safeThreads) { threadIndex ->
                    val share = base + if (threadIndex < remainder) 1 else 0
                    launch(dispatcher) {
                        var eventIndex = 0L
                        while (eventIndex < share) {
                            PulseSDK.INSTANCE.trackEvent(
                                "benchmark_event",
                                System.currentTimeMillis(),
                                mapOf(
                                    "benchMarkThreadIndex" to threadIndex,
                                    "benchMarkEventIndex" to eventIndex.toInt() + 1,
                                    "benchMarkTotalLogs" to totalLogs.toInt(),
                                    "benchMarkFiredEvent" to firedEvent.updateAndGet { it + 1 }.also { /*Log.d("benchmark", "firedEvent: $it")*/ }
                                ),
                            )
                            eventIndex++
                            
                            // Add delay to control RPS
                            if (delayMs > 0) {
                                delay(delayMs)
                            }
                        }
                    }
                }
            }
            PulseSDK.INSTANCE.trackEvent(
                "benchmark_completed",
                System.currentTimeMillis(),
            )
        } finally {
            dispatcher.close()
            fixedPool.shutdown()
            onComplete()
        }
    }
}

private fun canelBenchmark() {
    job?.cancel()
    job = null
}
