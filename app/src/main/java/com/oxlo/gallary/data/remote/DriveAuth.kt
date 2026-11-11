package com.oxlo.gallary.data.remote

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Minimal Google Drive auth helper.
 *
 * Requires an OAuth client registered for this package name + signing SHA-1 in
 * the Google Cloud console (see README). Without it Google Sign-In simply
 * returns no account and the Drive source reports a clear error.
 */
object DriveAuth {

    const val SCOPE_URL = "https://www.googleapis.com/auth/drive.readonly"

    fun signInIntent(context: Context): Intent {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(SCOPE_URL))
            .build()
        return GoogleSignIn.getClient(context, options).signInIntent
    }

    fun account(context: Context): GoogleSignInAccount? =
        runCatching { GoogleSignIn.getLastSignedInAccount(context) }.getOrNull()

    fun signOut(context: Context) {
        runCatching { GoogleSignIn.getClient(context, GoogleSignInOptions.DEFAULT_SIGN_IN).signOut() }
    }

    suspend fun accessToken(context: Context): String {
        val account = account(context) ?: throw IOException("Sign in to Google Drive first")
        val androidAccount = account.account ?: throw IOException("No Google account on this device")
        return withContext(Dispatchers.IO) {
            GoogleAuthUtil.getToken(context, androidAccount, "oauth2:$SCOPE_URL")
        }
    }
}
