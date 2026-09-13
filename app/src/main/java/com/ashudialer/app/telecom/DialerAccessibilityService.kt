package com.ashudialer.app.telecom

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Deliberately does almost nothing. Turning this on in Android's
 * Accessibility settings is what makes this app show up there at all (a
 * service class + accessibility_service_config.xml + the manifest <service>
 * entry below are all required for the app to appear in that list in the
 * first place - without them, there is nothing to toggle on).
 *
 * This does NOT and cannot capture call audio - see RecordingGuideScreen for
 * why that's true on almost every modern Android device, accessibility
 * service or not. Its only real effect is that being registered as an
 * accessibility-tool process makes some OEM battery managers (MIUI in
 * particular) less likely to kill this app's background process while a
 * call is active.
 */
class DialerAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally empty - this service does not act on any event.
    }

    override fun onInterrupt() {
        // Intentionally empty.
    }
}
