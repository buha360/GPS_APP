package com.example.gps_app

import android.util.Log

class SVGPathfinder (private val pathData: List<String>){

    fun asd(){
        Log.d("MyApp: findWhitePaths() - drawingPath: ", pathData.toString())
    }

    companion object {
        private var instance: SVGPathfinder? = null
        fun getInstance(): SVGPathfinder? {
            return instance
        }
    }

}