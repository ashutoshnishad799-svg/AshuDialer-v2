package com.ashudialer.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.ui.components.glassCard
import com.ashudialer.app.ui.theme.LocalDialerPalette

private enum class GuideLanguage(val label: String) { ENGLISH("English"), HINDI("हिन्दी") }

private data class GuideStep(val title: String, val body: String, val action: GuideAction? = null)

private enum class GuideAction { GET_SHIZUKU, OPEN_DEV_OPTIONS, OPEN_ABOUT_PHONE, OPEN_SHIZUKU }

private data class GuideText(
    val title: String,
    val intro: String,
    val beginnerHeader: String,
    val beginnerSteps: List<GuideStep>,
    val rootHeader: String,
    val rootSteps: List<GuideStep>,
    val afterHeader: String,
    val afterSteps: List<String>,
    val troubleHeader: String,
    val troubleshooting: List<String>,
    val confirm: String,
    val openSetup: String,
    val actionLabels: Map<GuideAction, String>
)

private val EN = GuideText(
    title = "How to set up call recording",
    intro = "Android blocks normal apps from hearing the other person on a phone call. Shizuku is a free helper app that safely gives Ashu Dialer the permission it needs. No root, no Magisk. You set it up once.",
    beginnerHeader = "No root? Follow these steps (about 3 minutes)",
    beginnerSteps = listOf(
        GuideStep("1. Install Shizuku", "Install the Shizuku app from Google Play or GitHub. Do not open it yet.", GuideAction.GET_SHIZUKU),
        GuideStep("2. Turn on Developer options", "Open Settings > About phone. Tap \"Build number\" 7 times quickly. You will see \"You are now a developer\".", GuideAction.OPEN_ABOUT_PHONE),
        GuideStep("3. Turn on Wireless debugging", "Connect to a Wi-Fi network. Open Settings > Developer options, then switch on \"Wireless debugging\" and tap Allow.", GuideAction.OPEN_DEV_OPTIONS),
        GuideStep("4. Pair Shizuku", "Open Shizuku and tap \"Pairing\" under \"Start via Wireless debugging\". In Developer options tap \"Pair device with pairing code\". A 6-digit code appears. Type that code into the notification from Shizuku (pull down your notification bar and use the reply box).", GuideAction.OPEN_SHIZUKU),
        GuideStep("5. Start Shizuku", "Back in Shizuku tap \"Start\". Wait until it says \"Shizuku is running\".", GuideAction.OPEN_SHIZUKU),
        GuideStep("6. Allow Ashu Dialer", "Come back to the Call recording setup screen and tap \"Allow\" on the Shizuku permission step. Choose \"Allow all the time\".")
    ),
    rootHeader = "Rooted phone? Much easier",
    rootSteps = listOf(
        GuideStep("1. Install Shizuku", "Install Shizuku from Google Play or GitHub.", GuideAction.GET_SHIZUKU),
        GuideStep("2. Start with root", "Open Shizuku and tap \"Start\" under \"Start via root\". When your root manager (Magisk / KernelSU) asks, choose Grant / Allow.", GuideAction.OPEN_SHIZUKU),
        GuideStep("3. Authorize Ashu Dialer", "In Shizuku open \"Authorized applications\" and switch Ashu Dialer ON. Or just tap \"Allow\" on the Shizuku permission step in this app."),
        GuideStep("4. Auto-start on boot", "In Shizuku Settings turn on \"Start on boot\" so it is always running after a restart.")
    ),
    afterHeader = "After setup",
    afterSteps = listOf(
        "Turn on the \"Record calls\" switch on the setup screen.",
        "Make or receive a call. A \"Recording call\" notification appears.",
        "Find recordings in the Recordings tab. WhatsApp and Telegram calls appear in their own pages there.",
        "Wireless-debugging Shizuku stops after a reboot. Open Shizuku and tap Start again (or use the auto-start option on the setup screen)."
    ),
    troubleHeader = "Not working?",
    troubleshooting = listOf(
        "Recording is silent or one-sided: in Recording settings change \"Audio source\" (try \"Voice communication\" or \"Mic\").",
        "\"Shizuku is not running\": open Shizuku and tap Start. Wireless debugging needs Wi-Fi to start.",
        "Nothing is recorded: check every required step on the setup screen is green, especially Battery.",
        "Xiaomi / Redmi / POCO: also turn on Autostart and set battery to \"No restrictions\" for Ashu Dialer."
    ),
    confirm = "I understand - continue",
    openSetup = "Open setup checklist",
    actionLabels = mapOf(
        GuideAction.GET_SHIZUKU to "Get Shizuku",
        GuideAction.OPEN_DEV_OPTIONS to "Open Developer options",
        GuideAction.OPEN_ABOUT_PHONE to "Open Settings",
        GuideAction.OPEN_SHIZUKU to "Open Shizuku"
    )
)

