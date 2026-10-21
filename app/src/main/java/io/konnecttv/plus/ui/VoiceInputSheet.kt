package io.konnecttv.plus.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import io.konnecttv.plus.TvViewModel

@Composable
fun VoiceInputSheet(vm: TvViewModel, open: Boolean, close: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val recognize = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if(result.resultCode==android.app.Activity.RESULT_OK) {
            text=result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty().take(1000)
        }
    }
    GlassSheet(open,onDismiss=close) {
        Text("Phone microphone",style=Body)
        Text("Dictate, review, then send to the focused TV search/text field. Phone audio is not streamed to Google Assistant; no always-listening wake word. Your phone's speech service may process audio online.",style=Small)
        PrimaryButton("Start dictation") {
            try { recognize.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_PROMPT,"Speak your TV search")) }
            catch(_:ActivityNotFoundException) { error="No speech service installed. Use Type on TV instead." }
        }
        error?.let { Text(it,style=Small) }
        Field(text,{text=it},"Review recognized text")
        PrimaryButton("Send reviewed text",enabled=text.isNotBlank()) { vm.type(text);text="";close() }
        SecondaryButton("Open TV voice assistant") { vm.key(231);close() }
        Text("The TV assistant uses the TV/remote microphone and depends on the TV firmware.",style=Small)
    }
}
