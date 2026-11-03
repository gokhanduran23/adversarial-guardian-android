package com.example.adversarialguardian

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.adversarialguardian.ui.theme.AdversarialGuardianTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

// Backend adresi. Emülatör 'localhost'u KENDİSİ sanır; bilgisayara erişmek
// için özel IP 10.0.2.2 kullanılır. Gerçek telefonda bu, bilgisayarın
// yerel ağ IP'siyle değiştirilecek.
const val BASE_URL = "http://localhost:8080"

// Tek bir sohbet mesajı.
data class ChatMessage(val text: String, val isUser: Boolean)

// Backend'den dönen yanıt.
data class ChatResult(val reply: String, val won: Boolean, val status: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AdversarialGuardianTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    App()
                }
            }
        }
    }
}

// Anahtar girilmeden sohbet açılmaz (BYOK).
@Composable
fun App() {
    var apiKey by remember { mutableStateOf("") }

    if (apiKey.isBlank()) {
        KeyGate(onStart = { key -> apiKey = key })
    } else {
        ChatScreen(apiKey = apiKey)
    }
}

// --- ANAHTAR GİRİŞ EKRANI (BYOK) ---
@Composable
fun KeyGate(onStart: (String) -> Unit) {
    var keyInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Bekçi",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Başlamak için kendi Anthropic API anahtarını gir.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = keyInput,
            onValueChange = { keyInput = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("sk-ant-...") },
            singleLine = true
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { if (keyInput.isNotBlank()) onStart(keyInput.trim()) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Başla")
        }
    }
}

// --- SOHBET EKRANI ---
@Composable
fun ChatScreen(apiKey: String) {
    // Her oturum için tek bir kimlik. remember sayesinde ekran yeniden
    // çizilse de aynı kalır (geçmiş backend'de bu kimlikle birikir).
    val sessionId = remember { UUID.randomUUID().toString() }
    val level = 1 // şimdilik sabit; seviye seçimini sonra ekleyeceğiz

    val messages = remember {
        mutableStateListOf(
            ChatMessage("Kadim kapının önündeyim. Ne istersin?", isUser = false)
        )
    }
    var input by remember { mutableStateOf(TextFieldValue("")) }
    var isLoading by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Yeni mesaj gelince listeyi en alta kaydır.
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(level)

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { message -> MessageBubble(message) }
        }

        if (isLoading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Bekçi düşünüyor...", style = MaterialTheme.typography.bodySmall)
            }
        }

        InputBar(
            input = input,
            isLoading = isLoading,
            onInputChange = { input = it },
            onSend = {
                val text = input.text.trim()
                if (text.isNotEmpty() && !isLoading) {
                    messages.add(ChatMessage(text, isUser = true))
                    input = TextFieldValue("")
                    isLoading = true

                    // Ağ çağrısı UI thread'ini bloklamamalı — coroutine içinde,
                    // IO dispatcher'da çalışır. Bu, Android'de async'in temeli.
                    scope.launch {
                        try {
                            val result = sendToGuardian(apiKey, sessionId, level, text)
                            messages.add(ChatMessage(result.reply, isUser = false))
                            if (result.won) {
                                messages.add(
                                    ChatMessage(
                                        "🎉 Kapı açıldı! Bu seviyeyi geçtin.",
                                        isUser = false
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            messages.add(
                                ChatMessage("[Bağlantı hatası] ${e.message}", isUser = false)
                            )
                        } finally {
                            isLoading = false
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun TopBar(level: Int) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer) {
        Text(
            text = "Bekçi — Seviye $level",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    val alignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor =
        if (message.isUser) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant
    val textColor =
        if (message.isUser) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant

    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .align(alignment)
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(bubbleColor)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(text = message.text, color = textColor)
        }
    }
}

@Composable
fun InputBar(
    input: TextFieldValue,
    isLoading: Boolean,
    onInputChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Bekçi'ye bir şey söyle...") },
            enabled = !isLoading
        )
        Spacer(modifier = Modifier.width(8.dp))
        Button(onClick = onSend, enabled = !isLoading) {
            Text("Gönder")
        }
    }
}

// --- AĞ ÇAGRISI: backend'in /api/chat ucuna POST ---
// withContext(Dispatchers.IO): ağ işi arka planda, UI donmaz.
// Yeni kütüphane yok — Android'in yerleşik HttpURLConnection + JSONObject'i.
suspend fun sendToGuardian(
    apiKey: String,
    sessionId: String,
    level: Int,
    message: String
): ChatResult = withContext(Dispatchers.IO) {
    val url = URL("$BASE_URL/api/chat")
    val conn = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
        connectTimeout = 15000
        readTimeout = 60000
    }

    val body = JSONObject()
        .put("sessionId", sessionId)
        .put("level", level)
        .put("message", message)
        .put("userApiKey", apiKey)
        .toString()

    conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

    val code = conn.responseCode
    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
    val responseText = stream?.bufferedReader()?.use { it.readText() } ?: ""

    if (code !in 200..299) {
        throw RuntimeException("Sunucu $code döndü: $responseText")
    }

    val json = JSONObject(responseText)
    ChatResult(
        reply = json.optString("reply"),
        won = json.optBoolean("won"),
        status = json.optString("status")
    )
}