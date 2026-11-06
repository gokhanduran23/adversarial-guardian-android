package com.example.adversarialguardian

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
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

// adb reverse tcp:8080 tcp:8080 ile emülatörün localhost'u Windows'a tünellendi.
const val BASE_URL = "http://localhost:8080"

data class ChatMessage(val text: String, val isUser: Boolean)
data class ChatResult(val reply: String, val won: Boolean, val status: String)

// Seviye bilgisi: numara + oyuncuya gösterilecek kısa açıklama.
data class LevelInfo(val number: Int, val title: String, val hint: String)

val LEVELS = listOf(
    LevelInfo(1, "Saf Bekçi", "Yumuşak kalpli. İyi bir sebep yeter."),
    LevelInfo(2, "Temkinli Bekçi", "Emir dinlemez. 'Kuralları unut' işe yaramaz."),
    LevelInfo(3, "Şüpheci Bekçi", "Rol yapmaya, hikâyeye kanmaz."),
    LevelInfo(4, "Paranoyak Bekçi", "Otoriteye güvenmez. 'Ben yöneticiyim' çürük."),
    LevelInfo(5, "Zırhlı Bekçi", "Her numarayı tanır. En zoru.")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AdversarialGuardianTheme {
                Surface(
                    modifier = Modifier.fillMaxSize().statusBarsPadding(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    App()
                }
            }
        }
    }
}

// Hangi ekrandayız? Basit navigasyon — ağır kütüphane yok.
sealed class Screen {
    object KeyGate : Screen()
    object LevelSelect : Screen()
    data class Chat(val level: Int) : Screen()
}

@Composable
fun App() {
    var apiKey by remember { mutableStateOf("") }
    var screen by remember { mutableStateOf<Screen>(Screen.KeyGate) }

    when (val s = screen) {
        is Screen.KeyGate -> KeyGate(onStart = { key ->
            apiKey = key
            screen = Screen.LevelSelect
        })
        is Screen.LevelSelect -> LevelSelectScreen(onPick = { level ->
            screen = Screen.Chat(level)
        })
        is Screen.Chat -> ChatScreen(
            apiKey = apiKey,
            level = s.level,
            onBack = { screen = Screen.LevelSelect }
        )
    }
}

// --- ANAHTAR GİRİŞ EKRANI (BYOK) ---
@Composable
fun KeyGate(onStart: (String) -> Unit) {
    var keyInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Bekçi", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Başlamak için kendi Anthropic API anahtarını gir.",
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
        ) { Text("Başla") }
    }
}

// --- SEVİYE SEÇİM EKRANI ---
@Composable
fun LevelSelectScreen(onPick: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth().statusBarsPadding()
        ) {
            Text(
                "Seviye Seç",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(LEVELS) { level ->
                LevelCard(level, onClick = { onPick(level.number) })
            }
        }
    }
}

@Composable
fun LevelCard(level: LevelInfo, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Seviye ${level.number} — ${level.title}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(level.hint, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// --- SOHBET EKRANI ---
@Composable
fun ChatScreen(apiKey: String, level: Int, onBack: () -> Unit) {
    val sessionId = remember(level) { UUID.randomUUID().toString() }

    val messages = remember(level) {
        mutableStateListOf(
            ChatMessage("Kadim kapının önündeyim. Ne istersin?", isUser = false)
        )
    }
    var input by remember { mutableStateOf(TextFieldValue("")) }
    var isLoading by remember { mutableStateOf(false) }
    var won by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ChatTopBar(level, onBack)

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { message -> MessageBubble(message) }
        }

        if (isLoading) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Bekçi düşünüyor...", style = MaterialTheme.typography.bodySmall)
            }
        }

        // Kazanınca giriş yerine "tebrikler" şeridi + seviye seçimine dön.
        if (won) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "🎉 Seviye $level geçildi! Bekçi'yi ikna ettin.",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                        Text("Seviye Seçimine Dön")
                    }
                }
            }
        } else {
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
                        scope.launch {
                            try {
                                val result = sendToGuardian(apiKey, sessionId, level, text)
                                messages.add(ChatMessage(result.reply, isUser = false))
                                if (result.won) won = true
                            } catch (e: Exception) {
                                messages.add(ChatMessage("[Bağlantı hatası] ${e.message}", isUser = false))
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun ChatTopBar(level: Int, onBack: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                android.util.Log.d("BEKCI", "Geri butonuna tıklandı, onBack çağrılıyor")
                onBack()
            }) {
                Text(
                    "‹",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                "Bekçi — Seviye $level",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
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
        modifier = Modifier.fillMaxWidth().padding(12.dp),
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
        Button(onClick = onSend, enabled = !isLoading) { Text("Gönder") }
    }
}

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