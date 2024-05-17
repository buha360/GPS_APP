package com.wardanger3.gps_app.manuals

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.wardanger3.gps_app.MainActivity
import com.wardanger3.gps_app.R

class IntroductionActivity : AppCompatActivity() {

    private lateinit var buttonNext: Button

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

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Location access not required anymore")

        val messageText = "Please note that location access is no longer necessary for the use of this application, as the feature requiring such access has been completely removed. We are committed to ensuring your privacy and optimizing app performance. Check out our Privacy Policy here: https://buha360.github.io/"
        val spannableString = SpannableString(messageText)

        val linkStart = messageText.indexOf("https://")
        val linkEnd = messageText.length

        spannableString.setSpan(
            URLSpan("https://buha360.github.io/"),
            linkStart,
            linkEnd,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        val textView = TextView(this).apply {
            text = spannableString
            movementMethod = LinkMovementMethod.getInstance()
        }

        builder.setView(textView)
        builder.setPositiveButton("OK") { dialog, _ ->
            dialog.dismiss()
        }
        builder.show()
    }
}
