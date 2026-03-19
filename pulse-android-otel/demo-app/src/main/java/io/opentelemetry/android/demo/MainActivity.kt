/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.demo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import android.view.ViewTreeObserver
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.pulse.android.api.otel.PulseDataCollectionConsent
import com.pulse.android.sdk.PulseSDK
import io.opentelemetry.android.demo.about.AboutActivity
import io.opentelemetry.android.demo.fragment.FragmentActivity
import io.opentelemetry.android.demo.theme.DemoAppTheme
import io.opentelemetry.android.demo.shop.ui.AstronomyShopActivity
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<DemoViewModel>()
    private var t0: Long = 0
    private var firstFrameCaptured = false
    private var frameMetricsListener: Window.OnFrameMetricsAvailableListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // t0: onCreate called
        t0 = System.currentTimeMillis()
        Log.d(TAG, "STARTUP_T0_MS=$t0")
        
        setContent {
            // Session id is often null on first frame; polling avoids crashing on error("Session ID is null").
            LaunchedEffect(Unit) {
                repeat(30) {
                    val sid = OtelDemoApplication.rum.getRumSessionId()
                    if (sid != null) {
                        viewModel.sessionIdState.value = sid
                        return@LaunchedEffect
                    }
                    delay(200L)
                }
                Log.w(TAG, "RUM session id still null after ~6s; keeping placeholder")
            }
            DemoAppTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Column(
                        modifier = Modifier.verticalScroll(state = rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        Row(
                            Modifier.padding(all = 20.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            SelectableText(
                                fontSize = 40.sp,
                                text =
                                    buildAnnotatedString {
                                        withStyle(style = SpanStyle(color = Color(0xFFF5A800))) {
                                            append("Open")
                                        }
                                        withStyle(style = SpanStyle(color = Color(0xFF425CC7))) {
                                            append("Telemetry")
                                        }
                                        withStyle(style = SpanStyle(color = Color.Black)) {
                                            append(" Android Demo")
                                        }
                                        toAnnotatedString()
                                    },
                            )
                        }
                        SessionId(viewModel.sessionIdState)
                        MainOtelButton(
                            painterResource(id = R.drawable.otel_icon),
                        )
                        val context = LocalContext.current
                        LauncherButton(
                            text = "Open Fragment activity",
                            onClick = {
                                context.startActivity(
                                    Intent(this@MainActivity, FragmentActivity::class.java).apply {
                                        putExtra(FragmentActivity.FRAGMENT_TYPE, "ListFragment")
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        LauncherButton(
                            text = "Go shopping",
                            onClick = {
                                OtelDemoApplication.logEvent("Go shopping", mapOf("shopping" to "true"))
                                context.startActivity(Intent(this@MainActivity, AstronomyShopActivity::class.java))
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        LauncherButton(
                            text = "Learn more",
                            onClick = {
                                context.startActivity(Intent(this@MainActivity, AboutActivity::class.java))
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            LauncherButton(
                                text = "Crash here",
                                onClick = {
                                    viewModel.performSomeWork()
                                },
                                modifier = Modifier.weight(1f),
                            )
                            LauncherButton(
                                text = "Trigger ANR",
                                onClick = {
                                    viewModel.triggerAnr()
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        LauncherButton(
                            text = "Network call",
                            onClick = {
                                viewModel.makeNetworkCall()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        LaunchedEffect(Unit) {
                            viewModel.networkMessage.collect { msg ->
                                if (msg != null) {
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    viewModel.clearNetworkMessage()
                                }
                            }
                        }

                        val locationPermissionLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.RequestPermission(),
                        ) { isGranted: Boolean ->
                            if (isGranted) {
                                Log.d(TAG, "Location permission granted")
                            } else {
                                Log.d(TAG, "Location permission denied")
                            }
                        }

                        LauncherButton(
                            text = "Ask location permission",
                            onClick = {
                                locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Row {
                            LauncherButton(
                                text = "Give consent",
                                onClick = {
                                    PulseSDK.INSTANCE.setDataCollectionState(PulseDataCollectionConsent.ALLOWED)
                                },
                                modifier = Modifier.wrapContentWidth(),
                            )
                            LauncherButton(
                                text = "Deny consent",
                                onClick = {
                                    PulseSDK.INSTANCE.setDataCollectionState(PulseDataCollectionConsent.DENIED)
                                },
                                modifier = Modifier.wrapContentWidth(),
                            )
                        }

                        LauncherButton(
                            text = "Open benchmark screen",
                            onClick = {
                                Intent(this@MainActivity, FragmentActivity::class.java).apply {
                                    putExtra(FragmentActivity.FRAGMENT_TYPE, "BenchmarkFragment")
                                    context.startActivity(this)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )

                    }
                }
            }
        }

        // Request the correct phone state permission based on API level
        // This permission is needed for gathering certain network information like
        // carrier name and network subtype (LTE, 4G) on certain API levels.
        val phoneStatePermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_BASIC_PHONE_STATE
        } else {
            Manifest.permission.READ_PHONE_STATE
        }

        if (ContextCompat.checkSelfPermission(this, phoneStatePermission)
            != PackageManager.PERMISSION_GRANTED
        ) {
            // Permission is not granted, request it
            ActivityCompat.requestPermissions(
                this,
                arrayOf(phoneStatePermission),
                100,
            )
        }

        // t1: first frame / first draw — FrameMetrics is API 24+ only; minSdk is 21.
        scheduleStartupT1Measurement()
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            frameMetricsListener?.let { window.removeOnFrameMetricsAvailableListener(it) }
            frameMetricsListener = null
        }
        super.onDestroy()
    }

    private fun recordStartupT1IfNeeded() {
        if (firstFrameCaptured) return
        if (isFinishing) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed) return
        firstFrameCaptured = true
        val t1 = System.currentTimeMillis()
        val totalStartupTime = t1 - t0
        Log.d(TAG, "STARTUP_T1_MS=$t1")
        Log.d(TAG, "STARTUP_TOTAL_MS=$totalStartupTime")
    }

    /**
     * Log t1 and total startup time. [Window.addOnFrameMetricsAvailableListener] exists from API 24;
     * on older APIs use first pre-draw as a proxy for first frame.
     */
    private fun scheduleStartupT1Measurement() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Post so the window/decor is ready; avoids rare crashes from registering too early.
            // API 35+: passing null Handler crashes (HardwareRendererObserver NPE: "handler and its looper cannot be null").
            val mainHandler = Handler(Looper.getMainLooper())
            window.decorView.post {
                val listener = Window.OnFrameMetricsAvailableListener { _, _, _ ->
                    recordStartupT1IfNeeded()
                }
                frameMetricsListener = listener
                window.addOnFrameMetricsAvailableListener(listener, mainHandler)
            }
        } else {
            val decor = window.decorView
            decor.viewTreeObserver.addOnPreDrawListener(
                object : ViewTreeObserver.OnPreDrawListener {
                    override fun onPreDraw(): Boolean {
                        decor.viewTreeObserver.removeOnPreDrawListener(this)
                        recordStartupT1IfNeeded()
                        return true
                    }
                },
            )
        }
    }
}
