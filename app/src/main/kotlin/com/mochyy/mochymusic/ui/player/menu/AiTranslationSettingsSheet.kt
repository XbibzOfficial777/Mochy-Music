package com.mochyy.mochymusic.ui.player.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.lyrics.AiTranslationSession
import com.mochyy.mochymusic.lyrics.LyricsTranslationHelper

@Composable
internal fun AiTranslationSettingsSheet(onDismiss: () -> Unit, onContinue: () -> Unit) {
    val active by AiTranslationSession.config.collectAsState()
    var key by remember(active) { mutableStateOf(active?.apiKey.orEmpty()) }
    var model by remember(active) { mutableStateOf(active?.model.orEmpty()) }

    // Configuration fields need the full sheet on small displays and when the IME resizes the
    // window; a half-height detent can leave the model field and Continue action below the fold.
    MenuSheetSurface(onDismiss = onDismiss, halfDetent = 0.94f, fullDetent = 0.94f) { _ ->
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .imePadding().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            Text("AI lyrics translation", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "Uses OpenRouter. Lyrics are sent to the selected model; provider charges may apply. Your API key stays in memory only and is cleared when the app process closes.",
                color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp,
            )
            Spacer(Modifier.height(16.dp))
            AiInput(label = "OpenRouter API key", value = key, onChange = { key = it }, secret = true)
            Spacer(Modifier.height(12.dp))
            AiInput(label = "Model ID (for example openai/gpt-4o-mini)", value = model, onChange = { model = it }, secret = false)
            Spacer(Modifier.height(18.dp))
            PillButton(
                label = "Continue to language and translate",
                enabled = key.isNotBlank() && model.isNotBlank(),
                highlighted = true,
                onClick = {
                    // Switching providers must not start a paid request until Translate is pressed.
                    LyricsTranslationHelper.triggerClearTranslations()
                    AiTranslationSession.set(key, model)
                    onContinue()
                },
            )
            if (active != null) {
                Spacer(Modifier.height(10.dp))
                PillButton(label = "Clear session key", onClick = {
                    LyricsTranslationHelper.triggerClearTranslations()
                    AiTranslationSession.clear()
                    key = ""
                    model = ""
                })
            }
        }
    }
}

@Composable
private fun AiInput(label: String, value: String, onChange: (String) -> Unit, secret: Boolean) {
    Text(label, color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else KeyboardType.Ascii),
        cursorBrush = SolidColor(Color.White),
        textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MenuRowSurface)
            .semantics { contentDescription = label }.padding(horizontal = 14.dp, vertical = 14.dp),
    )
}
