package com.oxlo.gallary.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.oxlo.gallary.GallaryApp
import com.oxlo.gallary.di.AppContainer

/** Tiny factory so view models can take the dependency container directly. */
class SimpleViewModelFactory<VM : ViewModel>(private val creator: () -> VM) :
    ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = creator() as T
}

@Composable
fun rememberContainer(): AppContainer {
    val context = LocalContext.current
    return remember(context) { (context.applicationContext as GallaryApp).container }
}
