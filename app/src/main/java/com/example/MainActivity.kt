package com.example

import android.content.Context
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import com.squareup.moshi.Moshi
import java.io.IOException
import java.util.concurrent.TimeUnit

// --- Models ---

data class Message(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: String, // "user" or "assistant"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false,
    val provider: String? = null,
    val model: String? = null,
    val latencyMs: Long? = null,
    val endpointUrl: String? = null
)

enum class ApiProvider(val displayName: String, val badge: String, val defaultEndpoint: String) {
    GEMINI("Google AI Studio", "✦ Gemini", "https://generativelanguage.googleapis.com"),
    OPEN_ROUTER("OpenRouter", "⇄ OpenRouter", "https://openrouter.ai/api/v1"),
    HUGGING_FACE("Hugging Face", "🤗 Hugging Face", "https://api-inference.huggingface.co")
}

data class ModelOption(
    val id: String,
    val displayName: String,
    val description: String,
    val provider: ApiProvider
)

val modelOptions = listOf(
    // Gemini
    ModelOption("gemini-2.5-flash", "Gemini 2.5 Flash", "Latest speed-optimized model, ideal for fast chats.", ApiProvider.GEMINI),
    ModelOption("gemini-2.5-pro", "Gemini 2.5 Pro", "Advanced complex reasoning & coding assistant.", ApiProvider.GEMINI),
    ModelOption("gemini-1.5-flash", "Gemini 1.5 Flash", "Standard production-grade model, high speed.", ApiProvider.GEMINI),
    ModelOption("gemini-1.5-pro", "Gemini 1.5 Pro", "Stable high-intelligence reasoning model.", ApiProvider.GEMINI),
    ModelOption("gemini-2.0-flash-exp", "Gemini 2.0 Flash Exp", "Next-gen experimental version of Flash.", ApiProvider.GEMINI),

    // OpenRouter
    ModelOption("google/gemini-2.5-flash:free", "Gemini 2.5 Flash (Free)", "Free Gemini access via OpenRouter hub.", ApiProvider.OPEN_ROUTER),
    ModelOption("meta-llama/llama-3.3-70b-instruct:free", "Llama 3.3 70B (Free)", "Flagship Meta open model with extreme capabilities.", ApiProvider.OPEN_ROUTER),
    ModelOption("deepseek/deepseek-chat:free", "DeepSeek V3 (Free)", "Top-tier conversational & coding assistant.", ApiProvider.OPEN_ROUTER),
    ModelOption("mistralai/mistral-7b-instruct:free", "Mistral 7B (Free)", "Lightweight, highly optimized instruction model.", ApiProvider.OPEN_ROUTER),
    ModelOption("qwen/qwen-2.5-72b-instruct:free", "Qwen 2.5 72B (Free)", "Ultra powerful multilingual & reasoning LLM.", ApiProvider.OPEN_ROUTER),

    // Hugging Face
    ModelOption("meta-llama/Llama-3.2-3B-Instruct", "Llama 3.2 3B Instruct", "Ultra-fast, compact instruction tuner.", ApiProvider.HUGGING_FACE),
    ModelOption("mistralai/Mistral-7B-Instruct-v0.3", "Mistral 7B Instruct", "Famous balanced open-weights model.", ApiProvider.HUGGING_FACE),
    ModelOption("Qwen/Qwen2.5-72B-Instruct", "Qwen 2.5 72B Instruct", "Heavy-duty serverless model on HF hub.", ApiProvider.HUGGING_FACE),
    ModelOption("microsoft/Phi-3-mini-4k-instruct", "Phi-3 Mini 4K", "Highly capable, compact semantic reasoner.", ApiProvider.HUGGING_FACE),
    ModelOption("HuggingFaceH4/zephyr-7b-beta", "Zephyr 7B Beta", "Robust conversational fine-tuned system.", ApiProvider.HUGGING_FACE)
)

// --- API Client ---

object ApiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder().build()

    fun sendRequest(
        provider: ApiProvider,
        model: String,
        prompt: String,
        apiKey: String,
        imageBase64: String? = null,
        onResponse: (success: Boolean, text: String, latencyMs: Long, endpointUrl: String, debugJson: String) -> Unit
    ) {
        val startTime = System.currentTimeMillis()
        val requestBuilder = Request.Builder()
        var url = ""
        var payloadJson = ""

        try {
            when (provider) {
                ApiProvider.GEMINI -> {
                    url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
                    payloadJson = if (imageBase64 != null) {
                        """
                        {
                          "contents": [
                            {
                              "parts": [
                                {
                                  "inline_data": {
                                    "mime_type": "image/jpeg",
                                    "data": "$imageBase64"
                                  }
                                },
                                {
                                  "text": ${escapeJsonString(prompt)}
                                }
                              ]
                            }
                          ]
                        }
                        """.trimIndent()
                    } else {
                        """
                        {
                          "contents": [
                            {
                              "parts": [
                                {
                                  "text": ${escapeJsonString(prompt)}
                                }
                              ]
                            }
                          ]
                        }
                        """.trimIndent()
                    }
                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val body = payloadJson.toRequestBody(mediaType)
                    requestBuilder.url(url).post(body)
                }
                ApiProvider.OPEN_ROUTER -> {
                    url = "https://openrouter.ai/api/v1/chat/completions"
                    payloadJson = """
                        {
                          "model": "$model",
                          "messages": [
                            {
                              "role": "user",
                              "content": ${escapeJsonString(prompt)}
                            }
                          ]
                        }
                    """.trimIndent()
                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val body = payloadJson.toRequestBody(mediaType)
                    requestBuilder.url(url)
                        .addHeader("Authorization", "Bearer $apiKey")
                        .addHeader("Content-Type", "application/json")
                        .addHeader("HTTP-Referer", "https://ai.studio/build")
                        .addHeader("X-Title", "Horizon BYOK Client")
                        .post(body)
                }
                ApiProvider.HUGGING_FACE -> {
                    url = "https://api-inference.huggingface.co/v1/chat/completions"
                    payloadJson = """
                        {
                          "model": "$model",
                          "messages": [
                            {
                              "role": "user",
                              "content": ${escapeJsonString(prompt)}
                            }
                          ]
                        }
                    """.trimIndent()
                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val body = payloadJson.toRequestBody(mediaType)
                    requestBuilder.url(url)
                        .addHeader("Authorization", "Bearer $apiKey")
                        .addHeader("Content-Type", "application/json")
                        .post(body)
                }
            }

            val request = requestBuilder.build()
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    val latency = System.currentTimeMillis() - startTime
                    onResponse(false, "Network connection error: ${e.message}\nPlease check your internet connection.", latency, url, "Error: ${e.localizedMessage}")
                }

                override fun onResponse(call: Call, response: Response) {
                    val latency = System.currentTimeMillis() - startTime
                    val responseBody = response.body?.string() ?: ""
                    val code = response.code

                    if (response.isSuccessful) {
                        try {
                            val responseText = extractText(provider, responseBody)
                            onResponse(true, responseText, latency, url, responseBody)
                        } catch (e: Exception) {
                            onResponse(false, "Failed to parse API response. Status: $code. Error: ${e.message}", latency, url, responseBody)
                        }
                    } else {
                        val parsedError = try {
                            extractError(provider, responseBody)
                        } catch (e: Exception) {
                            ""
                        }
                        val errorSuffix = if (parsedError.isNotEmpty()) "\nDetails: $parsedError" else ""
                        onResponse(
                            false,
                            "API returned HTTP error code $code.$errorSuffix",
                            latency,
                            url,
                            responseBody
                        )
                    }
                }
            })
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            onResponse(false, "Preparation error: ${e.message}", latency, url, "Exception during request setup: ${e.localizedMessage}")
        }
    }

    private fun escapeJsonString(string: String): String {
        return moshi.adapter(String::class.java).toJson(string)
    }

    private fun extractText(provider: ApiProvider, responseJson: String): String {
        return when (provider) {
            ApiProvider.GEMINI -> {
                val partsIndex = responseJson.indexOf("\"text\":")
                if (partsIndex != -1) {
                    val startQuote = responseJson.indexOf("\"", partsIndex + 7)
                    if (startQuote != -1) {
                        val endQuote = findEndQuote(responseJson, startQuote + 1)
                        if (endQuote != -1) {
                            val rawText = responseJson.substring(startQuote, endQuote + 1)
                            return moshi.adapter(String::class.java).fromJson(rawText) ?: ""
                        }
                    }
                }
                "Response format warning: Could not locate 'text' block. Full response log is available below."
            }
            ApiProvider.OPEN_ROUTER, ApiProvider.HUGGING_FACE -> {
                val contentIndex = responseJson.indexOf("\"content\":")
                if (contentIndex != -1) {
                    val startQuote = responseJson.indexOf("\"", contentIndex + 10)
                    if (startQuote != -1) {
                        val endQuote = findEndQuote(responseJson, startQuote + 1)
                        if (endQuote != -1) {
                            val rawText = responseJson.substring(startQuote, endQuote + 1)
                            return moshi.adapter(String::class.java).fromJson(rawText) ?: ""
                        }
                    }
                }
                "Response format warning: Could not locate 'content' block. Full response log is available below."
            }
        }
    }

    private fun extractError(provider: ApiProvider, responseJson: String): String {
        val errorIndex = responseJson.indexOf("\"message\":")
        if (errorIndex != -1) {
            val startQuote = responseJson.indexOf("\"", errorIndex + 10)
            if (startQuote != -1) {
                val endQuote = findEndQuote(responseJson, startQuote + 1)
                if (endQuote != -1) {
                    val rawText = responseJson.substring(startQuote, endQuote + 1)
                    return moshi.adapter(String::class.java).fromJson(rawText) ?: ""
                }
            }
        }
        val errIndex = responseJson.indexOf("\"error\":")
        if (errIndex != -1) {
            val startQuote = responseJson.indexOf("\"", errIndex + 8)
            if (startQuote != -1) {
                val endQuote = findEndQuote(responseJson, startQuote + 1)
                if (endQuote != -1) {
                    val rawText = responseJson.substring(startQuote, endQuote + 1)
                    return moshi.adapter(String::class.java).fromJson(rawText) ?: ""
                }
            }
        }
        return "Check key validity, quota or model ID constraints."
    }

    private fun findEndQuote(json: String, start: Int): Int {
        var escaped = false
        for (i in start until json.length) {
            val char = json[i]
            if (escaped) {
                escaped = false
                continue
            }
            if (char == '\\') {
                escaped = true
                continue
            }
            if (char == '"') {
                return i
            }
        }
        return -1
    }
}

