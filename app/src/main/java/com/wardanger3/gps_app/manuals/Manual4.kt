package com.wardanger3.gps_app.manuals

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.wardanger3.gps_app.MainActivity
import com.wardanger3.gps_app.R

class IntroductionActivity4 : AppCompatActivity() {

    private lateinit var buttonNext: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Layout kiválasztása a nyelvi kód alapján
        val layoutResId = when (intent.getStringExtra("LanguageCode")) {
            "hu" -> R.layout.manual4hun
            else -> R.layout.manual4
        }

        setContentView(layoutResId)

        // Teljes képernyős mód beállítása + navigációs sáv elrejtése
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)

        // Gomb inicializálása
        buttonNext = findViewById(R.id.buttonNext)

        // Gombra kattintás kezelése
        buttonNext.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
    }
}