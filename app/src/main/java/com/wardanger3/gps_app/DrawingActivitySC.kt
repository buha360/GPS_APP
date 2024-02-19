package com.wardanger3.gps_app

import android.annotation.SuppressLint
import android.content.ContentValues.TAG
import android.content.Intent
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
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.random.Random

class DrawingActivitySC : AppCompatActivity() {
    private val activityScope = CoroutineScope(Dispatchers.Main)

    private lateinit var mAdView : AdView
    private var mInterstitialAd: InterstitialAd? = null
    private lateinit var firebaseAnalytics: FirebaseAnalytics
    private var isGraphBuilt = false

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drawing_sc)

        // Teljes képernyős mód beállítása + navigációs sáv elrejtése
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)

        firebaseAnalytics = Firebase.analytics

        loadBannerAd()
        loadInterAd()

        // példányosítások
        val canvasViewSC: CanvasViewSC = findViewById(R.id.canvasViewSC)
        val clearButton = findViewById<Button>(R.id.clearButton)
        val backButton = findViewById<Button>(R.id.buttonBack)
        val saveButton = findViewById<Button>(R.id.saveButton)
        val radioGroup: RadioGroup = findViewById(R.id.radioGroup)
        val radioButton: RadioButton = findViewById(R.id.radioButton)
        val graphImageViewSC: ImageView = findViewById(R.id.graphImageViewSC)

        clearButton.background = ContextCompat.getDrawable(this@DrawingActivitySC, R.drawable.custom_button_red)
        backButton.background = ContextCompat.getDrawable(this@DrawingActivitySC, R.drawable.custom_button_green)
        saveButton.background = ContextCompat.getDrawable(this@DrawingActivitySC, R.drawable.custom_button_red)

        clearButton.isEnabled = false
        saveButton.isEnabled = false

        canvasViewSC.onDrawListener = object : CanvasViewSC.OnDrawListener {
            override fun onDrawStarted() {
                clearButton.isEnabled = true
                saveButton.isEnabled = true
                clearButton.setTextColor(Color.parseColor("#00ff7b"))
                saveButton.setTextColor(Color.parseColor("#00ff7b"))
                clearButton.background = ContextCompat.getDrawable(this@DrawingActivitySC, R.drawable.custom_button_green)
            }
        }

        // clear button
        clearButton.setOnClickListener {
            canvasViewSC.clearCanvas()
            clearButton.isEnabled = false
            saveButton.isEnabled = false
            saveButton.setTextColor(Color.parseColor("#ff0000")) // Piros szövegszín
            clearButton.setTextColor(Color.parseColor("#ff0000")) // Piros szövegszín
            clearButton.background = ContextCompat.getDrawable(this@DrawingActivitySC, R.drawable.custom_button_red)
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
                clearGraphFromImageView(graphImageViewSC)
                isRadioButtonChecked = false
            } else {
                // Ha ki van kapcsolva, akkor "bekapcsoljuk"
                radioGroup.clearCheck()
                drawGraphOnImageView(canvasViewSC, graphImageViewSC)
                isRadioButtonChecked = true
            }
        }

        MainActivity.onGraphBuildCompleteListener = {
            runOnUiThread {
                saveButton.background = ContextCompat.getDrawable(this, R.drawable.custom_button_green)
                isGraphBuilt = true
            }
        }

        saveButton.setOnClickListener {
            // Ellenőrizzük a gomb háttérszínét
            if (!isGraphBuilt) {
                Toast.makeText(this, "Still saving map", Toast.LENGTH_SHORT).show()
            } else {
                // Ha a háttérszín nem piros, akkor elindítjuk a mentési folyamatot
                activityScope.launch(Dispatchers.Default) {
                    Log.d(TAG, "Background task started")
                    canvasViewSC.createGraphFromPathData()
                    Log.d(TAG, "Graph creation completed")
                }

                // Véletlenszám generálása és hirdetés megjelenítése vagy navigálás a FinishSC-re
                val shouldShowAd = Random.nextInt(3) == 1

                if (shouldShowAd) {
                    showInterAd()
                } else {
                    val intent = Intent(this@DrawingActivitySC, FinishSC::class.java)
                    startActivity(intent)
                }
            }
        }
    }

    private fun clearGraphFromImageView(graphImageViewSC: ImageView) {
        graphImageViewSC.setImageDrawable(null) // Eltávolítjuk a gráfot az ImageView-ről
    }

    private fun drawGraphOnImageView(canvasViewSC: CanvasViewSC, graphImageViewSC: ImageView) {
        // Előkészítjük a Bitmap-et és a Canvas-t
        val bitmap = Bitmap.createBitmap(canvasViewSC.width, canvasViewSC.height, Bitmap.Config.ARGB_8888)
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
        graphImageViewSC.setImageBitmap(bitmap)
    }

    private fun loadBannerAd(){
        MobileAds.initialize(this) {}
        mAdView = findViewById(R.id.adView)
        val adRequest = AdRequest.Builder().build()
        mAdView.loadAd(adRequest)
    }

    private fun showInterAd(){
        if(mInterstitialAd != null){
            mInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback(){
                override fun onAdDismissedFullScreenContent() {
                    super.onAdDismissedFullScreenContent()
                    // Navigálás a FinishSC osztályba
                    val intent = Intent(this@DrawingActivitySC, FinishSC::class.java)
                    startActivity(intent)
                }
            }
            mInterstitialAd?.show(this)
        } else {
            val intent = Intent(this@DrawingActivitySC, FinishSC::class.java)
            startActivity(intent)
        }
    }

    private fun loadInterAd(){
        val adRequest = AdRequest.Builder().build()

        InterstitialAd.load(this,"ca-app-pub-3940256099942544/1033173712", adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                adError.toString().let { Log.d(TAG, it) }
                mInterstitialAd = null
            }

            override fun onAdLoaded(interstitialAd: InterstitialAd) {
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