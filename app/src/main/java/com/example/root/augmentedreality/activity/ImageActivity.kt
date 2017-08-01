package com.example.root.augmentedreality.activity

import android.support.v7.app.AppCompatActivity
import android.os.Bundle

import com.example.root.augmentedreality.R
import android.graphics.Bitmap
import android.os.Parcelable
import kotlinx.android.synthetic.main.activity_image.*

class ImageActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image)

        val intent = intent
        val bitmap = intent.getParcelableExtra<Parcelable>("BitmapImage") as Bitmap

        image_screen_shot.setImageBitmap(bitmap)
    }
}
