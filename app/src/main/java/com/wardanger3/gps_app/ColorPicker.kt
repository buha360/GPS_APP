package com.wardanger3.gps_app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.skydoves.colorpicker.compose.AlphaTile
import com.github.skydoves.colorpicker.compose.ColorEnvelope
import com.github.skydoves.colorpicker.compose.ColorPickerController
import com.github.skydoves.colorpicker.compose.HsvColorPicker

class ColorPickerActivity : AppCompatActivity() {
    private lateinit var controller: ColorPickerController
    private var strokeWidthValue: Float = 5f
    private var selectedColor by mutableIntStateOf(Color.WHITE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.colorpicker)

        controller = ColorPickerController()
        val colorPickerContainer = findViewById<FrameLayout>(R.id.colorPickerContainer)
        val confirmButton = findViewById<Button>(R.id.confirmButton)

        // Teljes képernyős mód beállítása + navigációs sáv elrejtése
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)

        colorPickerContainer.addView(ComposeView(this).apply {
            setContent {
                var strokeWidth by remember { mutableFloatStateOf(5f) }

                Column {
                    HsvColorPicker(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp)
                            .padding(10.dp),
                        controller = controller,
                        onColorChanged = { colorEnvelope: ColorEnvelope ->
                            selectedColor = Color.argb(
                                colorEnvelope.color.alpha,
                                colorEnvelope.color.red,
                                colorEnvelope.color.green,
                                colorEnvelope.color.blue
                            )
                        }
                    )

                    Row(modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp), // Hozzáadott padding a jobb elrendezés érdekében
                        horizontalArrangement = Arrangement.Center // Vízszintes középre igazítás
                    ) {
                        AlphaTile(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(6.dp)),
                            controller = controller
                        )
                    }

                    Text(
                        text = "Line Thickness",
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(), // Ez biztosítja, hogy a szöveg a teljes szélességet elfoglalja
                    style = TextStyle(
                            textAlign = TextAlign.Center, // Szöveg középre igazítása
                            fontSize = 22.sp, // Szöveg méretének növelése
                            color = androidx.compose.ui.graphics.Color.White
                        )
                    )

                    Slider(
                        value = strokeWidth,
                        onValueChange = {
                            strokeWidth = it
                            strokeWidthValue = it
                        },
                        valueRange = 5f..10f,
                        colors = SliderDefaults.colors(
                            thumbColor = androidx.compose.ui.graphics.Color(selectedColor),
                            activeTrackColor = androidx.compose.ui.graphics.Color(selectedColor)
                        )
                    )
                }
            }
        })

        confirmButton.setOnClickListener {
            val data = Intent().apply {
                putExtra("selectedColor", selectedColor)
                putExtra("strokeWidth", strokeWidthValue)
            }
            setResult(Activity.RESULT_OK, data)
            finish()
        }
    }
}