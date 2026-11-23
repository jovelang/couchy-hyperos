// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Couchy HyperOS contributors.
package com.homekey.standin;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

/** DUMP-protected local administration endpoint; exposes three fixed operations. */
public final class HomePolicyProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        // ContentProvider.call does not consistently enforce manifest read/write permissions.
        getContext().enforceCallingPermission(android.Manifest.permission.DUMP, "Trusted device administration only");
        if ("status".equals(method)) return HomePolicy.status(getContext());
        if ("enable".equals(method)) {
            return HomePolicy.changeEnabled(getContext(), true);
        }
        if ("disable".equals(method)) {
            return HomePolicy.changeEnabled(getContext(), false);
        }
        throw new IllegalArgumentException("Unknown HOME policy operation");
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) { throw new UnsupportedOperationException(); }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String where, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String where, String[] args) { throw new UnsupportedOperationException(); }
}
