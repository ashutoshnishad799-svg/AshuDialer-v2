package com.ashudialer.app.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Warning
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
import com.ashudialer.app.telecom.OemPermissionHelper
import com.ashudialer.app.ui.theme.DialerPalette
import com.ashudialer.app.ui.theme.LocalDialerPalette
import com.ashudialer.app.ui.components.glassCard

private enum class GuideLanguage(val label: String) {
    ENGLISH("English"),
    HINDI("हिंदी")
}

/**
 * Every word of copy in this screen is checked against what MediaRecorder
 * and AccessibilityService can actually do on a stock, non-root Android 10+
 * device (see CallRecorder.kt, which already implements exactly the
 * fallback chain described here). Deliberately NOT claiming Accessibility
 * gives two-way recording - it does not on the overwhelming majority of
 * devices, and Google's Play policy already forbids using the Accessibility
 * API for call-audio capture. Overselling that path would give someone a
 * false sense that a working feature exists when it mostly captures
 * silence, which is worse than being upfront that root/a supporting ROM is
 * genuinely the only reliable path to true two-way recording today.
 */
@Composable
fun RecordingGuideScreen(
    onBack: () -> Unit,
    // Non-null only when reached from the "Enable call recording" toggle in
    // Settings (rather than the informational ⓘ / banner in My Recordings).
    // Showing a confirm action here - instead of the toggle flipping on
    // immediately - means the person has actually seen this explanation
    // before recording becomes possible, not just had the option to.
    onConfirmEnable: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current
    val context = LocalContext.current
    var language by remember { mutableStateOf(GuideLanguage.ENGLISH) }
    val t = remember(language) { GuideStrings.forLanguage(language) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = palette.textPrimary)
            }
            Spacer(Modifier.width(4.dp))
            Text(t.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary, modifier = Modifier.weight(1f))
        }

        // Language toggle - a simple in-page pill switcher rather than
        // Android's per-app locale system, since this is one screen's copy,
        // not the whole app's UI, and a pill switch is instantly discoverable
        // without hunting through Settings. Built as a plain data-driven list
        // so adding a third/fourth language later is a one-line change to
        // GuideLanguage + GuideStrings, not a restructure of this screen.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GuideLanguage.values().forEach { lang ->
                val selected = lang == language
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selected) palette.accent else palette.cardBackground)
                        .clickable { language = lang }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        lang.label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) androidx.compose.ui.graphics.Color.White else palette.textPrimary
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item {
                InfoBanner(icon = Icons.Filled.Info, text = t.honestIntro, palette = palette)
                Spacer(Modifier.height(20.dp))
                Text(t.whyHeading, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = palette.textSecondary)
                Spacer(Modifier.height(6.dp))
                Text(t.whyBody, fontSize = 13.5.sp, color = palette.textPrimary, lineHeight = 20.sp)
                Spacer(Modifier.height(24.dp))
            }

            item {
                OptionCard(
                    badge = t.recommendedBadge,
                    title = t.option1Title,
                    body = t.option1Body,
                    steps = t.option1Steps,
                    palette = palette,
                    accentBadge = true
                )
                Spacer(Modifier.height(16.dp))
            }

            item {
                OptionCard(
                    badge = t.alsoWorksBadge,
                    title = t.option2Title,
                    body = t.option2Body,
                    steps = t.option2Steps,
                    palette = palette,
                    accentBadge = false
                )
                Spacer(Modifier.height(16.dp))
            }

            item {
                OptionCard(
                    badge = t.limitedBadge,
                    title = t.option3Title,
                    body = t.option3Body,
                    steps = t.option3Steps,
                    palette = palette,
                    accentBadge = false,
                    warning = t.option3Warning
                )
                Spacer(Modifier.height(16.dp))

                OutlinedButton(
                    onClick = {
                        try {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        } catch (_: Exception) {
                            OemPermissionHelper.openAppBatterySettings(context)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(t.openAccessibilitySettings)
                }
                Spacer(Modifier.height(8.dp))
                Text(t.sideloadNote, fontSize = 11.5.sp, color = palette.textSecondary, lineHeight = 16.sp)
                Spacer(Modifier.height(28.dp))
            }
        }

        if (onConfirmEnable != null) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                androidx.compose.material3.Button(
                    onClick = onConfirmEnable,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = palette.accent)
                ) {
                    Text(t.confirmEnableButton, fontWeight = FontWeight.SemiBold, color = androidx.compose.ui.graphics.Color.White)
                }
            }
        }
    }
}