private val HI = GuideText(
    title = "कॉल रिकॉर्डिंग कैसे सेट करें",
    intro = "Android सामान्य ऐप्स को फ़ोन कॉल में सामने वाले की आवाज़ सुनने नहीं देता। Shizuku एक फ्री हेल्पर ऐप है जो Ashu Dialer को ज़रूरी अनुमति सुरक्षित तरीके से देता है। न root चाहिए, न Magisk। बस एक बार सेट करना है।",
    beginnerHeader = "Root नहीं है? ये स्टेप्स फॉलो करें (लगभग 3 मिनट)",
    beginnerSteps = listOf(
        GuideStep("1. Shizuku इंस्टॉल करें", "Google Play या GitHub से Shizuku ऐप इंस्टॉल करें। अभी खोलें नहीं।", GuideAction.GET_SHIZUKU),
        GuideStep("2. Developer options चालू करें", "Settings > About phone खोलें। \"Build number\" पर 7 बार जल्दी-जल्दी टैप करें। \"You are now a developer\" दिखेगा।", GuideAction.OPEN_ABOUT_PHONE),
        GuideStep("3. Wireless debugging चालू करें", "Wi-Fi से कनेक्ट रहें। Settings > Developer options में \"Wireless debugging\" चालू करें और Allow दबाएँ।", GuideAction.OPEN_DEV_OPTIONS),
        GuideStep("4. Shizuku को पेयर करें", "Shizuku खोलें और \"Start via Wireless debugging\" के नीचे \"Pairing\" दबाएँ। Developer options में \"Pair device with pairing code\" दबाएँ। 6 अंकों का कोड दिखेगा। वही कोड Shizuku की नोटिफिकेशन (नोटिफिकेशन बार नीचे खींचें) के रिप्लाई बॉक्स में लिखें।", GuideAction.OPEN_SHIZUKU),
        GuideStep("5. Shizuku स्टार्ट करें", "Shizuku में वापस जाकर \"Start\" दबाएँ। जब \"Shizuku is running\" लिखा आए तब आगे बढ़ें।", GuideAction.OPEN_SHIZUKU),
        GuideStep("6. Ashu Dialer को अनुमति दें", "Call recording setup स्क्रीन पर वापस आकर Shizuku permission स्टेप में \"Allow\" दबाएँ। \"Allow all the time\" चुनें।")
    ),
    rootHeader = "Root है? बहुत आसान",
    rootSteps = listOf(
        GuideStep("1. Shizuku इंस्टॉल करें", "Google Play या GitHub से Shizuku इंस्टॉल करें।", GuideAction.GET_SHIZUKU),
        GuideStep("2. Root से स्टार्ट करें", "Shizuku खोलें और \"Start via root\" के नीचे \"Start\" दबाएँ। जब root मैनेजर (Magisk / KernelSU) पूछे तो Grant / Allow चुनें।", GuideAction.OPEN_SHIZUKU),
        GuideStep("3. Ashu Dialer को Authorize करें", "Shizuku में \"Authorized applications\" खोलें और Ashu Dialer को ON करें। या इस ऐप में Shizuku permission स्टेप पर \"Allow\" दबाएँ।"),
        GuideStep("4. Boot पर ऑटो-स्टार्ट", "Shizuku Settings में \"Start on boot\" चालू करें ताकि रीस्टार्ट के बाद भी हमेशा चलता रहे।")
    ),
    afterHeader = "सेटअप के बाद",
    afterSteps = listOf(
        "सेटअप स्क्रीन पर \"Record calls\" स्विच चालू करें।",
        "कोई कॉल करें या रिसीव करें। \"Recording call\" नोटिफिकेशन दिखेगा।",
        "रिकॉर्डिंग्स Recordings टैब में मिलेंगी। WhatsApp और Telegram कॉल्स वहाँ अपने अलग पेज में दिखेंगी।",
        "Wireless debugging वाला Shizuku रीबूट के बाद बंद हो जाता है। Shizuku खोलकर फिर Start दबाएँ (या सेटअप स्क्रीन का ऑटो-स्टार्ट विकल्प इस्तेमाल करें)।"
    ),
    troubleHeader = "काम नहीं कर रहा?",
    troubleshooting = listOf(
        "रिकॉर्डिंग खाली है या एक तरफ की ही है: Recording settings में \"Audio source\" बदलें (\"Voice communication\" या \"Mic\" आज़माएँ)।",
        "\"Shizuku is not running\": Shizuku खोलकर Start दबाएँ। Wireless debugging शुरू करने के लिए Wi-Fi चाहिए।",
        "कुछ रिकॉर्ड नहीं हो रहा: सेटअप स्क्रीन के सारे ज़रूरी स्टेप हरे हैं या नहीं देखें, खासकर Battery।",
        "Xiaomi / Redmi / POCO: Ashu Dialer के लिए Autostart चालू करें और बैटरी \"No restrictions\" रखें।"
    ),
    confirm = "समझ गया - आगे बढ़ें",
    openSetup = "सेटअप चेकलिस्ट खोलें",
    actionLabels = mapOf(
        GuideAction.GET_SHIZUKU to "Shizuku डाउनलोड करें",
        GuideAction.OPEN_DEV_OPTIONS to "Developer options खोलें",
        GuideAction.OPEN_ABOUT_PHONE to "Settings खोलें",
        GuideAction.OPEN_SHIZUKU to "Shizuku खोलें"
    )
)