// --- Key Persistence ---

class KeyManager(context: Context) {
    private val prefs = context.getSharedPreferences("horizon_prefs", Context.MODE_PRIVATE)

    fun getApiKey(provider: ApiProvider): String {
        val savedKey = prefs.getString("key_${provider.name}", "") ?: ""
        if (savedKey.isNotEmpty()) {
            return savedKey
        }
        return if (provider == ApiProvider.GEMINI) {
            com.example.BuildConfig.GEMINI_API_KEY
        } else {
            ""
        }
    }

    fun saveApiKey(provider: ApiProvider, key: String) {
        prefs.edit().putString("key_${provider.name}", key).apply()
    }

    fun clearApiKey(provider: ApiProvider) {
        prefs.edit().remove("key_${provider.name}").apply()
    }

    fun getCustomModel(provider: ApiProvider): String {
        return prefs.getString("custom_model_${provider.name}", "") ?: ""
    }

    fun saveCustomModel(provider: ApiProvider, modelName: String) {
        prefs.edit().putString("custom_model_${provider.name}", modelName).apply()
    }

    fun clearCustomModel(provider: ApiProvider) {
        prefs.edit().remove("custom_model_${provider.name}").apply()
    }

    fun isVoiceWakeEnabled(): Boolean {
        return prefs.getBoolean("voice_wake_enabled", false)
    }

    fun setVoiceWakeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("voice_wake_enabled", enabled).apply()
    }
}

// --- ViewModel ---

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val selectedProvider: ApiProvider = ApiProvider.GEMINI,
    val selectedModel: String = "gemini-2.5-flash",
    val customModelInput: String = "",
    val geminiKey: String = "",
    val openRouterKey: String = "",
    val huggingFaceKey: String = "",
    val geminiCustomModel: String = "",
    val openRouterCustomModel: String = "",
    val huggingFaceCustomModel: String = "",
    val currentRequestLog: String? = null,
    
    // Agent-specific States
    val activeTab: String = "chat", // "chat" or "agent"
    val agentGoal: String = "Open YouTube and search for Jetpack Compose",
    val isAgentRunning: Boolean = false,
    val agentLogs: List<String> = emptyList(),
    val isSimulatorMode: Boolean = true,
    val isVoiceWakeEnabled: Boolean = false,
    
    // Simulator states
    val simulatorActiveScreen: String = "HOME", // HOME, YOUTUBE, CONTACTS, ADD_CONTACT, SETTINGS, LOCKED
    val simulatorSearchQuery: String = "",
    val simulatorContactFirstName: String = "",
    val simulatorContactLastName: String = "",
    val simulatorSavedContacts: List<String> = listOf("Alice Smith", "Bob Jones"),
    val simulatorVolumeLevel: Int = 50
)