@Composable
private fun InfoBanner(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, palette: DialerPalette) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(palette.accentSoft).padding(14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = palette.accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 13.sp, color = palette.textPrimary, lineHeight = 19.sp)
    }
}

@Composable
private fun OptionCard(
    badge: String,
    title: String,
    body: String,
    steps: List<String>,
    palette: DialerPalette,
    accentBadge: Boolean,
    warning: String? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth().glassCard(palette, 18.dp).padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(if (accentBadge) palette.accent else palette.cardBorder)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(badge, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (accentBadge) androidx.compose.ui.graphics.Color.White else palette.textSecondary)
        }
        Spacer(Modifier.height(10.dp))
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(body, fontSize = 13.sp, color = palette.textSecondary, lineHeight = 19.sp)
        if (steps.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            steps.forEach { step ->
                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = palette.accent, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(step, fontSize = 13.sp, color = palette.textPrimary, lineHeight = 18.sp)
                }
            }
        }
        if (warning != null) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(palette.danger.copy(alpha = 0.12f)).padding(10.dp)
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = palette.danger, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
                Text(warning, fontSize = 12.sp, color = palette.textPrimary, lineHeight = 17.sp)
            }
        }
    }
}

/**
 * Plain data holder for the two supported languages. Kept as a separate
 * object (rather than string resources) because this screen's language
 * toggle is independent of the device/system locale by design - someone
 * can read this guide in Hindi even if the rest of the app and device are
 * in English, and vice versa.
 */
private data class GuideStringSet(
    val title: String,
    val honestIntro: String,
    val whyHeading: String,
    val whyBody: String,
    val recommendedBadge: String,
    val alsoWorksBadge: String,
    val limitedBadge: String,
    val option1Title: String,
    val option1Body: String,
    val option1Steps: List<String>,
    val option2Title: String,
    val option2Body: String,
    val option2Steps: List<String>,
    val option3Title: String,
    val option3Body: String,
    val option3Steps: List<String>,
    val option3Warning: String,
    val openAccessibilitySettings: String,
    val sideloadNote: String,
    val confirmEnableButton: String
)

private object GuideStrings {
    fun forLanguage(language: GuideLanguage): GuideStringSet = when (language) {
        GuideLanguage.ENGLISH -> ENGLISH
        GuideLanguage.HINDI -> HINDI
    }

