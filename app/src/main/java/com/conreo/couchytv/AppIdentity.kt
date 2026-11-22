// Modified for Couchy HyperOS, 2026-11-23. Upstream and change notices: UPSTREAM.md, CHANGELOG.md.
package com.conreo.couchytv

/** Keep Android application identity separate from the inherited source namespace. */
object AppIdentity {
    const val PACKAGE = BuildConfig.APPLICATION_ID
    const val REMOTE_ACTION = PACKAGE + ".REMOTE_ACTION"
    const val SETTINGS_ENTRY_ID = PACKAGE + ".settings"
    const val SOURCE_URL = "https://github.com/jovelang/couchy-hyperos"
}
