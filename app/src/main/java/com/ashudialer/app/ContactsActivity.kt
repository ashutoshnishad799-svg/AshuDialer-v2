package com.ashudialer.app

import android.os.Bundle

/**
 * Second launcher entry used for the native dual Phone/Contacts experience.
 * It reuses the complete MainActivity implementation and only selects the
 * Contacts tab on launch, so there is no duplicated UI or business logic.
 */
class ContactsActivity : MainActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        intent.putExtra(EXTRA_LAUNCH_TAB, TAB_CONTACTS)
        super.onCreate(savedInstanceState)
    }
}