    private val ENGLISH = GuideStringSet(
        title = "Two-way call recording",
        honestIntro = "Being straight with you: on a normal, non-rooted phone, no app - including this one - can reliably record both sides of a call. Android blocks that on purpose. This page explains exactly why, and which of the options below genuinely work.",
        whyHeading = "WHY THIS HAPPENS",
        whyBody = "Since Android 10, only the phone's built-in system dialer (whatever came preinstalled from the manufacturer) is allowed to use the special audio channel that carries both your voice and the other person's voice together. Third-party dialers - even ones set as your default - are not given access to that channel by Android's security rules. This app already tries the best available option automatically every time you record (you can see this working - it silently tries the call-audio channel first, then falls back to your microphone). What's below are the only ways to go further than that.",
        recommendedBadge = "WORKS RELIABLY",
        alsoWorksBadge = "WORKS ON SOME DEVICES",
        limitedBadge = "ONE SIDE ONLY",
        option1Title = "1. Root your phone (Magisk)",
        option1Body = "Rooting removes Android's restriction entirely by letting you install this app as a privileged \"system app\" through Magisk, the same level of access the manufacturer's own dialer has. This is the only method that reliably captures both sides clearly on most modern phones.",
        option1Steps = listOf(
            "Your phone must already be rooted with Magisk installed",
            "Install this app as a Magisk module (system priv-app) - this grants it the same call-audio access as your phone's built-in dialer",
            "Set it as your default dialer as usual, then record normally"
        ),
        option2Title = "2. Custom ROMs with call-recording support",
        option2Body = "Some custom ROMs (community-built Android versions, e.g. certain LineageOS or AOSP-based builds) re-enable the call-audio channel for any default dialer, not just the preinstalled one. This works without root on those specific ROMs.",
        option2Steps = listOf(
            "Check your ROM's changelog/settings for \"call recording\" support",
            "If supported, simply set this app as your default dialer",
            "Recording will use the call-audio channel automatically - no extra setup needed"
        ),
        option3Title = "3. Accessibility service (your voice only)",
        option3Body = "Android's Accessibility service can help this app stay active and reliable in the background, but it cannot legally or technically capture the other person's voice on almost any modern phone. At best, this records only your own voice through the microphone - which this app already does automatically as a fallback, with or without Accessibility turned on.",
        option3Steps = listOf(
            "This does not add two-way recording - it does not unlock the other person's audio",
            "Only worth enabling if recordings are cutting out or the app is being closed by your phone in the background"
        ),
        option3Warning = "Google's Play Store rules explicitly forbid using Accessibility to record call audio, and on most phones this channel only captures silence. We're telling you this upfront instead of pretending it works.",
        openAccessibilitySettings = "Open Accessibility settings",
        sideloadNote = "If you installed this app from outside the Play Store (a direct link, Telegram, or another website), your phone may hide this app in Accessibility settings by default. Go to Settings → Apps → Ashu Dialer → tap the 3-dot menu → \"Allow restricted settings\", then come back here to turn Accessibility on. This step, and the exact wording, can vary a little by phone brand.",
        confirmEnableButton = "I understand, turn on call recording"
    )

