package com.wardanger3.gps_app.interfaces

interface IFinish {
    enum class DisplayMode {
        Original, Transformed, Both
    }

    companion object {
        const val COLOR_PICKER_REQUEST = 1
    }

    fun drawTransformedImage()
    fun drawBothImages()
    fun updateChangeButtonText()
    fun drawOriginalImage()
    fun loadImage()
    fun updateImageDisplay()
    fun resetAppState()
}