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

class IntroductionActivity3 : AppCompatActivity() {

    private lateinit var buttonNext: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Nyelvi kód fogadása az Intentből
        val languageCode = intent.getStringExtra("LanguageCode")

        // Layout kiválasztása a nyelvi kód alapján
        val layoutResId = when (languageCode) {
            "hu" -> R.layout.manual3hun
            else -> R.layout.manual3
        }

        setContentView(layoutResId)

        // Teljes képernyős mód beállítása és navigációs sáv elrejtése
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)

        buttonNext = findViewById(R.id.buttonNext)
        buttonNext.setOnClickListener {
            val nextActivityClass = when (languageCode) {
                "hu" -> IntroductionActivity4::class.java
                else -> IntroductionActivity4::class.java
            }
            val intent = Intent(this, nextActivityClass)
            intent.putExtra("LanguageCode", languageCode)
            startActivity(intent)
        }
    }
}