    private val HINDI = GuideStringSet(
        title = "दोनों तरफ की कॉल रिकॉर्डिंग",
        honestIntro = "सीधी बात: बिना रूट किए एक सामान्य फ़ोन पर कोई भी ऐप - यह ऐप भी - कॉल की दोनों तरफ की आवाज़ को भरोसे से रिकॉर्ड नहीं कर सकता। Android जानबूझकर इसे रोकता है। यह पेज बताता है कि ऐसा क्यों होता है, और नीचे दिए गए विकल्पों में से कौन-सा असल में काम करता है।",
        whyHeading = "ऐसा क्यों होता है",
        whyBody = "Android 10 के बाद से, सिर्फ़ फ़ोन में पहले से मौजूद (कंपनी का दिया हुआ) डायलर ही उस खास ऑडियो चैनल का इस्तेमाल कर सकता है जिसमें आपकी और सामने वाले की आवाज़ दोनों साथ आती हैं। थर्ड-पार्टी डायलर - चाहे वो डिफ़ॉल्ट सेट हो या नहीं - को Android की सुरक्षा नीति इस चैनल तक पहुंच नहीं देती। यह ऐप हर बार रिकॉर्डिंग शुरू करते समय पहले से ही सबसे अच्छा उपलब्ध तरीका अपने आप आज़माता है (पहले कॉल-ऑडियो चैनल, फिर आपका माइक्रोफ़ोन)। नीचे दिए गए तरीके इससे आगे जाने के असली रास्ते हैं।",
        recommendedBadge = "भरोसेमंद तरीका",
        alsoWorksBadge = "कुछ डिवाइस पर काम करता है",
        limitedBadge = "सिर्फ़ एक तरफ़ की आवाज़",
        option1Title = "1. फ़ोन रूट करें (Magisk)",
        option1Body = "रूट करने से Android की ये पाबंदी पूरी तरह हट जाती है, क्योंकि आप इस ऐप को Magisk के ज़रिए एक \"सिस्टम ऐप\" के तौर पर इंस्टॉल कर सकते हैं - बिल्कुल वैसी ही एक्सेस जैसी फ़ोन कंपनी के अपने डायलर को मिलती है। ज़्यादातर आधुनिक फ़ोन पर दोनों तरफ़ की साफ़ आवाज़ रिकॉर्ड करने का यही सबसे भरोसेमंद तरीका है।",
        option1Steps = listOf(
            "आपका फ़ोन पहले से Magisk के साथ रूट होना चाहिए",
            "इस ऐप को Magisk मॉड्यूल (सिस्टम प्रिव-ऐप) के तौर पर इंस्टॉल करें - इससे इसे फ़ोन के बिल्ट-इन डायलर जितनी ही कॉल-ऑडियो एक्सेस मिल जाती है",
            "इसे हमेशा की तरह डिफ़ॉल्ट डायलर बनाएं, फिर सामान्य तरीके से रिकॉर्ड करें"
        ),
        option2Title = "2. कॉल-रिकॉर्डिंग सपोर्ट वाले Custom ROM",
        option2Body = "कुछ Custom ROM (कम्युनिटी द्वारा बनाए गए Android वर्शन, जैसे कुछ LineageOS या AOSP-आधारित बिल्ड) किसी भी डिफ़ॉल्ट डायलर के लिए कॉल-ऑडियो चैनल फिर से चालू कर देते हैं, सिर्फ़ पहले से मौजूद डायलर के लिए नहीं। इन खास ROM पर यह बिना रूट किए भी काम करता है।",
        option2Steps = listOf(
            "अपने ROM की सेटिंग्स/changelog में \"call recording\" सपोर्ट चेक करें",
            "अगर सपोर्ट है, तो बस इस ऐप को डिफ़ॉल्ट डायलर बनाएं",
            "रिकॉर्डिंग अपने आप कॉल-ऑडियो चैनल इस्तेमाल करेगी - कोई अतिरिक्त सेटअप नहीं चाहिए"
        ),
        option3Title = "3. Accessibility सर्विस (सिर्फ़ आपकी आवाज़)",
        option3Body = "Android की Accessibility सर्विस इस ऐप को बैकग्राउंड में सक्रिय और भरोसेमंद बनाए रखने में मदद कर सकती है, लेकिन यह सामने वाले की आवाज़ को कानूनी या तकनीकी तौर पर लगभग किसी भी आधुनिक फ़ोन पर कैप्चर नहीं कर सकती। ज़्यादा से ज़्यादा यह सिर्फ़ आपकी अपनी आवाज़ माइक्रोफ़ोन से रिकॉर्ड करती है - जो यह ऐप वैसे भी अपने आप, बिना Accessibility ऑन किए भी, फ़ॉलबैक के तौर पर पहले से करता है।",
        option3Steps = listOf(
            "इससे दोनों तरफ़ की रिकॉर्डिंग नहीं मिलती - सामने वाले की आवाज़ अनलॉक नहीं होती",
            "इसे सिर्फ़ तभी ऑन करें अगर रिकॉर्डिंग बीच में रुक जाती है या फ़ोन बैकग्राउंड में ऐप को बंद कर देता है"
        ),
        option3Warning = "Google Play Store के नियम साफ़ तौर पर कॉल-ऑडियो रिकॉर्ड करने के लिए Accessibility के इस्तेमाल को मना करते हैं, और ज़्यादातर फ़ोन पर यह चैनल सिर्फ़ खामोशी (silence) रिकॉर्ड करता है। हम यह बात पहले ही साफ़ बता रहे हैं, बजाय यह दिखाने के कि यह काम करता है।",
        openAccessibilitySettings = "Accessibility सेटिंग्स खोलें",
        sideloadNote = "अगर आपने यह ऐप Play Store के बाहर से (सीधा लिंक, Telegram, या किसी और वेबसाइट से) इंस्टॉल किया है, तो आपका फ़ोन इसे Accessibility सेटिंग्स में डिफ़ॉल्ट रूप से छुपा सकता है। Settings → Apps → Ashu Dialer में जाएं → ऊपर 3-डॉट मेन्यू दबाएं → \"Allow restricted settings\" चुनें, फिर यहां वापस आकर Accessibility ऑन करें। यह स्टेप और इसके सटीक शब्द फ़ोन ब्रांड के हिसाब से थोड़े अलग हो सकते हैं।",
        confirmEnableButton = "समझ गया, कॉल रिकॉर्डिंग ऑन करें"
    )
}
