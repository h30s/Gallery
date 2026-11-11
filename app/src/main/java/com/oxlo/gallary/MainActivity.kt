package com.oxlo.gallary

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.oxlo.gallary.data.settings.AppSettings
import com.oxlo.gallary.di.AppContainer
import com.oxlo.gallary.ui.SimpleViewModelFactory
import com.oxlo.gallary.ui.gallery.GalleryViewModel
import com.oxlo.gallary.ui.navigation.GallaryNavHost
import com.oxlo.gallary.ui.theme.GallaryTheme
import com.oxlo.gallary.util.mediaPermissions

class MainActivity : AppCompatActivity() {

    private val container: AppContainer
        get() = (application as GallaryApp).container

    private val galleryViewModel: GalleryViewModel by viewModels {
        SimpleViewModelFactory { GalleryViewModel(container) }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) {
            galleryViewModel.refresh(announce = false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val settings by container.settings.settings
                .collectAsState(initial = AppSettings())

            GallaryTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GallaryNavHost(
                        container = container,
                        galleryViewModel = galleryViewModel
                    )
                }
            }
        }

        requestMediaAccess()
    }

    private fun requestMediaAccess() {
        val missing = mediaPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            galleryViewModel.refresh(announce = false)
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
            galleryViewModel.refresh(announce = false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            container.faceAnalyzer.close()
        }
    }
}
