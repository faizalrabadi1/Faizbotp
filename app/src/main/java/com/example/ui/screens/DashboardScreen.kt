package com.example.ui.screens

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BotJavascriptInterface(private val onPriceUpdated: (String) -> Unit) {
    @JavascriptInterface
    fun onWebSocketMessage(message: String) {
        // Debounce or filter on the Kotlin side if needed,
        // but avoid triggering Compose state on every single tick.
        if (message.contains("price", ignoreCase = true)) {
            // we don't update UI on every message to avoid ANR
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DashboardScreen() {
    var isBotRunning by remember { mutableStateOf(false) }
    var selectedStrategy by remember { mutableStateOf("RSI") }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var latestPrice by remember { mutableStateOf("جاري جلب السعر...") }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    
    val strategies = listOf("RSI", "MACD", "Moving Average", "Bollinger", "Stochastic", "CCI")
    var entryAmount by remember { mutableStateOf("1") }
    var useMartingale by remember { mutableStateOf(false) }
    var martingaleSteps by remember { mutableStateOf("3") }
    var takeProfit by remember { mutableStateOf("") }
    var stopLoss by remember { mutableStateOf("") }

    val context = LocalContext.current

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("لوحة تحكم البوت") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // WebView takes the remaining space
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.cacheMode = WebSettings.LOAD_DEFAULT
                            
                            addJavascriptInterface(BotJavascriptInterface { price ->
                                // Optional UI update debounce
                            }, "AndroidBot")
                            
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    // Inject script to monkey-patch WebSocket object
                                    val jsInject = """
                                        (function() {
                                            if (window.botWsInjected) return;
                                            window.botWsInjected = true;
                                            const OriginalWebSocket = window.WebSocket;
                                            window.WebSocket = function(url, protocols) {
                                                let ws;
                                                if (protocols) {
                                                     ws = new OriginalWebSocket(url, protocols);
                                                } else {
                                                     ws = new OriginalWebSocket(url);
                                                }
                                                
                                                ws.addEventListener('message', function(event) {
                                                    if (window.AndroidBot) {
                                                        // Send data to Kotlin
                                                        window.AndroidBot.onWebSocketMessage(event.data ? event.data.toString() : "");
                                                    }
                                                });
                                                
                                                return ws;
                                            };
                                            window.WebSocket.prototype = OriginalWebSocket.prototype;
                                            window.WebSocket.CONNECTING = OriginalWebSocket.CONNECTING;
                                            window.WebSocket.OPEN = OriginalWebSocket.OPEN;
                                            window.WebSocket.CLOSING = OriginalWebSocket.CLOSING;
                                            window.WebSocket.CLOSED = OriginalWebSocket.CLOSED;
                                            console.log('Pocket Bot WS Injected');
                                        })();
                                    """.trimIndent()
                                    view?.evaluateJavascript(jsInject, null)
                                }
                            }
                            webChromeClient = android.webkit.WebChromeClient()
                            loadUrl("https://pocketoption.com/en/cabinet/demo-high-low/")
                        }
                    },
                    update = { view ->
                        webViewRef = view
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Bottom Control Panel
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    Text(
                        text = "إعدادات البوت الاحترافية:",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "الاستراتيجية الكلاسيكية:",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(strategies) { strategy ->
                            FilterChip(
                                selected = selectedStrategy == strategy,
                                onClick = { selectedStrategy = strategy },
                                label = { Text(strategy) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = entryAmount,
                        onValueChange = { entryAmount = it },
                        label = { Text("مبلغ الدخول الأساسي ($)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp),
                        tonalElevation = 2.dp
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "تفعيل المضاعفات (Martingale)",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Switch(
                                    checked = useMartingale,
                                    onCheckedChange = { useMartingale = it }
                                )
                            }

                            if (useMartingale) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = martingaleSteps,
                                    onValueChange = { martingaleSteps = it },
                                    label = { Text("عدد خطوات المضاعفة (الحد الأقصى)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = takeProfit,
                            onValueChange = { takeProfit = it },
                            label = { Text("أخذ الربح ($)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = stopLoss,
                            onValueChange = { stopLoss = it },
                            label = { Text("وقف الخسارة ($)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            isBotRunning = !isBotRunning
                            coroutineScope.launch {
                                if (isBotRunning) {
                                    snackbarHostState.showSnackbar(
                                        message = "تم تفعيل البوت (استراتيجية: $selectedStrategy)",
                                        duration = SnackbarDuration.Short
                                    )
                                } else {
                                    snackbarHostState.showSnackbar(
                                        message = "تم إيقاف البوت",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isBotRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = if (isBotRunning) Icons.Default.Close else Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isBotRunning) "إيقاف البوت" else "بدء التداول الآلي",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}