class ChatViewModel(private val keyManager: KeyManager) : ViewModel() {
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        _uiState.update { state ->
            val voiceEnabled = keyManager.isVoiceWakeEnabled()
            DeviceControlService.isVoiceWakeEnabled = voiceEnabled
            val geminiCustom = keyManager.getCustomModel(ApiProvider.GEMINI)
            val openRouterCustom = keyManager.getCustomModel(ApiProvider.OPEN_ROUTER)
            val hfCustom = keyManager.getCustomModel(ApiProvider.HUGGING_FACE)
            state.copy(
                geminiKey = keyManager.getApiKey(ApiProvider.GEMINI),
                openRouterKey = keyManager.getApiKey(ApiProvider.OPEN_ROUTER),
                huggingFaceKey = keyManager.getApiKey(ApiProvider.HUGGING_FACE),
                geminiCustomModel = geminiCustom,
                openRouterCustomModel = openRouterCustom,
                huggingFaceCustomModel = hfCustom,
                customModelInput = when (state.selectedProvider) {
                    ApiProvider.GEMINI -> geminiCustom
                    ApiProvider.OPEN_ROUTER -> openRouterCustom
                    ApiProvider.HUGGING_FACE -> hfCustom
                },
                isVoiceWakeEnabled = voiceEnabled
            )
        }
    }

    fun setVoiceWakeEnabled(enabled: Boolean, context: Context) {
        keyManager.setVoiceWakeEnabled(enabled)
        DeviceControlService.isVoiceWakeEnabled = enabled
        _uiState.update { it.copy(isVoiceWakeEnabled = enabled) }
        val statusText = if (enabled) "ENABLED" else "DISABLED"
        Toast.makeText(context, "Always-On Voice Wake $statusText. Keyword: 'Horizon'", Toast.LENGTH_SHORT).show()
    }

    fun handleIncomingVoiceCommand(command: String, context: Context) {
        val trimmed = command.trim().lowercase()
        if (!trimmed.contains("horizon")) return

        val state = _uiState.value
        if (trimmed.contains("lock")) {
            if (state.isSimulatorMode) {
                _uiState.update { it.copy(
                    simulatorActiveScreen = "LOCKED",
                    agentLogs = it.agentLogs + "🔒 [Voice Wake] Heard 'lock'. Simulating device LOCK."
                ) }
            } else {
                _uiState.update { it.copy(
                    agentLogs = it.agentLogs + "🔒 [Voice Wake] Heard 'lock'. Dispatching global LOCK screen action."
                ) }
            }
            Toast.makeText(context, "Horizon: Locking Phone", Toast.LENGTH_SHORT).show()
        } else if (trimmed.contains("unlock")) {
            if (state.isSimulatorMode) {
                _uiState.update { it.copy(
                    simulatorActiveScreen = "HOME",
                    agentLogs = it.agentLogs + "🔓 [Voice Wake] Heard 'unlock'. Simulating swipe-to-UNLOCK."
                ) }
            } else {
                _uiState.update { it.copy(
                    agentLogs = it.agentLogs + "🔓 [Voice Wake] Heard 'unlock'. Dispatching screen wake & unlock gestures."
                ) }
            }
            Toast.makeText(context, "Horizon: Unlocking Phone", Toast.LENGTH_SHORT).show()
        } else {
            val rawAction = command.substringAfter("horizon", "").trim()
            if (rawAction.isNotEmpty()) {
                _uiState.update { it.copy(
                    agentGoal = rawAction,
                    activeTab = "agent",
                    agentLogs = it.agentLogs + "🗣️ [Voice Wake] Heard: \"$command\". Starting autonomous Agent with goal: \"$rawAction\""
                ) }
                Toast.makeText(context, "Horizon Voice Trigger: \"$rawAction\"", Toast.LENGTH_LONG).show()
                startAgent(rawAction)
            }
        }
    }

    fun setActiveTab(tab: String) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun setAgentGoal(goal: String) {
        _uiState.update { it.copy(agentGoal = goal) }
    }

    fun toggleSimulatorMode(enabled: Boolean) {
        _uiState.update { it.copy(isSimulatorMode = enabled) }
    }

    fun resetSimulator() {
        _uiState.update { it.copy(
            simulatorActiveScreen = "HOME",
            simulatorSearchQuery = "",
            simulatorContactFirstName = "",
            simulatorContactLastName = "",
            simulatorSavedContacts = listOf("Alice Smith", "Bob Jones"),
            simulatorVolumeLevel = 50,
            agentLogs = listOf("Simulator reset to Home Screen.")
        ) }
    }

    fun setProvider(provider: ApiProvider) {
        _uiState.update { state ->
            val defaultModel = modelOptions.firstOrNull { it.provider == provider }?.id ?: ""
            val savedCustomModel = when (provider) {
                ApiProvider.GEMINI -> state.geminiCustomModel
                ApiProvider.OPEN_ROUTER -> state.openRouterCustomModel
                ApiProvider.HUGGING_FACE -> state.huggingFaceCustomModel
            }
            state.copy(
                selectedProvider = provider,
                selectedModel = if (savedCustomModel.isNotEmpty()) "custom" else defaultModel,
                customModelInput = savedCustomModel
            )
        }
    }

    fun setModel(modelId: String) {
        _uiState.update { it.copy(selectedModel = modelId) }
    }

    fun setCustomModelInput(customModel: String) {
        val provider = _uiState.value.selectedProvider
        keyManager.saveCustomModel(provider, customModel)
        _uiState.update { state ->
            val updatedState = state.copy(customModelInput = customModel)
            when (provider) {
                ApiProvider.GEMINI -> updatedState.copy(geminiCustomModel = customModel)
                ApiProvider.OPEN_ROUTER -> updatedState.copy(openRouterCustomModel = customModel)
                ApiProvider.HUGGING_FACE -> updatedState.copy(huggingFaceCustomModel = customModel)
            }
        }
    }

    fun updateCustomModel(provider: ApiProvider, customModel: String) {
        keyManager.saveCustomModel(provider, customModel)
        _uiState.update { state ->
            val updatedState = when (provider) {
                ApiProvider.GEMINI -> state.copy(geminiCustomModel = customModel)
                ApiProvider.OPEN_ROUTER -> state.copy(openRouterCustomModel = customModel)
                ApiProvider.HUGGING_FACE -> state.copy(huggingFaceCustomModel = customModel)
            }
            if (updatedState.selectedProvider == provider) {
                updatedState.copy(customModelInput = customModel)
            } else {
                updatedState
            }
        }
    }

    fun clearCustomModel(provider: ApiProvider) {
        keyManager.clearCustomModel(provider)
        _uiState.update { state ->
            val updatedState = when (provider) {
                ApiProvider.GEMINI -> state.copy(geminiCustomModel = "")
                ApiProvider.OPEN_ROUTER -> state.copy(openRouterCustomModel = "")
                ApiProvider.HUGGING_FACE -> state.copy(huggingFaceCustomModel = "")
            }
            if (updatedState.selectedProvider == provider) {
                updatedState.copy(customModelInput = "")
            } else {
                updatedState
            }
        }
    }

    fun updateKey(provider: ApiProvider, key: String) {
        keyManager.saveApiKey(provider, key)
        _uiState.update { state ->
            when (provider) {
                ApiProvider.GEMINI -> state.copy(geminiKey = key)
                ApiProvider.OPEN_ROUTER -> state.copy(openRouterKey = key)
                ApiProvider.HUGGING_FACE -> state.copy(huggingFaceKey = key)
            }
        }
    }

    fun clearKey(provider: ApiProvider) {
        keyManager.clearApiKey(provider)
        _uiState.update { state ->
            when (provider) {
                ApiProvider.GEMINI -> state.copy(geminiKey = "")
                ApiProvider.OPEN_ROUTER -> state.copy(openRouterKey = "")
                ApiProvider.HUGGING_FACE -> state.copy(huggingFaceKey = "")
            }
        }
    }

    fun sendMessage(prompt: String) {
        if (prompt.trim().isEmpty()) return

        val state = _uiState.value
        val activeProvider = state.selectedProvider
        val apiKey = when (activeProvider) {
            ApiProvider.GEMINI -> state.geminiKey
            ApiProvider.OPEN_ROUTER -> state.openRouterKey
            ApiProvider.HUGGING_FACE -> state.huggingFaceKey
        }

        if (apiKey.trim().isEmpty()) {
            val errorMsg = Message(
                role = "assistant",
                content = "Error: API Key is missing for ${activeProvider.displayName}. Please input your own API Key in the 'API Keys Setup' section above before generating.",
                isError = true
            )
            _uiState.update { it.copy(messages = it.messages + Message(role = "user", content = prompt) + errorMsg) }
            return
        }

        val activeModel = if (state.selectedModel == "custom") {
            if (state.customModelInput.trim().isEmpty()) {
                val errorMsg = Message(
                    role = "assistant",
                    content = "Error: 'Custom Model' is selected but model ID is blank. Please enter a valid identifier.",
                    isError = true
                )
                _uiState.update { it.copy(messages = it.messages + Message(role = "user", content = prompt) + errorMsg) }
                return
            }
            state.customModelInput.trim()
        } else {
            state.selectedModel
        }

        val userMsg = Message(role = "user", content = prompt)
        _uiState.update { it.copy(
            messages = it.messages + userMsg,
            isLoading = true,
            currentRequestLog = "Dispatching HTTP POST request to $activeModel via ${activeProvider.displayName} API..."
        ) }

        ApiClient.sendRequest(activeProvider, activeModel, prompt, apiKey) { success, responseText, latency, url, debugJson ->
            val assistantMsg = Message(
                role = "assistant",
                content = responseText,
                isError = !success,
                provider = activeProvider.displayName,
                model = activeModel,
                latencyMs = latency,
                endpointUrl = url
            )

            val log = """
                PROVIDER: ${activeProvider.displayName}
                MODEL ID: $activeModel
                HTTP STATUS: ${if (success) "200 Success" else "Error"}
                ROUND-TRIP LATENCY: ${latency}ms
                ENDPOINT URL: $url
                
                [Response Payload Content]
                $debugJson
            """.trimIndent()

            _uiState.update { it.copy(
                messages = it.messages + assistantMsg,
                isLoading = false,
                currentRequestLog = log
            ) }
        }
    }

    fun clearChat() {
        _uiState.update { it.copy(messages = emptyList(), currentRequestLog = null) }
    }

    // --- Autonomous Agent Control Loop ---

    fun startAgent(goal: String) {
        val state = _uiState.value
        if (state.isAgentRunning) return

        _uiState.update { it.copy(
            isAgentRunning = true,
            agentGoal = goal,
            agentLogs = listOf("🏁 Starting autonomous session for goal: \"$goal\"")
        ) }

        runAgentStep()
    }

    fun stopAgent() {
        _uiState.update { it.copy(isAgentRunning = false, agentLogs = it.agentLogs + "⏸️ Agent session paused by user.") }
    }

    private fun runAgentStep() {
        val state = _uiState.value
        if (!state.isAgentRunning) return

        val activeProvider = state.selectedProvider
        val apiKey = when (activeProvider) {
            ApiProvider.GEMINI -> state.geminiKey
            ApiProvider.OPEN_ROUTER -> state.openRouterKey
            ApiProvider.HUGGING_FACE -> state.huggingFaceKey
        }

        if (apiKey.trim().isEmpty()) {
            _uiState.update { it.copy(
                isAgentRunning = false,
                agentLogs = it.agentLogs + "❌ Error: API Key is missing for ${activeProvider.displayName}. Stopping loop."
            ) }
            return
        }

        val activeModel = if (state.selectedModel == "custom") {
            if (state.customModelInput.trim().isEmpty()) {
                _uiState.update { it.copy(
                    isAgentRunning = false,
                    agentLogs = it.agentLogs + "❌ Error: Custom model ID is blank. Stopping loop."
                ) }
                return
            }
            state.customModelInput.trim()
        } else {
            state.selectedModel
        }

        // Get elements on current screen
        val screenLayout = if (state.isSimulatorMode) {
            getSimulatorScreenLayout(state)
        } else {
            DeviceControlService.instance?.captureScreenLayout() 
                ?: "Screen elements empty. Ensure the 'Horizon Autonomous Controller' Accessibility Service is enabled in Settings."
        }

        // Capture a live screenshot for vision-grounded decisions. Only available on a real
        // device (not the simulator) with the Gemini provider, and only on Android 11+; falls
        // back silently to text-only mode otherwise so the agent loop is never blocked by this.
        val screenshotBase64 = if (!state.isSimulatorMode && activeProvider == ApiProvider.GEMINI) {
            DeviceControlService.instance?.captureScreenshotBase64()
        } else {
            null
        }

        val currentStepCount = state.agentLogs.filter { it.startsWith("👉") }.size + 1
        _uiState.update { it.copy(
            agentLogs = it.agentLogs + "👉 [Step $currentStepCount] Analyzing layout of screen & requesting model decision..."
        ) }

        val finalPrompt = """
            You are an advanced Android OS Autonomous Automation Agent.
            Your current high-level goal is: "${state.agentGoal}"

            ${if (screenshotBase64 != null) "A screenshot of the current screen is attached as an image. Use it to visually confirm what each element in the tree below looks like and where it sits, especially when text labels alone are ambiguous." else "Analyze the current screen layout below and output the single best NEXT action to move closer to the goal."}

            AVAILABLE ACTIONS:
            1. Click on a coordinate: `[CLICK: x, y]`
            2. Swipe: `[SWIPE: startX, startY, endX, endY, durationMs]`
            3. Type text into a field: `[TYPE: "your text"]`
            4. Global back action: `[BACK]`
            5. Global home action: `[HOME]`
            6. Wait briefly: `[WAIT: seconds]`
            7. Declare task completed: `[COMPLETE: "final status message detailing what was achieved"]`

            CURRENT SCREEN ELEMENT TREE:
            $screenLayout

            CRITICAL INSTRUCTIONS:
            - You must output exactly ONE action in your response.
            - Do not output multiple actions in one step.
            - Place the action at the very top of your response, followed by a brief 1-sentence thought explaining why you took this action.
            - For CLICK actions: you MUST copy the x, y values verbatim from the `Bounds=(x, y)` shown next to the element you are targeting in the CURRENT SCREEN ELEMENT TREE above. Never invent, estimate, guess, or round coordinates. Find the exact line for the element you want, and use its exact Bounds=(x, y) pair as-is.
            - Only target elements marked `[Clickable]` or `[Editable]`.
            - If no element in the tree matches what you need, do not guess a coordinate — instead use [WAIT: 1] or [BACK] and explain why in your thought.
        """.trimIndent()

        ApiClient.sendRequest(activeProvider, activeModel, finalPrompt, apiKey, screenshotBase64) { success, responseText, latency, url, debugJson ->
            val currentState = _uiState.value
            if (!currentState.isAgentRunning) return@sendRequest // Stopped in transition

            if (!success) {
                _uiState.update { it.copy(
                    isAgentRunning = false,
                    agentLogs = it.agentLogs + "❌ Error calling API: $responseText. Session paused."
                ) }
                return@sendRequest
            }

            val actionText = responseText.trim()
            
            var nextState = _uiState.value
            val executionLog = if (currentState.isSimulatorMode) {
                processSimulatorAction(actionText, nextState) { updatedState ->
                    nextState = updatedState
                }
            } else {
                executeRealAction(actionText)
            }

            val briefDecision = actionText.lines().firstOrNull { it.contains("[") } ?: actionText.take(80)
            val newLogs = currentState.agentLogs + 
                "🤖 AI Decision: $briefDecision" +
                "⚡ Execution: $executionLog"

            val isComplete = actionText.contains("[COMPLETE]") || actionText.contains("COMPLETE:")

            _uiState.update { 
                nextState.copy(
                    agentLogs = newLogs,
                    isAgentRunning = if (isComplete) false else nextState.isAgentRunning
                )
            }

            if (isComplete) {
                _uiState.update { it.copy(agentLogs = it.agentLogs + "🎉 Task Completed successfully!") }
            } else if (_uiState.value.isAgentRunning) {
                // Wait 3.5 seconds and trigger the next step
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    runAgentStep()
                }, 3500)
            }
        }
    }

    private fun executeRealAction(actionText: String): String {
        val service = DeviceControlService.instance
        if (service == null) {
            return "Failed to dispatch: Accessibility service is disconnected. Make sure it is enabled."
        }

        val clickMatch = Regex("\\[CLICK:\\s*(\\d+)\\s*,\\s*(\\d+)\\]").find(actionText)
        val swipeMatch = Regex("\\[SWIPE:\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\]").find(actionText)
        val typeMatch = Regex("\\[TYPE:\\s*\"([^\"]*)\"\\]").find(actionText)
        val backMatch = actionText.contains("[BACK]")
        val homeMatch = actionText.contains("[HOME]")
        val completeMatch = Regex("\\[COMPLETE:\\s*\"([^\"]*)\"\\]").find(actionText)

        return when {
            clickMatch != null -> {
                val x = clickMatch.groupValues[1].toFloat()
                val y = clickMatch.groupValues[2].toFloat()
                val ok = service.tapAt(x, y)
                if (ok) "Dispatched tap gesture at coordinates ($x, $y)." else "Tap gesture dispatch failed."
            }
            swipeMatch != null -> {
                val sx = swipeMatch.groupValues[1].toFloat()
                val sy = swipeMatch.groupValues[2].toFloat()
                val ex = swipeMatch.groupValues[3].toFloat()
                val ey = swipeMatch.groupValues[4].toFloat()
                val dur = swipeMatch.groupValues[5].toLong()
                val ok = service.swipe(sx, sy, ex, ey, dur)
                if (ok) "Dispatched swipe gesture from ($sx, $sy) to ($ex, $ey)." else "Swipe gesture dispatch failed."
            }
            typeMatch != null -> {
                // Accessibility text entry is best done with clipboard/focus or simulator.
                // We notify that typing requires direct input simulator or active focused widget typing.
                "Typed text target sequence request received: \"${typeMatch.groupValues[1]}\""
            }
            backMatch -> {
                val ok = service.triggerGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                if (ok) "Triggered global Back key action." else "Back action failed."
            }
            homeMatch -> {
                val ok = service.triggerGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                if (ok) "Triggered global Home key action." else "Home action failed."
            }
            completeMatch != null -> {
                val msg = completeMatch.groupValues[1]
                "Task marked complete by agent: \"$msg\""
            }
            else -> "Action parsed. No hardware dispatch required."
        }
    }
}

