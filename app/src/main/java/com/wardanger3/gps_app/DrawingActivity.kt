package com.wardanger3.gps_app

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.CompoundButtonCompat
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.random.Random

class DrawingActivity : AppCompatActivity() {
    private val activityScope = CoroutineScope(Dispatchers.Main)

    private lateinit var mAdView: AdView
    private var mInterstitialAd: InterstitialAd? = null
    private var isGraphBuilt = false

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drawing)

        // Teljes képernyős mód beállítása + navigációs sáv elrejtése
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)

        loadBannerAd()
        loadInterAd()

        // példányosítások
        val canvasView: CanvasView = findViewById(R.id.canvasView)
        val clearButton = findViewById<Button>(R.id.clearButton)
        val backButton = findViewById<Button>(R.id.buttonBack)
        val saveButton = findViewById<Button>(R.id.saveButton)
        val radioGroup: RadioGroup = findViewById(R.id.radioGroup)
        val radioButton: RadioButton = findViewById(R.id.radioButton)
        val graphImageView: ImageView = findViewById(R.id.graphImageView)
        val colorStateList = ColorStateList.valueOf(Color.parseColor("#ff0000"))

        CompoundButtonCompat.setButtonTintList(radioButton, colorStateList)
        radioButton.isEnabled = false  // Letiltja a RadioButton-t

        clearButton.background = ContextCompat.getDrawable(this@DrawingActivity, R.drawable.custom_button_red)
        backButton.background = ContextCompat.getDrawable(this@DrawingActivity, R.drawable.custom_button_green)
        saveButton.background = ContextCompat.getDrawable(this@DrawingActivity, R.drawable.custom_button_red)

        clearButton.isEnabled = false
        saveButton.isEnabled = false

        canvasView.onDrawListener = object : CanvasView.OnDrawListener {
            override fun onDrawStarted() {
                clearButton.isEnabled = true
                saveButton.isEnabled = true
                clearButton.setTextColor(Color.parseColor("#00ff7b"))
                saveButton.setTextColor(Color.parseColor("#00ff7b"))
                clearButton.background = ContextCompat.getDrawable(this@DrawingActivity, R.drawable.custom_button_green)
            }
        }

        // clear button
        clearButton.setOnClickListener {
            canvasView.clearCanvas() // clearCanvas függvény meghívása a CanvasView példányon
            clearButton.isEnabled = false
            saveButton.isEnabled = false
            saveButton.setTextColor(Color.parseColor("#ff0000")) // Piros szövegszín
            clearButton.setTextColor(Color.parseColor("#ff0000")) // Piros szövegszín
            clearButton.background = ContextCompat.getDrawable(this@DrawingActivity, R.drawable.custom_button_red)
        }

        //back button
        backButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java) // MainActivity újraindítása
            startActivity(intent)
        }

        var isRadioButtonChecked = false
        radioButton.setOnClickListener {
            if (isRadioButtonChecked) {
                // Ha már be van kapcsolva, akkor "kikapcsoljuk"
                radioButton.isChecked = false // Itt volt az elírás
                clearGraphFromImageView(graphImageView)
                isRadioButtonChecked = false
            } else {
                // Ha ki van kapcsolva, akkor "bekapcsoljuk"
                radioGroup.clearCheck()
                drawGraphOnImageView(canvasView, graphImageView)
                isRadioButtonChecked = true
            }
        }

        MainActivity.onGraphBuildCompleteListener = {
            runOnUiThread {
                saveButton.background = ContextCompat.getDrawable(this, R.drawable.custom_button_green)
                isGraphBuilt = true

                // RadioButton színének változtatása zöldre
                val colorStateListGreen = ColorStateList.valueOf(Color.parseColor("#00ff7b"))
                CompoundButtonCompat.setButtonTintList(radioButton, colorStateListGreen)
                radioButton.isEnabled = true  // Engedélyezi a RadioButton-t
            }
        }

        //save button
        saveButton.setOnClickListener {
            // Ellenőrizzük a gomb háttérszínét
            if (!isGraphBuilt) {
                Toast.makeText(this, "Still saving map", Toast.LENGTH_SHORT).show()
            } else {
                // Ha a háttérszín nem piros, akkor elindítjuk a mentési folyamatot
                activityScope.launch(Dispatchers.Default) {
                    Log.d(ContentValues.TAG, "Background task started")
                    canvasView.createGraphFromPathData()
                    Log.d(ContentValues.TAG, "Graph creation completed")
                }

                // Véletlenszám generálása és hirdetés megjelenítése vagy navigálás a FinishSC-re
                val shouldShowAd = Random.nextInt(3) == 1

                if (shouldShowAd) {
                    showInterAd()
                } else {
                    val intent = Intent(this@DrawingActivity, Finish::class.java)
                    startActivity(intent)
                }
            }
        }

        val sharedPref = getSharedPreferences("MyAppPreferences", MODE_PRIVATE)
        if (sharedPref.getBoolean("firstTimeSimple", true)) {
            AlertDialog.Builder(this)
                .setTitle("The Drawing Process!")
                .setMessage("On this drawing page for the simple mode, you can create quick and easy drawings to overlay on the map. In the bottom right corner of the canvas, there is a red button that turns green once the map data has been loaded. Press it to view the data! After this, you can start drawing and simply press the save button to save your work.")
                .setPositiveButton("OK") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()

            sharedPref.edit().putBoolean("firstTimeSimple", false).apply()
        }
    }

    private fun clearGraphFromImageView(graphImageView: ImageView) {
        graphImageView.setImageDrawable(null) // Eltávolítjuk a gráfot az ImageView-ről
    }

    private fun drawGraphOnImageView(canvasView: CanvasView, graphImageView: ImageView) {
        // Előkészítjük a Bitmap-et és a Canvas-t
        val bitmap = Bitmap.createBitmap(canvasView.width, canvasView.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint().apply {
            color = Color.RED
            strokeWidth = 3f
            alpha = 64 // 25% átlátszóság
        }

        // Itt rajzold meg a gráfot hasonlóan, ahogy korábban tennéd
        MainActivity.DataHolder.largeGraph?.forEach { (vertex, neighbors) ->
            neighbors.forEach { neighbor ->
                canvas.drawLine(vertex.x.toFloat(), vertex.y.toFloat(),
                    neighbor.x.toFloat(), neighbor.y.toFloat(), paint)
            }
        }

        // Beállítjuk a kirajzolt Bitmap-et az ImageView forrásaként
        graphImageView.setImageBitmap(bitmap)
    }

    private fun loadBannerAd() {
        MobileAds.initialize(this) {}
        mAdView = findViewById(R.id.adView)
        val adRequest = AdRequest.Builder().build()
        mAdView.loadAd(adRequest)
    }

    private fun showInterAd() {
        Log.d(ContentValues.TAG, "Showing interstitial ad")
        if (mInterstitialAd != null) {
            mInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    super.onAdDismissedFullScreenContent()
                    Log.d(ContentValues.TAG, "Ad dismissed, navigating to FinishSC")
                    // Navigálás a FinishSC osztályba
                    val intent = Intent(this@DrawingActivity, Finish::class.java)
                    startActivity(intent)
                }
            }
            mInterstitialAd?.show(this)
        } else {
            Log.d(ContentValues.TAG, "Interstitial ad not loaded, navigating to FinishSC")
            val intent = Intent(this@DrawingActivity, Finish::class.java)
            startActivity(intent)
        }
    }

    private fun loadInterAd() {
        val adRequest = AdRequest.Builder().build()

        InterstitialAd.load(
            this,
            "ca-app-pub-5856401808246306/6923288924",
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    adError.toString().let { Log.d(ContentValues.TAG, it) }
                    mInterstitialAd = null
                }

                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    Log.d(ContentValues.TAG, "Ad was loaded.")
                    mInterstitialAd = interstitialAd
                }
            })
    }

    override fun onDestroy() {
        super.onDestroy()
        MainActivity.onGraphBuildCompleteListener = null
        activityScope.cancel()
    }
}