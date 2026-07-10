package com.example

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.content.Context
import android.widget.Toast

object VoiceTriggerManager {
    var onVoiceCommandReceived: ((String) -> Unit)? = null

    fun triggerCommand(command: String) {
        onVoiceCommandReceived?.invoke(command)
    }
}

class DeviceControlService : AccessibilityService() {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        var instance: DeviceControlService? = null
            private set

        fun isEnabled(): Boolean = instance != null

        var isVoiceWakeEnabled: Boolean = false
            set(value) {
                field = value
                if (value) {
                    instance?.startListening()
                } else {
                    instance?.stopListening()
                }
            }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        if (isVoiceWakeEnabled) {
            startListening()
        }
    }

    fun startListening() {
        if (isListening) return
        isListening = true
        mainHandler.post {
            initAndStartSpeechRecognizer()
        }
    }

    fun stopListening() {
        isListening = false
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            speechRecognizer = null
        }
    }

    private fun initAndStartSpeechRecognizer() {
        if (!isListening) return
        try {
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {}
                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() {}
                        override fun onError(error: Int) {
                            if (isListening) {
                                mainHandler.postDelayed({
                                    initAndStartSpeechRecognizer()
                                }, 1500)
                            }
                        }
                        override fun onResults(results: Bundle?) {
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val text = matches?.firstOrNull()?.lowercase() ?: ""
                            if (text.isNotEmpty()) {
                                handleVoiceCommand(text)
                            }
                            if (isListening) {
                                mainHandler.postDelayed({
                                    initAndStartSpeechRecognizer()
                                }, 500)
                            }
                        }
                        override fun onPartialResults(partialResults: Bundle?) {}
                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            }
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleVoiceCommand(text: String) {
        val command = text.lowercase().trim()
        if (command.contains("horizon")) {
            mainHandler.post {
                VoiceTriggerManager.triggerCommand(command)
            }

            if (command.contains("lock")) {
                try {
                    performGlobalAction(8) // GLOBAL_ACTION_LOCK_SCREEN
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else if (command.contains("unlock")) {
                try {
                    val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                    val wakeLock = powerManager.newWakeLock(
                        PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                        "Horizon:WakeLock"
                    )
                    wakeLock.acquire(3000)
                    mainHandler.postDelayed({
                        swipe(540f, 1800f, 540f, 400f, 300)
                    }, 500)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We can capture screen changes or events here
    }

    override fun onInterrupt() {
        // Handle interruptions
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    /**
     * Taps at the specific coordinates on the screen.
     */
    fun tapAt(x: Float, y: Float): Boolean {
        val path = Path().apply {
            moveTo(x, y)
        }
        val builder = GestureDescription.Builder()
        builder.addStroke(GestureDescription.StrokeDescription(path, 0, 100))
        return dispatchGesture(builder.build(), null, null)
    }

    /**
     * Swipes from start to end coordinates with specified duration.
     */
    fun swipe(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long): Boolean {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val builder = GestureDescription.Builder()
        builder.addStroke(GestureDescription.StrokeDescription(path, 0, duration))
        return dispatchGesture(builder.build(), null, null)
    }

    /**
     * Triggers standard global actions like Back, Home, and Recents.
     */
    fun triggerGlobalAction(action: Int): Boolean {
        return performGlobalAction(action)
    }

    /**
     * Captures and serializes the interactive elements of the current screen to text format.
     */
    fun captureScreenLayout(): String {
        val root = rootInActiveWindow ?: return "Screen content empty or not accessible."
        val sb = StringBuilder()
        sb.append("Current Screen Hierarchy:\n")
        traverseAndSerialize(root, 0, sb)
        root.recycle()
        return sb.toString()
    }

    private fun traverseAndSerialize(node: AccessibilityNodeInfo?, depth: Int, sb: StringBuilder) {
        if (node == null) return
        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        val className = node.className?.toString() ?: ""
        val shortClass = className.substringAfterLast('.')
        val isClickable = node.isClickable
        val isEditable = node.isEditable
        val viewId = node.viewIdResourceName?.substringAfterLast(':') ?: ""

        // Filter for meaningful nodes
        if (text.isNotEmpty() || desc.isNotEmpty() || isClickable || isEditable) {
            val indent = "  ".repeat(depth.coerceAtMost(5))
            val clickableTag = if (isClickable) " [Clickable]" else ""
            val editableTag = if (isEditable) " [Editable]" else ""
            val idTag = if (viewId.isNotEmpty()) " id=\"$viewId\"" else ""
            val label = if (text.isNotEmpty()) "text=\"$text\"" else if (desc.isNotEmpty()) "desc=\"$desc\"" else ""

            sb.append("$indent- <$shortClass>$idTag $label$clickableTag$editableTag Bounds=(${bounds.centerX()}, ${bounds.centerY()}) Rect=(${bounds.left},${bounds.top},${bounds.right},${bounds.bottom})\n")
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            traverseAndSerialize(child, depth + 1, sb)
            child?.recycle()
        }
    }
}