/**
 * Beginner-friendly, step-by-step guide to the Shizuku setup (English + Hindi), with a button on
 * each step that jumps straight to the right place. Reached from the ? button on the setup screen
 * and from the Recordings screen's help icon.
 *
 * [onConfirmEnable] is non-null only when the guide is shown as the confirm step before recording
 * is switched on; the button then confirms and continues.
 */
@Composable
fun RecordingGuideScreen(
    onBack: () -> Unit,
    onConfirmEnable: (() -> Unit)? = null,
    onOpenSetup: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current
    val context = LocalContext.current
    var language by remember { mutableStateOf(GuideLanguage.HINDI) }
    val t = if (language == GuideLanguage.ENGLISH) EN else HI

    fun open(intent: Intent) { runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    fun run(action: GuideAction) {
        when (action) {
            GuideAction.GET_SHIZUKU -> open(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/RikkaApps/Shizuku/releases/latest")))
            GuideAction.OPEN_DEV_OPTIONS -> open(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            GuideAction.OPEN_ABOUT_PHONE -> open(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
            GuideAction.OPEN_SHIZUKU -> {
                val launch = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                if (launch != null) open(launch)
                else open(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/RikkaApps/Shizuku/releases/latest")))
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = palette.textPrimary) }
            Spacer(Modifier.width(4.dp))
            Text(t.title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary, modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier.clip(RoundedCornerShape(50)).background(palette.accentSoft).padding(2.dp)
            ) {
                GuideLanguage.entries.forEach { lang ->
                    Text(
                        lang.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        color = if (lang == language) palette.solidBackground else palette.textSecondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (lang == language) palette.accent else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { language = lang }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(t.intro, fontSize = 13.sp, color = palette.textSecondary, lineHeight = 19.sp)
            }
            item { Header(t.beginnerHeader, palette) }
            items(t.beginnerSteps.size) { i -> StepCard(t.beginnerSteps[i], t, ::run, palette) }

            item { Header(t.rootHeader, palette) }
            items(t.rootSteps.size) { i -> StepCard(t.rootSteps[i], t, ::run, palette) }

            item { Header(t.afterHeader, palette) }
            items(t.afterSteps.size) { i -> Bullet(t.afterSteps[i], palette) }

            item { Header(t.troubleHeader, palette) }
            items(t.troubleshooting.size) { i -> Bullet(t.troubleshooting[i], palette) }

            item {
                Spacer(Modifier.height(8.dp))
                if (onOpenSetup != null) {
                    Button(onClick = onOpenSetup, modifier = Modifier.fillMaxWidth()) { Text(t.openSetup, fontSize = 14.sp) }
                    Spacer(Modifier.height(8.dp))
                }
                if (onConfirmEnable != null) {
                    OutlinedButton(onClick = onConfirmEnable, modifier = Modifier.fillMaxWidth()) { Text(t.confirm, fontSize = 14.sp) }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun Header(text: String, palette: com.ashudialer.app.ui.theme.DialerPalette) {
    Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = palette.accent, modifier = Modifier.padding(top = 10.dp))
}

@Composable
private fun Bullet(text: String, palette: com.ashudialer.app.ui.theme.DialerPalette) {
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 7.dp).size(5.dp).clip(CircleShape).background(palette.accent))
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 13.sp, color = palette.textPrimary, lineHeight = 19.sp)
    }
}

@Composable
private fun StepCard(
    step: GuideStep,
    t: GuideText,
    onAction: (GuideAction) -> Unit,
    palette: com.ashudialer.app.ui.theme.DialerPalette
) {
    Column(Modifier.fillMaxWidth().glassCard(palette, 16.dp).padding(14.dp)) {
        Text(step.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
        Spacer(Modifier.height(4.dp))
        Text(step.body, fontSize = 12.5.sp, color = palette.textSecondary, lineHeight = 18.sp)
        step.action?.let { action ->
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { onAction(action) }, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                Icon(Icons.Filled.OpenInNew, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(t.actionLabels[action].orEmpty(), fontSize = 12.5.sp)
            }
        }
    }
}
