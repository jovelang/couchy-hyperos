// Modified for Couchy HyperOS, 2026-11-23. Upstream and change notices: UPSTREAM.md, CHANGELOG.md.
package com.conreo.couchytv.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

/** Use system minute/time-zone events, including network clock corrections and resume. */
fun clockChanges(context:Context)=callbackFlow {
    val receiver=object:BroadcastReceiver() {
        override fun onReceive(c:Context?,intent:Intent?) {trySend(System.currentTimeMillis())}
    }
    val filter=IntentFilter().apply {addAction(Intent.ACTION_TIME_TICK);addAction(Intent.ACTION_TIME_CHANGED);addAction(Intent.ACTION_TIMEZONE_CHANGED);addAction(Intent.ACTION_DATE_CHANGED)}
    ContextCompat.registerReceiver(context,receiver,filter,ContextCompat.RECEIVER_NOT_EXPORTED)
    trySend(System.currentTimeMillis())
    awaitClose {context.unregisterReceiver(receiver)}
}
