package com.example.chat_ui.debug

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat_ui.data.ApiProvider
import com.example.chat_ui.config.ConfigManager
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

// Persistence
private const val DEBUG_PREFS = "debug_console_prefs"
private const val KEY_LAST_DEBUG_JSON = "last_debug_json"

enum class TestMode {
    BACKEND,      // Test via Backend proxy
    DIRECT_API    // Test directly with Google AI Studio API
}

// Unified result for both test modes
data class UnifiedTestResult(
    val modelId: String,
    val ok: Boolean,
    val output: String,
    val details: String = ""
)

private data class PersistedDebug(
    val mode: TestMode,
    val statusMessage: String,
    val modelCount: Int,
    val results: List<UnifiedTestResult>
)

private fun buildDebugJson(
    mode: TestMode,
    statusMessage: String,
    modelCount: Int,
    results: List<UnifiedTestResult>
): String {
    val root = JSONObject()
    root.put("timestampMs", System.currentTimeMillis())
    root.put("mode", mode.name)
    root.put("statusMessage", statusMessage)
    root.put("modelCount", modelCount)

    val arr = JSONArray()
    results.forEach { r ->
        val o = JSONObject()
        o.put("modelId", r.modelId)
        o.put("ok", r.ok)
        o.put("output", r.output)
        o.put("details", r.details)
        arr.put(o)
    }
    root.put("results", arr)

    // Pretty JSON
    return root.toString(2)
}

private fun saveLastDebugJson(context: Context, json: String) {
    context.getSharedPreferences(DEBUG_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_LAST_DEBUG_JSON, json)
        .apply()
}

private fun loadLastDebugJson(context: Context): String? {
    return context.getSharedPreferences(DEBUG_PREFS, Context.MODE_PRIVATE)
        .getString(KEY_LAST_DEBUG_JSON, null)
}

