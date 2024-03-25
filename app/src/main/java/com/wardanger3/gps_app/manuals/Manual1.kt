package com.wardanger3.gps_app.manuals

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.wardanger3.gps_app.MainActivity
import com.wardanger3.gps_app.R

class IntroductionActivity : AppCompatActivity() {

    private lateinit var buttonNext: Button

    companion object {
        private const val REQUEST_LOCATION = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Nyelvi kód fogadása az Intentből
        val languageCode = intent.getStringExtra("LanguageCode")

        // Layout kiválasztása a nyelvi kód alapján
        val layoutResId = when (languageCode) {
            "hu" -> R.layout.manualhun
            else -> R.layout.manual
        }

        setContentView(layoutResId)

        // Teljes képernyős mód beállítása és navigációs sáv elrejtése
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)

        checkLocationPermissionAndInformUser()

        buttonNext = findViewById(R.id.buttonNext)
        buttonNext.setOnClickListener {
            val nextActivityClass = when (languageCode) {
                "hu" -> IntroductionActivity2::class.java
                else -> IntroductionActivity2::class.java
            }
            val intent = Intent(this, nextActivityClass)
            intent.putExtra("LanguageCode", languageCode)
            startActivity(intent)
        }
    }

    private fun checkLocationPermissionAndInformUser() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Tájékoztató dialog megjelenítése először
            AlertDialog.Builder(this)
                .setTitle("Location Permission Needed")
                .setMessage("This app requires access to your location to enable drawing on the map based on your current location even when the app is running in the background. We do not collect, transmit, or store your location data for any other purposes. Your location data is used exclusively within the app to provide and improve the service. No location data is shared with third parties or used for advertising purposes.\n")
                .setPositiveButton("OK") { dialog, which ->
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        REQUEST_LOCATION
                    )
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
}