// --- Simulator Helper Functions ---

fun getSimulatorScreenLayout(state: ChatUiState): String {
    return when (state.simulatorActiveScreen) {
        "HOME" -> """
            Screen ID: HOME
            Dimensions: 1080 x 2400
            Interactive Elements:
            - <ImageView> id="app_settings" desc="Settings Icon" [Clickable] Bounds=(150, 400) Rect=(50,300,250,500)
            - <ImageView> id="app_youtube" desc="YouTube Icon" [Clickable] Bounds=(450, 400) Rect=(350,300,550,500)
            - <ImageView> id="app_contacts" desc="Contacts Icon" [Clickable] Bounds=(750, 400) Rect=(650,300,850,500)
            - <TextView> id="clock" text="10:30 AM" Bounds=(540, 150)
            - <TextView> text="Select an app icon to launch, or type a query." Bounds=(540, 800)
        """.trimIndent()
        "YOUTUBE" -> """
            Screen ID: YOUTUBE
            Dimensions: 1080 x 2400
            Interactive Elements:
            - <Button> id="nav_back" text="Back to Home" [Clickable] Bounds=(80, 150) Rect=(20,100,140,200)
            - <EditText> id="yt_search" text="${state.simulatorSearchQuery}" hint="Search YouTube" [Clickable] [Editable] Bounds=(540, 300) Rect=(150,250,930,350)
            - <Button> id="yt_search_btn" text="Search Icon" [Clickable] Bounds=(980, 300) Rect=(930,250,1030,350)
            - <TextView> text="Recommended Videos:" Bounds=(150, 500)
            ${if (state.simulatorSearchQuery.isNotEmpty()) {
                """
                - <CardView> id="video_result_1" [Clickable] Bounds=(540, 800) Rect=(50,650,1030,950)
                  - <TextView> text="Found: ${state.simulatorSearchQuery} - Complete Beginners Tutorial"
                  - <TextView> text="Jetpack Mastery · 12K views"
                """.trimIndent()
            } else {
                """
                - <CardView> id="video_1" [Clickable] Bounds=(540, 800) Rect=(50,650,1030,950)
                  - <TextView> text="Jetpack Compose Essentials in 15 Minutes"
                  - <TextView> text="Android Devs · 140K views"
                - <CardView> id="video_2" [Clickable] Bounds=(540, 1200) Rect=(50,1050,1030,1350)
                  - <TextView> text="How to build Autonomous Phone Agents"
                  - <TextView> text="Horizon Lab · 8.2K views"
                """.trimIndent()
            }}
        """.trimIndent()
        "CONTACTS" -> """
            Screen ID: CONTACTS
            Dimensions: 1080 x 2400
            Interactive Elements:
            - <Button> id="nav_back" text="Back to Home" [Clickable] Bounds=(80, 150) Rect=(20,100,140,200)
            - <Button> id="add_contact_fab" text="+ Add New Contact" [Clickable] Bounds=(540, 300) Rect=(300,250,780,350)
            - <TextView> text="Saved Contacts:" Bounds=(150, 450)
            ${state.simulatorSavedContacts.mapIndexed { idx, name ->
                "- <CardView> id=\"contact_${idx}\" [Clickable] Bounds=(540, ${600 + idx * 150}) Rect=(50,${530 + idx * 150},1030,${670 + idx * 150})\n  - <TextView> text=\"$name\""
            }.joinToString("\n")}
        """.trimIndent()
        "ADD_CONTACT" -> """
            Screen ID: ADD_CONTACT
            Dimensions: 1080 x 2400
            Interactive Elements:
            - <Button> id="nav_back" text="Back to Contacts" [Clickable] Bounds=(80, 150) Rect=(20,100,140,200)
            - <EditText> id="first_name_input" text="${state.simulatorContactFirstName}" hint="First Name" [Clickable] [Editable] Bounds=(540, 400) Rect=(150,350,930,450)
            - <EditText> id="last_name_input" text="${state.simulatorContactLastName}" hint="Last Name" [Clickable] [Editable] Bounds=(540, 600) Rect=(150,550,930,650)
            - <Button> id="btn_save" text="SAVE CONTACT" [Clickable] Bounds=(540, 850) Rect=(300,800,780,900)
            - <Button> id="btn_cancel" text="Cancel" [Clickable] Bounds=(540, 1000) Rect=(350,950,730,1050)
        """.trimIndent()
        "SETTINGS" -> """
            Screen ID: SETTINGS
            Dimensions: 1080 x 2400
            Interactive Elements:
            - <Button> id="nav_back" text="Back to Home" [Clickable] Bounds=(80, 150) Rect=(20,100,140,200)
            - <TextView> text="System Settings" Bounds=(540, 150)
            - <TextView> id="vol_label" text="Media Volume: ${state.simulatorVolumeLevel}%" Bounds=(540, 400)
            - <SeekBar> id="vol_slider" progress="${state.simulatorVolumeLevel}" hint="Slide to adjust" [Clickable] Bounds=(540, 500) Rect=(150,450,930,550)
              - Tip: Click (300, 500) for 20%, Click (540, 500) for 50%, Click (800, 500) for 80%
            - <Button> id="btn_reset_vol" text="Mute Volume" [Clickable] Bounds=(540, 700) Rect=(350,650,730,750)
        """.trimIndent()
        "LOCKED" -> """
            Screen ID: LOCKED
            Dimensions: 1080 x 2400
            Interactive Elements:
            - <TextView> id="lock_title" text="Horizon Lock Screen" Bounds=(540, 300)
            - <TextView> id="lock_subtitle" text="Device is Locked" Bounds=(540, 450)
            - <Button> id="btn_unlock" text="Swipe Up to Unlock" [Clickable] Bounds=(540, 1500) Rect=(300,1400,780,1600)
        """.trimIndent()
        else -> "Unknown screen"
    }
}

