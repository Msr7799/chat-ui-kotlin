package com.example.chat_ui.ui.screens

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat_ui.R
import com.example.chat_ui.api.ChatApiClient
import com.example.chat_ui.config.ConfigManager
import com.example.chat_ui.data.firebase.FirebaseManager
import com.example.chat_ui.data.ApiProvider
import com.example.chat_ui.data.ProviderConfig
import kotlinx.coroutines.launch
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Helper functions for API testing
 */
private fun isValidHttpUrl(url: String): Boolean {
    return try {
        val uri = Uri.parse(url.trim())
        val schemeOk = uri.scheme?.lowercase() in listOf("http", "https")
        schemeOk && !uri.host.isNullOrBlank()
    } catch (_: Exception) {
        false
    }
}

private fun normalizeBackendBaseUrl(url: String): String {
    val u = url.trim().trimEnd('/')
    return if (u.endsWith("/v1")) u.removeSuffix("/v1") else u
}

private suspend fun testHuggingFace(apiKey: String, client: OkHttpClient): String {
    val req = Request.Builder()
        .url("https://huggingface.co/api/whoami-v2")
        .get()
        .addHeader("Authorization", "Bearer ${apiKey.trim()}")
        .addHeader("Accept", "application/json")
        .build()

    client.newCall(req).execute().use { res ->
        val body = res.body?.string().orEmpty()
        return when (res.code) {
            200 -> "✓ HuggingFace token is valid"
            401, 403 -> "✗ HuggingFace auth failed (HTTP ${res.code})"
            else -> "✗ HuggingFace test failed (HTTP ${res.code}): ${body.take(180)}"
        }
    }
}

private suspend fun testGoogleAiStudio(baseUrl: String, apiKey: String, client: OkHttpClient): String {
    val url = "${baseUrl.trimEnd('/')}/models?key=${apiKey.trim()}"
    val req = Request.Builder()
        .url(url)
        .get()
        .addHeader("Accept", "application/json")
        .build()

    client.newCall(req).execute().use { res ->
        val body = res.body?.string().orEmpty()
        return when (res.code) {
            200 -> "✓ Google AI Studio key is valid"
            400 -> "✗ Google AI Studio request invalid (HTTP 400). Check baseUrl/key."
            401, 403 -> "✗ Google AI Studio auth failed (HTTP ${res.code})"
            else -> "✗ Google AI Studio test failed (HTTP ${res.code}): ${body.take(180)}"
        }
    }
}