private fun parsePersistedDebug(json: String): PersistedDebug? {
    return try {
        val root = JSONObject(json)
        val modeStr = root.optString("mode", TestMode.DIRECT_API.name)
        val mode = runCatching { TestMode.valueOf(modeStr) }.getOrDefault(TestMode.DIRECT_API)

        val statusMessage = root.optString("statusMessage", "Ready to test")
        val modelCount = root.optInt("modelCount", 0)

        val arr = root.optJSONArray("results") ?: JSONArray()
        val out = ArrayList<UnifiedTestResult>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                UnifiedTestResult(
                    modelId = o.optString("modelId", ""),
                    ok = o.optBoolean("ok", false),
                    output = o.optString("output", ""),
                    details = o.optString("details", "")
                )
            )
        }

        PersistedDebug(
            mode = mode,
            statusMessage = statusMessage,
            modelCount = modelCount,
            results = out
        )
    } catch (_: Exception) {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    onBack: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var testMode by remember { mutableStateOf(TestMode.DIRECT_API) }
    var isRunning by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<UnifiedTestResult>>(emptyList()) }
    var statusMessage by remember { mutableStateOf("Ready to test") }
    var modelCount by remember { mutableStateOf(0) }

    // Restore last debug on first open
    LaunchedEffect(Unit) {
        val saved = loadLastDebugJson(context)
        if (!saved.isNullOrBlank()) {
            val parsed = parsePersistedDebug(saved)
            if (parsed != null) {
                testMode = parsed.mode
                statusMessage = parsed.statusMessage
                modelCount = parsed.modelCount
                results = parsed.results
            }
        }
    }

    // Get API key from settings
    val apiKey = remember {
        ConfigManager.getProviderConfig().apiKey
    }

    val backendBaseUrl = remember {
        ConfigManager.getBaseUrlForProvider(ApiProvider.HUGGINGFACE)
    }

    val backendTester = remember(backendBaseUrl) {
        BackendChatMultiModelTester(
            baseUrl = backendBaseUrl,
            tokenProvider = { FirebaseIdTokenProvider.getIdToken(forceRefresh = true) }
        )
    }

    val directTester = remember(apiKey) {
        DirectGeminiApiTester(apiKey)
    }

    fun persistNow() {
        val json = buildDebugJson(testMode, statusMessage, modelCount, results)
        saveLastDebugJson(context, json)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.BugReport,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Debug Console")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Test Mode Selector
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Test Mode",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Direct API Button
                        FilterChip(
                            selected = testMode == TestMode.DIRECT_API,
                            onClick = { testMode = TestMode.DIRECT_API },
                            label = { Text("Direct API") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Key,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                        // Backend Button
                        FilterChip(
                            selected = testMode == TestMode.BACKEND,
                            onClick = { testMode = TestMode.BACKEND },
                            label = { Text("Backend") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Cloud,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (testMode == TestMode.DIRECT_API)
                            "Google AI Studio API (FREE Check)"
                        else
                            "Backend Multi-Model Tester",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (testMode == TestMode.DIRECT_API)
                            "generativelanguage.googleapis.com"
                        else
                            backendBaseUrl,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (modelCount > 0) {
                        Text(
                            text = "Models: $modelCount",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Run Test Button
            Button(
                onClick = {
                    scope.launch {
                        isRunning = true
                        results = emptyList()

                        try {
                            if (testMode == TestMode.DIRECT_API) {
                                statusMessage = "Checking models availability..."

                                if (apiKey.isBlank()) {
                                    statusMessage = "API Key not set. Go to Settings > API Settings."
                                    isRunning = false
                                    persistNow()
                                    return@launch
                                }

                                val modelInfos = directTester.testAllGoogleModels()
                                modelCount = modelInfos.size

                                results = modelInfos.map { info ->
                                    UnifiedTestResult(
                                        modelId = info.id,
                                        ok = info.available,
                                        output = if (info.available) {
                                            "Available | ${info.displayName}"
                                        } else {
                                            info.error ?: "Not available"
                                        },
                                        details = if (info.available) {
                                            "Input: ${info.inputTokenLimit} | Output: ${info.outputTokenLimit}\n" +
                                                    "Methods: ${info.supportedMethods.joinToString(", ")}"
                                        } else ""
                                    )
                                }

                                val okCount = results.count { it.ok }
                                val failCount = results.count { !it.ok }
                                statusMessage = "Done: $okCount Available, $failCount Not Found"
                                persistNow()

                            } else {
                                statusMessage = "Fetching models from backend..."

                                val modelIds = backendTester.fetchModelIds()
                                modelCount = modelIds.size
                                statusMessage = "Testing ${modelIds.size} models..."

                                val testResults = backendTester.runMultiModelTest()
                                results = testResults.map { r ->
                                    UnifiedTestResult(
                                        modelId = r.modelId,
                                        ok = r.ok,
                                        output = r.output
                                    )
                                }

                                val okCount = results.count { it.ok }
                                val failCount = results.count { !it.ok }
                                statusMessage = "Done: $okCount OK, $failCount Failed"
                                persistNow()
                            }

                        } catch (e: Exception) {
                            Log.e("DebugScreen", "Test failed", e)
                            statusMessage = "Error: ${e.message}"
                            persistNow()
                        } finally {
                            isRunning = false
                        }
                    }
                },
                enabled = !isRunning,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Testing...")
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (testMode == TestMode.DIRECT_API)
                            "Check Model Availability"
                        else
                            "Run Multi-Model Test"
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Copy JSON Button
            OutlinedButton(
                onClick = {
                    val json = buildDebugJson(testMode, statusMessage, modelCount, results)
                    clipboard.setText(AnnotatedString(json))
                    saveLastDebugJson(context, json)
                    statusMessage = "Copied JSON to clipboard."
                },
                enabled = results.isNotEmpty() && !isRunning,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Copy JSON",
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Results List
            if (results.isNotEmpty()) {
                Text(
                    text = "Results",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(results) { result ->
                        UnifiedResultCard(result)
                    }
                }
            }
        }
    }
}

@Composable
private fun UnifiedResultCard(result: UnifiedTestResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (result.ok)
                Color(0xFF1B5E20).copy(alpha = 0.1f)
            else
                Color(0xFFB71C1C).copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = if (result.ok) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                tint = if (result.ok) Color(0xFF4CAF50) else Color(0xFFF44336),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.modelId,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = result.output.take(200) + if (result.output.length > 200) "..." else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
                if (result.details.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = result.details,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}