fun processSimulatorAction(actionText: String, state: ChatUiState, updateState: (ChatUiState) -> Unit): String {
    val clickMatch = Regex("\\[CLICK:\\s*(\\d+)\\s*,\\s*(\\d+)\\]").find(actionText)
    val typeMatch = Regex("\\[TYPE:\\s*\"([^\"]*)\"\\]").find(actionText)
    val backMatch = actionText.contains("[BACK]")
    val homeMatch = actionText.contains("[HOME]")
    val completeMatch = Regex("\\[COMPLETE:\\s*\"([^\"]*)\"\\]").find(actionText)

    return when {
        clickMatch != null -> {
            val x = clickMatch.groupValues[1].toInt()
            val y = clickMatch.groupValues[2].toInt()
            val logMsg = "Executed [CLICK] at coordinates ($x, $y)."
            
            when (state.simulatorActiveScreen) {
                "HOME" -> {
                    when {
                        x in 50..250 && y in 300..500 -> {
                            updateState(state.copy(simulatorActiveScreen = "SETTINGS"))
                            "$logMsg Opened Settings App."
                        }
                        x in 350..550 && y in 300..500 -> {
                            updateState(state.copy(simulatorActiveScreen = "YOUTUBE"))
                            "$logMsg Opened YouTube App."
                        }
                        x in 650..850 && y in 300..500 -> {
                            updateState(state.copy(simulatorActiveScreen = "CONTACTS"))
                            "$logMsg Opened Contacts App."
                        }
                        else -> "$logMsg Tapped empty area of Home Screen."
                    }
                }
                "YOUTUBE" -> {
                    when {
                        x in 20..140 && y in 100..200 -> {
                            updateState(state.copy(simulatorActiveScreen = "HOME"))
                            "$logMsg Returned to Home Screen."
                        }
                        x in 150..930 && y in 250..350 -> {
                            "$logMsg Focused YouTube search box."
                        }
                        x in 930..1030 && y in 250..350 -> {
                            "$logMsg Triggered YouTube Search execution."
                        }
                        else -> "$logMsg Tapped YouTube content feeds."
                    }
                }
                "CONTACTS" -> {
                    when {
                        x in 20..140 && y in 100..200 -> {
                            updateState(state.copy(simulatorActiveScreen = "HOME"))
                            "$logMsg Returned to Home Screen."
                        }
                        x in 300..780 && y in 250..350 -> {
                            updateState(state.copy(simulatorActiveScreen = "ADD_CONTACT"))
                            "$logMsg Opened Add New Contact layout Form."
                        }
                        else -> "$logMsg Selected contact row item."
                    }
                }
                "ADD_CONTACT" -> {
                    when {
                        x in 20..140 && y in 100..200 -> {
                            updateState(state.copy(simulatorActiveScreen = "CONTACTS"))
                            "$logMsg Cancelled and returned to Contacts App."
                        }
                        x in 300..780 && y in 800..900 -> {
                            val newName = "${state.simulatorContactFirstName} ${state.simulatorContactLastName}".trim()
                            val finalName = if (newName.isEmpty()) "New Contact User" else newName
                            val updatedContacts = state.simulatorSavedContacts + finalName
                            updateState(state.copy(
                                simulatorActiveScreen = "CONTACTS",
                                simulatorSavedContacts = updatedContacts,
                                simulatorContactFirstName = "",
                                simulatorContactLastName = ""
                            ))
                            "$logMsg Successfully saved and created contact: '$finalName'."
                        }
                        x in 350..730 && y in 950..1050 -> {
                            updateState(state.copy(
                                simulatorActiveScreen = "CONTACTS",
                                simulatorContactFirstName = "",
                                simulatorContactLastName = ""
                            ))
                            "$logMsg Dismissed contact editor layout."
                        }
                        else -> logMsg
                    }
                }
                "SETTINGS" -> {
                    when {
                        x in 20..140 && y in 100..200 -> {
                            updateState(state.copy(simulatorActiveScreen = "HOME"))
                            "$logMsg Returned to Home Screen."
                        }
                        x in 150..930 && y in 450..550 -> {
                            val percent = (((x - 150).toFloat() / 780f) * 100).toInt().coerceIn(0, 100)
                            updateState(state.copy(simulatorVolumeLevel = percent))
                            "$logMsg Set volume seekbar level to $percent%."
                        }
                        x in 350..730 && y in 650..750 -> {
                            updateState(state.copy(simulatorVolumeLevel = 0))
                            "$logMsg Muted media audio stream."
                        }
                        else -> logMsg
                    }
                }
                "LOCKED" -> {
                    when {
                        x in 300..780 && y in 1400..1600 -> {
                            updateState(state.copy(simulatorActiveScreen = "HOME"))
                            "$logMsg Clicked 'Swipe Up to Unlock' button. Unlocked device."
                        }
                        else -> "$logMsg Tapped lock screen."
                    }
                }
                else -> logMsg
            }
        }
        typeMatch != null -> {
            val typedText = typeMatch.groupValues[1]
            val logMsg = "Executed [TYPE] \"$typedText\""
            
            when (state.simulatorActiveScreen) {
                "YOUTUBE" -> {
                    updateState(state.copy(simulatorSearchQuery = typedText))
                    "$logMsg as YouTube search query input."
                }
                "ADD_CONTACT" -> {
                    if (state.simulatorContactFirstName.isEmpty()) {
                        updateState(state.copy(simulatorContactFirstName = typedText))
                        "$logMsg as First Name entry."
                    } else {
                        updateState(state.copy(simulatorContactLastName = typedText))
                        "$logMsg as Last Name entry."
                    }
                }
                else -> "$logMsg into active edit form field."
            }
        }
        backMatch -> {
            val current = state.simulatorActiveScreen
            if (current == "LOCKED") {
                "Executed [BACK] global command action. Ignored since device is locked."
            } else if (current != "HOME") {
                val next = if (current == "ADD_CONTACT") "CONTACTS" else "HOME"
                updateState(state.copy(simulatorActiveScreen = next))
                "Executed [BACK] global command action. Backwards to $next."
            } else {
                "Executed [BACK] global command action. Already at root."
            }
        }
        homeMatch -> {
            val current = state.simulatorActiveScreen
            if (current == "LOCKED") {
                "Executed [HOME] command action. Ignored since device is locked."
            } else if (current != "HOME") {
                updateState(state.copy(simulatorActiveScreen = "HOME"))
                "Executed [HOME] command action. Returned to root desktop launcher."
            } else {
                "Executed [HOME] command action. Already at Home."
            }
        }
        completeMatch != null -> {
            val completionMsg = completeMatch.groupValues[1]
            "Executed [COMPLETE]. Goal completed: \"$completionMsg\""
        }
        else -> "Executed generic layout analysis step."
    }
}


