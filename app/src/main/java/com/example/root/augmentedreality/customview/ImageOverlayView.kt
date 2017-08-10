package com.example.root.augmentedreality.customview

import android.content.Context
import android.graphics.Bitmap
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.RelativeLayout
import com.example.root.augmentedreality.R

/**
 * Created by root on 10/8/17.
 * Inflate image view.
 */
class ImageOverlayView(context: Context, attributeSet: AttributeSet? = null, defStyle: Int = 0) :
        RelativeLayout(context, attributeSet, defStyle)
{
    init
    {
        inflateLayout(context)
    }

    // Inflates the Custom View Layout.
    private fun inflateLayout(context: Context) {

        val inflater = LayoutInflater.from(context)

        // Generates the layout for the image view.
        inflater.inflate(R.layout.bitmap_layout, this, true)
    }

    fun setImageViewFromBitmap(coverBook: Bitmap) {
        val iv = findViewById(R.id.custom_view_image) as ImageView
        iv.setImageBitmap(coverBook)
    }

}