/**
 * Enhanced API Settings Screen V3
 * 
 * Features:
 * - Provider dropdown with smart alerts
 * - Auto-fill Base URL for Google Vertex AI
 * - HuggingFace token setup guide
 * - Google Sign-In integration
 * - Bilingual support (EN/AR)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiSettingsScreenV3(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme
    
    // State - initialize with empty values, will load from config in LaunchedEffect
    var selectedProvider by remember { mutableStateOf(ApiProvider.HUGGINGFACE) }
    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var defaultModel by remember { mutableStateOf("") }
    var showApiKey by remember { mutableStateOf(false) }
    var showProviderMenu by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    
    // Dialogs
    var showHuggingFaceSetup by remember { mutableStateOf(false) }
    
    // Function to load the configuration based on the selected provider state
    val loadConfigByProvider = remember<(ApiProvider) -> Unit> {
        { provider ->
            baseUrl = ConfigManager.getBaseUrlForProvider(provider)
            apiKey = ConfigManager.getApiKeyForProvider(provider)
            // Use the global model value, or the provider's default model hint
            defaultModel = ConfigManager.getDefaultModelForProvider(provider) 

            val maskedKey = if (apiKey.isBlank()) "<empty>" else "<redacted:${apiKey.length}>"
            Log.d("ApiSettingsScreenV3", "Config loaded for ${provider.name}: apiKey=$maskedKey, baseUrl=$baseUrl")
            
            // Check for HuggingFace setup hint
            if (provider == ApiProvider.HUGGINGFACE && (apiKey.isBlank() || apiKey.startsWith("hf_") && apiKey.length < 20)) {
                showHuggingFaceSetup = true
            } else {
                showHuggingFaceSetup = false
            }
        }
    }

    // Load saved configuration on screen entry (using the saved provider)
    LaunchedEffect(Unit) {
        val currentConfig = ConfigManager.getProviderConfig()
        selectedProvider = currentConfig.provider
        // Trigger loadConfigByProvider via selectedProvider state change
    }
    
    // Update base URL, API Key, and Model when provider changes
    LaunchedEffect(selectedProvider) {
        loadConfigByProvider(selectedProvider)
    }
    
    // HuggingFace Setup Dialog
    if (showHuggingFaceSetup) {
        AlertDialog(
            onDismissRequest = { showHuggingFaceSetup = false },
            icon = {
                Icon(Icons.Default.Key, contentDescription = null, tint = colorScheme.primary)
            },
            title = {
                Text(stringResource(R.string.huggingface_setup_title))
            },
            text = {
                Text(stringResource(R.string.huggingface_setup_message))
            },
            confirmButton = {
                TextButton(
                    onClick = { showHuggingFaceSetup = false }
                ) {
                    Text(stringResource(R.string.huggingface_setup_positive))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showHuggingFaceSetup = false
                        // Open HuggingFace tokens page
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.huggingface_token_url)))
                        context.startActivity(intent)
                    }
                ) {
                    Text(stringResource(R.string.huggingface_setup_negative))
                }
            }
        )
    }
    

    
    Column(modifier = Modifier.fillMaxSize().background(colorScheme.background)) {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.api_settings),
                    color = colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold
                )
            },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = colorScheme.onBackground
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = colorScheme.background)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = colorScheme.primary.copy(alpha = 0.1f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.provider_info_message),
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                }
            }

            // Provider Selection
            Column {
                Text(
                    text = stringResource(R.string.api_provider),
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                ExposedDropdownMenuBox(
                    expanded = showProviderMenu,
                    onExpandedChange = { showProviderMenu = !showProviderMenu }
                ) {
                    OutlinedTextField(
                        value = selectedProvider.displayName,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryEditable, enabled = true),
                        leadingIcon = {
                            Icon(
                                Icons.Default.CloudQueue,
                                contentDescription = null,
                                tint = colorScheme.primary
                            )
                        },
                        trailingIcon = {
                            Icon(
                                if (showProviderMenu) Icons.Default.ExpandLess 
                                else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = colorScheme.outline
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colorScheme.onSurface,
                            unfocusedTextColor = colorScheme.onSurface,
                            focusedBorderColor = colorScheme.primary,
                            unfocusedBorderColor = colorScheme.outline,
                            focusedContainerColor = colorScheme.surfaceVariant,
                            unfocusedContainerColor = colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    
                    ExposedDropdownMenu(
                        expanded = showProviderMenu,
                        onDismissRequest = { showProviderMenu = false }
                    ) {
                        ApiProvider.entries.forEach { provider ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = provider.displayName,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = when (provider) {
                                                ApiProvider.HUGGINGFACE -> stringResource(R.string.provider_huggingface_desc)
                                                ApiProvider.GOOGLE_AI_STUDIO -> "Direct API - No Backend (Gemini, Veo, Imagen)"
                                            },
                                            fontSize = 11.sp,
                                            color = colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                },
                                onClick = {
                                    // Update selectedProvider state, which triggers LaunchedEffect
                                    // to reload all config data for this provider
                                    selectedProvider = provider
                                    showProviderMenu = false
                                },
                                leadingIcon = {
                                    if (selectedProvider == provider) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = colorScheme.primary
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Base URL (read-only for Google when auto-filled)
            Column {
                Text(
                    text = stringResource(R.string.base_url),
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = false,
                    leadingIcon = {
                        Icon(Icons.Default.Link, contentDescription = null)
                    },
                    trailingIcon = {
                        // Reset button uses the provider's default URL
                        val providerDefaultUrl = selectedProvider.defaultBaseUrl
                        if (baseUrl != providerDefaultUrl) {
                            IconButton(
                                onClick = { baseUrl = providerDefaultUrl }
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Reset",
                                    tint = colorScheme.primary
                                )
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colorScheme.onSurface,
                        unfocusedTextColor = colorScheme.onSurface,
                        focusedBorderColor = colorScheme.primary,
                        unfocusedBorderColor = colorScheme.outline,
                        disabledBorderColor = colorScheme.outline.copy(alpha = 0.5f),
                        disabledTextColor = colorScheme.onSurface.copy(alpha = 0.7f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = false,
                    maxLines = 3
                )
            }

            // API Key
            Column {
                Text(
                    text = when (selectedProvider) {
                        ApiProvider.HUGGINGFACE -> "HuggingFace Token"
                        ApiProvider.GOOGLE_AI_STUDIO -> "Google AI Studio API Key"
                    },
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                when (selectedProvider) {
                                    ApiProvider.HUGGINGFACE -> "hf_..."
                                    ApiProvider.GOOGLE_AI_STUDIO -> "AIza..."
                                }
                            )
                        },
                        supportingText = {
                            if (selectedProvider == ApiProvider.GOOGLE_AI_STUDIO) {
                                Column {
                                    Text(
                                        text = stringResource(R.string.google_ai_studio_api_key_note),
                                        color = colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = stringResource(R.string.google_ai_studio_api_keys_link_label),
                                        color = colorScheme.primary,
                                        fontSize = 12.sp,
                                        modifier = Modifier.clickable {
                                            val url = context.getString(R.string.google_ai_studio_api_keys_url)
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                            context.startActivity(intent)
                                        }
                                    )
                                }
                            }
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Key, contentDescription = null)
                        },
                        trailingIcon = {
                            IconButton(onClick = { showApiKey = !showApiKey }) {
                                Icon(
                                    if (showApiKey) Icons.Default.VisibilityOff 
                                    else Icons.Default.Visibility,
                                    contentDescription = if (showApiKey) "Hide" else "Show"
                                )
                            }
                        },
                        visualTransformation = if (showApiKey) VisualTransformation.None 
                                            else PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colorScheme.onSurface,
                            unfocusedTextColor = colorScheme.onSurface,
                            focusedBorderColor = colorScheme.primary,
                            unfocusedBorderColor = colorScheme.outline
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
            }

            // Default Model
            Column {
                Text(
                    text = stringResource(R.string.default_model),
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = defaultModel,
                    onValueChange = { defaultModel = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { 
                        Text(
                            when (selectedProvider) {
                                ApiProvider.HUGGINGFACE -> "omni"
                                ApiProvider.GOOGLE_AI_STUDIO -> "gemini-2.5-flash"
                            }
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.SmartToy, contentDescription = null)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colorScheme.onSurface,
                        unfocusedTextColor = colorScheme.onSurface,
                        focusedBorderColor = colorScheme.primary,
                        unfocusedBorderColor = colorScheme.outline
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }

            // Test Result
            if (testResult != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (testResult!!.startsWith("✓"))
                            colorScheme.primaryContainer
                        else colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            if (testResult!!.startsWith("✓"))
                                Icons.Default.CheckCircle
                            else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (testResult!!.startsWith("✓"))
                                colorScheme.primary
                            else colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = testResult!!,
                            color = colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // HTTP Client for API testing
            val httpClient = remember {
                OkHttpClient.Builder()
                    .connectTimeout(6, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .callTimeout(12, TimeUnit.SECONDS)
                    .build()
            }

            var isTesting by remember { mutableStateOf(false) }

            val canTest = remember(selectedProvider, baseUrl, apiKey) {
                when (selectedProvider) {
                    ApiProvider.HUGGINGFACE -> apiKey.isNotBlank()
                    ApiProvider.GOOGLE_AI_STUDIO -> apiKey.isNotBlank() && isValidHttpUrl(baseUrl)
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            isTesting = true
                            testResult = "Testing connection..."

                            try {
                                testResult = when (selectedProvider) {
                                    ApiProvider.HUGGINGFACE ->
                                        testHuggingFace(apiKey, httpClient)

                                    ApiProvider.GOOGLE_AI_STUDIO ->
                                        testGoogleAiStudio(baseUrl, apiKey, httpClient)
                                }
                            } catch (e: Exception) {
                                testResult = "✗ Test failed: ${e.message}"
                            } finally {
                                isTesting = false
                            }
                        }
                    },
                    enabled = canTest && !isTesting,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = colorScheme.onSurface
                    )
                ) {
                    Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isTesting) "Testing..." else stringResource(R.string.test_connection),
                        color = colorScheme.onSurface
                    )
                }

                Button(
                    onClick = {
                        Log.d("ApiSettingsScreenV3", "=== SAVE BUTTON CLICKED ===")
                        Log.d("ApiSettingsScreenV3", "Provider: ${selectedProvider.name}")
                        Log.d("ApiSettingsScreenV3", "Base URL: $baseUrl")
                        Log.d("ApiSettingsScreenV3", "API Key length: ${apiKey.length}")
                        Log.d("ApiSettingsScreenV3", "Default Model: $defaultModel")

                        // Note: ConfigManager.saveProviderConfig now handles clearing the old provider's key
                        val config = ProviderConfig(
                            provider = selectedProvider,
                            baseUrl = baseUrl,
                            apiKey = apiKey,
                            useGoogleAuth = false // Always false as Vertex is removed
                        )

                        Log.d("ApiSettingsScreenV3", "Calling ConfigManager.saveProviderConfig...")
                        ConfigManager.saveProviderConfig(config)
                        Log.d("ApiSettingsScreenV3", "✓ ConfigManager.saveProviderConfig completed")

                        // Save general settings
                        ConfigManager.set(ConfigManager.Keys.DEFAULT_MODEL, defaultModel)

                        // Save provider-specific settings for model routing alias
                        when (selectedProvider) {
                            ApiProvider.GOOGLE_AI_STUDIO -> {
                                // If using Google AI Studio, the default model is the main alias
                                ConfigManager.set(ConfigManager.Keys.PUBLIC_LLM_ROUTER_ALIAS_ID, defaultModel)
                                Log.d("ApiSettingsScreenV3", "✓ Google AI Studio settings saved")
                            }
                            ApiProvider.HUGGINGFACE -> {
                                // If using HuggingFace, we usually keep the omni router alias
                                ConfigManager.set(ConfigManager.Keys.PUBLIC_LLM_ROUTER_ALIAS_ID, "omni")
                            }
                        }

                        // Verify what was saved
                        val savedConfig = ConfigManager.getProviderConfig()
                        Log.d("ApiSettingsScreenV3", "=== VERIFICATION ===")
                        Log.d("ApiSettingsScreenV3", "Saved Provider: ${savedConfig.provider.name}")
                        Log.d("ApiSettingsScreenV3", "Saved API Key length: ${savedConfig.apiKey.length}")

                        testResult = "✓ Settings saved successfully"
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.save_settings))
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}