// --- Main UI Activity ---

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val keyManager = KeyManager(applicationContext)

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    BYOKPlaygroundScreen(
                        keyManager = keyManager,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun BYOKPlaygroundScreen(
    keyManager: KeyManager,
    modifier: Modifier = Modifier
) {
    val viewModel: ChatViewModel = viewModel { ChatViewModel(keyManager) }
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current

    var settingsExpanded by remember { mutableStateOf(false) }
    var inputPrompt by remember { mutableStateOf("") }
    val lazyListState = rememberLazyListState()

    LaunchedEffect(Unit) {
        VoiceTriggerManager.onVoiceCommandReceived = { command ->
            viewModel.handleIncomingVoiceCommand(command, context)
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = results?.firstOrNull() ?: ""
            if (spokenText.isNotEmpty()) {
                inputPrompt = spokenText
            }
        }
    }

    // Key visibility state
    var showGeminiKey by remember { mutableStateOf(false) }
    var showOpenRouterKey by remember { mutableStateOf(false) }
    var showHFKey by remember { mutableStateOf(false) }

    // Scroll to latest message automatically
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            lazyListState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // App Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Horizon",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "BYOK Multi-API Phone Agent",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Quick Toggle Config
            IconButton(
                onClick = { settingsExpanded = !settingsExpanded },
                modifier = Modifier
                    .background(
                        color = if (settingsExpanded) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .testTag("settings_toggle")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "API Keys Configuration",
                    tint = if (settingsExpanded) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)

        // Expandable Settings Drawer
        AnimatedVisibility(
            visible = settingsExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "🔧 Configure Your Keys (Stored Locally)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // 1. Google AI Studio Key & Custom Model
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Google AI Studio (Gemini)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = uiState.geminiKey,
                                onValueChange = { viewModel.updateKey(ApiProvider.GEMINI, it) },
                                placeholder = { Text("AI Studio key (AIzaSy...)") },
                                textStyle = MaterialTheme.typography.bodySmall,
                                visualTransformation = if (showGeminiKey) VisualTransformation.None else PasswordVisualTransformation(),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("gemini_key_input"),
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(onClick = { showGeminiKey = !showGeminiKey }) {
                                        Icon(
                                            imageVector = if (showGeminiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Toggle Visibility"
                                        )
                                    }
                                }
                            )
                            if (uiState.geminiKey.isNotEmpty()) {
                                IconButton(
                                    onClick = { viewModel.clearKey(ApiProvider.GEMINI) },
                                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Clear")
                                }
                            }
                        }
                        OutlinedTextField(
                            value = uiState.geminiCustomModel,
                            onValueChange = { viewModel.updateCustomModel(ApiProvider.GEMINI, it) },
                            placeholder = { Text("Default: gemini-2.5-flash") },
                            label = { Text("Custom Model Override", fontSize = 10.sp) },
                            textStyle = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("gemini_custom_model_settings_input"),
                            singleLine = true,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Build,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingIcon = {
                                if (uiState.geminiCustomModel.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.clearCustomModel(ApiProvider.GEMINI) }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear Custom Model")
                                    }
                                }
                            }
                        )
                    }

                    // 2. OpenRouter Key & Custom Model
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("OpenRouter Key", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = uiState.openRouterKey,
                                onValueChange = { viewModel.updateKey(ApiProvider.OPEN_ROUTER, it) },
                                placeholder = { Text("OpenRouter key (sk-or-v1...)") },
                                textStyle = MaterialTheme.typography.bodySmall,
                                visualTransformation = if (showOpenRouterKey) VisualTransformation.None else PasswordVisualTransformation(),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("openrouter_key_input"),
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(onClick = { showOpenRouterKey = !showOpenRouterKey }) {
                                        Icon(
                                            imageVector = if (showOpenRouterKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Toggle Visibility"
                                        )
                                    }
                                }
                            )
                            if (uiState.openRouterKey.isNotEmpty()) {
                                IconButton(
                                    onClick = { viewModel.clearKey(ApiProvider.OPEN_ROUTER) },
                                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Clear")
                                }
                            }
                        }
                        OutlinedTextField(
                            value = uiState.openRouterCustomModel,
                            onValueChange = { viewModel.updateCustomModel(ApiProvider.OPEN_ROUTER, it) },
                            placeholder = { Text("Default: google/gemini-2.5-flash:free") },
                            label = { Text("Custom Model Override", fontSize = 10.sp) },
                            textStyle = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("openrouter_custom_model_settings_input"),
                            singleLine = true,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Build,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingIcon = {
                                if (uiState.openRouterCustomModel.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.clearCustomModel(ApiProvider.OPEN_ROUTER) }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear Custom Model")
                                    }
                                }
                            }
                        )
                    }

                    // 3. Hugging Face Access Token & Custom Model
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Hugging Face Access Token", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = uiState.huggingFaceKey,
                                onValueChange = { viewModel.updateKey(ApiProvider.HUGGING_FACE, it) },
                                placeholder = { Text("Hugging Face user access token (hf_...)") },
                                textStyle = MaterialTheme.typography.bodySmall,
                                visualTransformation = if (showHFKey) VisualTransformation.None else PasswordVisualTransformation(),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("huggingface_key_input"),
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(onClick = { showHFKey = !showHFKey }) {
                                        Icon(
                                            imageVector = if (showHFKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Toggle Visibility"
                                        )
                                    }
                                }
                            )
                            if (uiState.huggingFaceKey.isNotEmpty()) {
                                IconButton(
                                    onClick = { viewModel.clearKey(ApiProvider.HUGGING_FACE) },
                                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Clear")
                                }
                            }
                        }
                        OutlinedTextField(
                            value = uiState.huggingFaceCustomModel,
                            onValueChange = { viewModel.updateCustomModel(ApiProvider.HUGGING_FACE, it) },
                            placeholder = { Text("Default: meta-llama/Llama-3.2-3B-Instruct") },
                            label = { Text("Custom Model Override", fontSize = 10.sp) },
                            textStyle = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("huggingface_custom_model_settings_input"),
                            singleLine = true,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Build,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingIcon = {
                                if (uiState.huggingFaceCustomModel.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.clearCustomModel(ApiProvider.HUGGING_FACE) }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear Custom Model")
                                    }
                                }
                            }
                        )
                    }

                    Button(
                        onClick = { settingsExpanded = false },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Done Configurations")
                    }
                }
            }
        }

        // Provider Selector Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Active API Provider",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ApiProvider.values().forEach { provider ->
                    val isSelected = uiState.selectedProvider == provider
                    val hasKey = when (provider) {
                        ApiProvider.GEMINI -> uiState.geminiKey.isNotEmpty()
                        ApiProvider.OPEN_ROUTER -> uiState.openRouterKey.isNotEmpty()
                        ApiProvider.HUGGING_FACE -> uiState.huggingFaceKey.isNotEmpty()
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                            .border(
                                width = 1.5.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { viewModel.setProvider(provider) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = provider.badge,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(
                                            color = if (hasKey) Color(0xFF4CAF50) else Color(0xFFFF5722),
                                            shape = RoundedCornerShape(50)
                                        )
                                )
                                Text(
                                    text = if (hasKey) "Key Configured" else "No Key Found",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Model Selector Dropdown & Custom Field
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val providerModels = modelOptions.filter { it.provider == uiState.selectedProvider }
            var dropdownExpanded by remember { mutableStateOf(false) }
            val selectedModelOption = providerModels.find { it.id == uiState.selectedModel }

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = selectedModelOption?.displayName ?: if (uiState.customModelInput.isNotEmpty()) "Custom: ${uiState.customModelInput}" else "Custom Model Input...",
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { dropdownExpanded = true }
                        .testTag("model_selector_field"),
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        IconButton(onClick = { dropdownExpanded = true }) {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Open dropdown list")
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                DropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    providerModels.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(option.displayName, fontWeight = FontWeight.Bold)
                                    Text(option.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            onClick = {
                                viewModel.setModel(option.id)
                                dropdownExpanded = false
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text("Custom Model Specified...", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text("Enter any external model ID manually.", style = MaterialTheme.typography.bodySmall)
                            }
                        },
                        onClick = {
                            viewModel.setModel("custom")
                            dropdownExpanded = false
                        }
                    )
                }
            }

            // Animated custom model input if custom is selected
            AnimatedVisibility(
                visible = uiState.selectedModel == "custom",
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                OutlinedTextField(
                    value = uiState.customModelInput,
                    onValueChange = { viewModel.setCustomModelInput(it) },
                    placeholder = {
                        val helperHint = when (uiState.selectedProvider) {
                            ApiProvider.GEMINI -> "e.g. gemini-2.0-flash"
                            ApiProvider.OPEN_ROUTER -> "e.g. liquid/lfm-40b:free"
                            ApiProvider.HUGGING_FACE -> "e.g. deepseek-ai/DeepSeek-R1-Distill-Qwen-1.5B"
                        }
                        Text("Custom identifier ($helperHint)")
                    },
                    label = { Text("Manual Model ID String") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                        .testTag("custom_model_input_field"),
                    singleLine = true
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Tab Selector Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), shape = RoundedCornerShape(10.dp))
                .padding(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (uiState.activeTab == "chat") MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { viewModel.setActiveTab("chat") }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (uiState.activeTab == "chat") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Chat Playground",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (uiState.activeTab == "chat") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (uiState.activeTab == "agent") MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { viewModel.setActiveTab("agent") }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (uiState.activeTab == "agent") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Phone Control Agent",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (uiState.activeTab == "agent") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // MAIN TAB CONTENT BODY
        if (uiState.activeTab == "chat") {
            // Messages Box / Play Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                if (uiState.messages.isEmpty()) {
                    // Beautiful Empty Onboarding Screen
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        item {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Welcome to Horizon",
                                modifier = Modifier
                                    .size(64.dp)
                                    .padding(bottom = 12.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Horizon Playground",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Connect to major free-tier open models with Bring Your Own Key (BYOK) privacy controls.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        "✨ Simple Set-up Guide:",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    StepItem(1, "Tap the gear icon ⚙️ in the top right.")
                                    StepItem(2, "Enter your free key for Gemini, OpenRouter, or Hugging Face.")
                                    StepItem(3, "Select your active provider, select a free model, and type your prompt!")
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                "Try Suggestion Starters:",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start
                            )
                        }

                        val suggestions = listOf(
                            "Explain quantum mechanics simply in 3 bullet points.",
                            "Write a Kotlin function to calculate Fibonacci numbers.",
                            "Draft a friendly email suggesting a project review call."
                        )

                        items(suggestions) { suggestion ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { inputPrompt = suggestion },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Terminal, contentDescription = "Prompt suggestion", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = suggestion,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Messaging Conversation List
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(uiState.messages) { msg ->
                            MessageBubble(
                                message = msg,
                                onCopy = {
                                    clipboardManager.setText(AnnotatedString(msg.content))
                                    Toast.makeText(context, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }

            // Streaming/Metrics collapse card
            if (uiState.currentRequestLog != null) {
                var logDetailsExpanded by remember { mutableStateOf(false) }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                          modifier = Modifier
                              .fillMaxWidth()
                              .clickable { logDetailsExpanded = !logDetailsExpanded },
                          horizontalArrangement = Arrangement.SpaceBetween,
                          verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Terminal, contentDescription = "Logs", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "📊 Live API Request Inspector",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            Icon(
                                imageVector = if (logDetailsExpanded) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Expand Log details",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }

                        if (logDetailsExpanded) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = uiState.currentRequestLog ?: "",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    .padding(8.dp)
                            )
                        }
                    }
                }
            }

            // Active Loading Bar
            if (uiState.isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp)
                        .testTag("api_progress_bar")
                )
            }

            // Bottom Controls Send Area
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = { viewModel.clearChat() },
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .size(52.dp)
                        .testTag("clear_chat_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear Chat History",
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }

                OutlinedTextField(
                    value = inputPrompt,
                    onValueChange = { inputPrompt = it },
                    placeholder = { Text("Ask Horizon anything...") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("prompt_text_field"),
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (inputPrompt.trim().isNotEmpty() && !uiState.isLoading) {
                            viewModel.sendMessage(inputPrompt)
                            inputPrompt = ""
                            focusManager.clearFocus()
                        }
                    }),
                    singleLine = false,
                    maxLines = 4,
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault())
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your command...")
                                }
                                try {
                                    speechLauncher.launch(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Speech recognition is not supported on this device.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Voice Command Input",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )

                val canSend = inputPrompt.trim().isNotEmpty() && !uiState.isLoading
                IconButton(
                    onClick = {
                        if (canSend) {
                            viewModel.sendMessage(inputPrompt)
                            inputPrompt = ""
                            focusManager.clearFocus()
                        }
                    },
                    modifier = Modifier
                        .background(
                            color = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .size(52.dp)
                        .testTag("send_button"),
                    enabled = canSend
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Submit prompt",
                        tint = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        } else {
            // PHONE CONTROL AGENT PANEL VIEW
            PhoneControlAgentPanel(
                uiState = uiState,
                viewModel = viewModel,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun PhoneControlAgentPanel(
    uiState: ChatUiState,
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isRealEnabled = DeviceControlService.isEnabled()
    val scrollState = rememberLazyListState()

    val agentSpeechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = results?.firstOrNull() ?: ""
            if (spokenText.isNotEmpty()) {
                viewModel.setAgentGoal(spokenText)
            }
        }
    }

    val voicePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.setVoiceWakeEnabled(true, context)
        } else {
            Toast.makeText(context, "Microphone permission is required for Always-On Voice Wake.", Toast.LENGTH_LONG).show()
        }
    }

    // Keep logs scrolled down
    LaunchedEffect(uiState.agentLogs.size) {
        if (uiState.agentLogs.isNotEmpty()) {
            scrollState.animateScrollToItem(uiState.agentLogs.size - 1)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // 1. Connection & Mode Status Bar
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (uiState.isSimulatorMode) 
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                    else 
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "⚙️ Execution Environment",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        
                        // Simulator vs Real Switch
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("Simulator", style = MaterialTheme.typography.bodySmall, fontWeight = if (uiState.isSimulatorMode) FontWeight.Bold else FontWeight.Normal)
                            Switch(
                                checked = !uiState.isSimulatorMode,
                                onCheckedChange = { viewModel.toggleSimulatorMode(!it) },
                                modifier = Modifier.scale(0.7f)
                            )
                            Text("Real OS", style = MaterialTheme.typography.bodySmall, fontWeight = if (!uiState.isSimulatorMode) FontWeight.Bold else FontWeight.Normal)
                        }
                    }

                    if (!uiState.isSimulatorMode) {
                        // Real mode connection banner
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(if (isRealEnabled) Color(0xFF4CAF50) else Color(0xFFFF9800), shape = RoundedCornerShape(50))
                            )
                            Text(
                                text = if (isRealEnabled) "Horizon Accessibility Service is ACTIVE" else "Accessibility Permission required",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            if (!isRealEnabled) {
                                Button(
                                    onClick = {
                                        try {
                                            val intent = android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Could not open settings", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Authorize", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    } else {
                        // Simulator active banner
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(Color(0xFF2196F3), shape = RoundedCornerShape(50))
                            )
                            Text(
                                text = "Running Sandbox Simulation",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = { viewModel.resetSimulator() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Reset", modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reset Sandbox", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Always-on voice wake",
                                tint = if (uiState.isVoiceWakeEnabled) MaterialTheme.colorScheme.primary else Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    "Background Voice Activation (Optional)",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Locked-screen trigger word: \"horizon unlock my phone\".",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                            }
                        }
                        Switch(
                            checked = uiState.isVoiceWakeEnabled,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                        context,
                                        android.Manifest.permission.RECORD_AUDIO
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                    
                                    if (hasPermission) {
                                        viewModel.setVoiceWakeEnabled(true, context)
                                    } else {
                                        voicePermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                    }
                                } else {
                                    viewModel.setVoiceWakeEnabled(false, context)
                                }
                            },
                            modifier = Modifier.scale(0.7f)
                        )
                    }
                }
            }
        }

        // 2. Visual Phone Simulator Screen (SANDBOX DEVICE PREVIEW)
        if (uiState.isSimulatorMode) {
            item {
                Text(
                    text = "📱 Interactive Simulator Device View",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                
                // Rounded device container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(24.dp))
                        .border(4.dp, Color(0xFF333333), shape = RoundedCornerShape(24.dp))
                        .padding(bottom = 8.dp)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Top status bar notch representation
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(30.dp)
                                .background(Color(0xFF121212))
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("10:30 AM", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            // Small Speaker Notch Capsule
                            Box(
                                modifier = Modifier
                                    .width(70.dp)
                                    .height(14.dp)
                                    .background(Color.Black, shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                            )
                            // Network indicators
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Wifi, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = Color.Green, modifier = Modifier.size(12.dp))
                            }
                        }

                        // Simulator App Area based on activeState
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(Color(0xFF1C1B1F))
                                .padding(12.dp)
                        ) {
                            when (uiState.simulatorActiveScreen) {
                                "HOME" -> {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.SpaceAround
                                    ) {
                                        // App Icons Grid Row
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceEvenly
                                        ) {
                                            // Settings Icon
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier.clickable { viewModel.startAgent("Goal: Go to settings and mute the volume") }
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(52.dp)
                                                        .background(Color(0xFF555555), shape = RoundedCornerShape(14.dp)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White, modifier = Modifier.size(28.dp))
                                                }
                                                Text("Settings", color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                                            }

                                            // YouTube Icon
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(52.dp)
                                                        .background(Color(0xFFE53935), shape = RoundedCornerShape(14.dp)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Default.PlayArrow, contentDescription = "YouTube", tint = Color.White, modifier = Modifier.size(28.dp))
                                                }
                                                Text("YouTube", color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                                            }

                                            // Contacts Icon
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(52.dp)
                                                        .background(Color(0xFF1E88E5), shape = RoundedCornerShape(14.dp)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Default.Contacts, contentDescription = "Contacts", tint = Color.White, modifier = Modifier.size(28.dp))
                                                }
                                                Text("Contacts", color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                                            }
                                        }

                                        Text(
                                            "Double-tap above app icons or launch an Agent to autonomously control settings, contacts list, and browser pipelines.",
                                            color = Color.Gray,
                                            fontSize = 9.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(horizontal = 12.dp)
                                        )
                                    }
                                }
                                "YOUTUBE" -> {
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        // Header
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Red, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("YouTube Premium", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        
                                        // Virtual input box
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(28.dp)
                                                .background(Color(0xFF2D2D2D), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 8.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                                Text(
                                                    text = if (uiState.simulatorSearchQuery.isEmpty()) "Search videos..." else uiState.simulatorSearchQuery,
                                                    color = if (uiState.simulatorSearchQuery.isEmpty()) Color.DarkGray else Color.White,
                                                    fontSize = 10.sp
                                                )
                                                Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(12.dp))
                                            }
                                        }
                                        
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Feed Video Results:", color = Color.LightGray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        
                                        // Video items
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF262626))
                                        ) {
                                            Row(modifier = Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Box(modifier = Modifier.size(36.dp).background(Color.DarkGray))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Column {
                                                    Text(
                                                        text = if (uiState.simulatorSearchQuery.isNotEmpty()) "Found: ${uiState.simulatorSearchQuery} Crash Course" else "Build Autonomous Phone Agents",
                                                        color = Color.White,
                                                        fontSize = 9.sp,
                                                        maxLines = 1
                                                    )
                                                    Text("Horizon AI Labs · 14K views", color = Color.Gray, fontSize = 8.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                                "CONTACTS" -> {
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("📖 My Contacts", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Box(
                                                modifier = Modifier
                                                    .background(Color(0xFF1E88E5), shape = RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text("+ Add", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        
                                        // Contact List Scroll mockup
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            uiState.simulatorSavedContacts.forEach { contactName ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(Color(0xFF2A2A2A), RoundedCornerShape(4.dp))
                                                        .padding(6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(modifier = Modifier.size(16.dp).background(Color.Gray, RoundedCornerShape(50)))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(contactName, color = Color.White, fontSize = 10.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                                "ADD_CONTACT" -> {
                                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("👤 Create Contact Form", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        
                                        // Field 1
                                        Column {
                                            Text("First Name", color = Color.Gray, fontSize = 8.sp)
                                            Box(modifier = Modifier.fillMaxWidth().height(26.dp).background(Color(0xFF2C2C2C), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp), contentAlignment = Alignment.CenterStart) {
                                                Text(uiState.simulatorContactFirstName.ifEmpty { "Enter text..." }, color = if (uiState.simulatorContactFirstName.isEmpty()) Color.DarkGray else Color.White, fontSize = 9.sp)
                                            }
                                        }
                                        
                                        // Field 2
                                        Column {
                                            Text("Last Name", color = Color.Gray, fontSize = 8.sp)
                                            Box(modifier = Modifier.fillMaxWidth().height(26.dp).background(Color(0xFF2C2C2C), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp), contentAlignment = Alignment.CenterStart) {
                                                Text(uiState.simulatorContactLastName.ifEmpty { "Enter text..." }, color = if (uiState.simulatorContactLastName.isEmpty()) Color.DarkGray else Color.White, fontSize = 9.sp)
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(modifier = Modifier.weight(1f).height(24.dp).background(Color(0xFF4CAF50), RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                                                Text("SAVE", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Box(modifier = Modifier.weight(1f).height(24.dp).background(Color(0xFF555555), RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                                                Text("CANCEL", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                                "SETTINGS" -> {
                                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("⚙️ Device Settings", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        
                                        Column {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Media Audio Volume", color = Color.LightGray, fontSize = 9.sp)
                                                Text("${uiState.simulatorVolumeLevel}%", color = Color.Green, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                            }
                                            
                                            // Sliders progress visual
                                            Box(modifier = Modifier.fillMaxWidth().height(8.dp).background(Color.DarkGray, RoundedCornerShape(50))) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth(uiState.simulatorVolumeLevel / 100f)
                                                        .fillMaxHeight()
                                                        .background(Color(0xFF4CAF50), RoundedCornerShape(50))
                                                )
                                            }
                                        }

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(28.dp)
                                                .background(Color(0xFFD32F2F), RoundedCornerShape(4.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("MUTE VOLUME", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                "LOCKED" -> {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(top = 20.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Lock,
                                                contentDescription = "Device Locked",
                                                tint = Color.White,
                                                modifier = Modifier.size(32.dp)
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                "Device Locked",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                "Say \"Horizon unlock my phone\" to unlock",
                                                color = Color.Gray,
                                                fontSize = 8.sp,
                                                textAlign = TextAlign.Center
                                            )
                                        }

                                        if (uiState.isVoiceWakeEnabled) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier
                                                    .background(Color(0xFF1B5E20), RoundedCornerShape(12.dp))
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .background(Color.Green, RoundedCornerShape(50))
                                                )
                                                Text(
                                                    "Horizon is Listening...",
                                                    color = Color.Green,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        } else {
                                            Text(
                                                "Voice Wake is Disabled",
                                                color = Color.DarkGray,
                                                fontSize = 8.sp
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                viewModel.handleIncomingVoiceCommand("horizon unlock my phone", context)
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.15f)),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 12.dp),
                                            contentPadding = PaddingValues(vertical = 4.dp)
                                        ) {
                                            Text(
                                                "Swipe Up to Unlock",
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Bottom Global Action Pill Bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(30.dp)
                                .background(Color(0xFF121212)),
                            contentAlignment = Alignment.Center
                        ) {
                            // Home pill button
                            Box(
                                modifier = Modifier
                                    .width(80.dp)
                                    .height(4.dp)
                                    .background(Color.White, shape = RoundedCornerShape(50))
                            )
                        }
                    }
                }
            }
        }

        if (uiState.isSimulatorMode) {
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "🗣️ Voice Trigger Simulation Sandbox",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            "Simulate vocal inputs directly. Works whether the simulated phone is locked or unlocked, provided 'Background Voice Activation' is enabled.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = {
                                    VoiceTriggerManager.triggerCommand("horizon lock my phone")
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(28.dp).weight(1f)
                            ) {
                                Text("🔒 Speak: Lock Phone", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    VoiceTriggerManager.triggerCommand("horizon unlock my phone")
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(28.dp).weight(1f)
                            ) {
                                Text("🔓 Speak: Unlock Phone", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = {
                                    VoiceTriggerManager.triggerCommand("horizon search kittens on youtube")
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(28.dp).fillMaxWidth()
                            ) {
                                Text("📺 Speak: \"horizon search kittens on youtube\"", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        var customVoicePhrase by remember { mutableStateOf("") }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = customVoicePhrase,
                                onValueChange = { customVoicePhrase = it },
                                placeholder = { Text("Or speak: e.g. horizon search cat videos", fontSize = 9.sp) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp)
                            )
                            Button(
                                onClick = {
                                    if (customVoicePhrase.isNotEmpty()) {
                                        val phrase = if (customVoicePhrase.lowercase().contains("horizon")) customVoicePhrase else "horizon $customVoicePhrase"
                                        VoiceTriggerManager.triggerCommand(phrase)
                                        customVoicePhrase = ""
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(36.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Text("Simulate", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // 3. Goal Specification panel
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "🎯 Automation Task Target",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = uiState.agentGoal,
                        onValueChange = { viewModel.setAgentGoal(it) },
                        placeholder = { Text("What do you want the AI to execute on this phone?") },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 2,
                        enabled = !uiState.isAgentRunning,
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault())
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your automation goal...")
                                    }
                                    try {
                                        agentSpeechLauncher.launch(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Speech recognition is not supported on this device.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = !uiState.isAgentRunning
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Voice Input automation goal",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    )

                    // Quick suggestion pill rows
                    Text("Select a demo scenario task:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val goals = listOf(
                            "Add Bob Ross to contacts",
                            "Mute system volume",
                            "Search Kotlin on YouTube"
                        )
                        goals.forEach { g ->
                            Box(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(50.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50.dp))
                                    .clickable { if (!uiState.isAgentRunning) viewModel.setAgentGoal(g) }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(g, fontSize = 9.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Session runners
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (uiState.isAgentRunning) {
                            Button(
                                onClick = { viewModel.stopAgent() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = "Stop", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pause Agent Session")
                            }
                        } else {
                            Button(
                                onClick = { viewModel.startAgent(uiState.agentGoal) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Play", modifier = Modifier.size(16.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Run Autonomous Loop", color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        // 4. Step-by-Step logs
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "📃 Agent Execution Action Logs",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (uiState.isAgentRunning) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                }
            }
        }

        if (uiState.agentLogs.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No execution sessions active. Press 'Run Autonomous Loop' above to trigger an AI agent automation cycle.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(uiState.agentLogs) { log ->
                val isExecution = log.startsWith("⚡")
                val isDecision = log.startsWith("🤖")
                val isError = log.contains("Error") || log.contains("❌")
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            isError -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                            isExecution -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                            isDecision -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        }
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = log,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = if (isExecution || isDecision) FontFamily.Monospace else FontFamily.Default,
                            color = when {
                                isError -> MaterialTheme.colorScheme.error
                                isExecution -> MaterialTheme.colorScheme.primary
                                isDecision -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StepItem(step: Int, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(16.dp)
                .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(50)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = step.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun MessageBubble(
    message: Message,
    onCopy: () -> Unit
) {
    val isUser = message.role == "user"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // Label header showing model & metrics if applicable
        if (!isUser) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(bottom = 2.dp, start = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "${message.provider ?: "AI"} · ${message.model ?: "LLM"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold
                )
                if (message.latencyMs != null) {
                    Text(
                        text = "(${message.latencyMs}ms)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (!isUser) {
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier
                        .size(28.dp)
                        .padding(end = 4.dp, top = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy message content",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 16.dp
                        )
                    )
                    .background(
                        color = when {
                            isUser -> MaterialTheme.colorScheme.primary
                            message.isError -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .widthIn(max = 290.dp)
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        isUser -> MaterialTheme.colorScheme.onPrimary
                        message.isError -> MaterialTheme.colorScheme.onErrorContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

