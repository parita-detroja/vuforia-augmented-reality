@file:Suppress("DEPRECATION")

package com.example.root.augmentedreality.locationhelper

import android.app.Activity
import android.content.Context
import android.hardware.Camera
import android.opengl.Matrix
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import java.io.IOException

/**
 * Created by root on 5/7/17.
 */
class ARCamera (context: Context, surfaceView: SurfaceView) : ViewGroup(context), SurfaceHolder.Callback
{
    private val TAG: String = "ARCamera"

    var activity: Activity

    var surfaceHolder: SurfaceHolder

    @Suppress("DEPRECATION")
    var previewSize: Camera.Size? = null

    @Suppress("DEPRECATION")
    var currentCamera: Camera? = null

    @Suppress("DEPRECATION")
    var parameters: Camera.Parameters? = null

    @Suppress("DEPRECATION")
    lateinit var supportedPreviewSizes: MutableList<Camera.Size>

    var projectionMatrix: FloatArray = kotlin.FloatArray(16)

    var cameraWidth: Int = 0
    var cameraHeight: Int = 0
    private val Z_NEAR: Float = 0.5f
    private val Z_FAR: Float = 2000f

    init
    {
        this.activity = context as Activity
        surfaceHolder = surfaceView.holder
        surfaceHolder.addCallback(this)
        @Suppress("DEPRECATION")
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)
    }

    @Suppress("DEPRECATION") fun setCamera(camera: Camera?)
    {
        this.currentCamera = camera
        supportedPreviewSizes = currentCamera?.parameters?.supportedPreviewSizes ?: emptyList<Camera.Size>() as MutableList<Camera.Size>
        requestLayout()
        val params: Camera.Parameters = currentCamera!!.parameters

        val focusMode: List<String> = params.supportedFocusModes

        if(focusMode.contains(Camera.Parameters.FOCUS_MODE_AUTO))
        {
            params.focusMode = Camera.Parameters.FOCUS_MODE_AUTO
            currentCamera!!.parameters = params
        }


    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int)
    {

        val width = resolveSize(suggestedMinimumWidth, widthMeasureSpec)
        val height = resolveSize(suggestedMinimumHeight, heightMeasureSpec)
        setMeasuredDimension(width, height)

        previewSize = getOptimalPreviewSize(supportedPreviewSizes, width, height)

    }

    @Suppress("DEPRECATION")
    private fun getOptimalPreviewSize(sizes: List<Camera.Size>, width: Int, height: Int) : Camera.Size?
    {
        val ASPECT_TOLERANCE = 0.1
        val targetRatio: Double = (width/height).toDouble()

        var optimalSize: Camera.Size? = null
        var minDiff: Double = Double.MAX_VALUE

        val targetHeight: Int = height

        for (size in sizes)
        {
            val ratio = size.width.toDouble() / size.height
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE)
            {
                continue
            }

            if (Math.abs(size.height - targetHeight) < minDiff)
            {
                optimalSize = size
                minDiff = Math.abs(size.height - targetHeight).toDouble()
            }
        }

        if (optimalSize == null)
        {
            minDiff = Double.MAX_VALUE
            for (size in sizes)
            {
                if (Math.abs(size.height - targetHeight) < minDiff)
                {
                    optimalSize = size
                    minDiff = Math.abs(size.height - targetHeight).toDouble()
                }
            }
        }

        if (optimalSize == null)
        {
            optimalSize = sizes[0]
        }

        return optimalSize
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int)
    {
        if (currentCamera != null)
        {
            this.cameraWidth = width
            this.cameraHeight = height

            val params = currentCamera!!.parameters
            params.setPreviewSize(previewSize!!.width, previewSize!!.height)
            requestLayout()

            currentCamera!!.parameters = params
            currentCamera!!.startPreview()

            generateProjectionMatrix()
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder?)
    {
        if(currentCamera != null)
        {
            currentCamera!!.setPreviewCallback(null)
            currentCamera!!.stopPreview()
            currentCamera!!.release()
            currentCamera = null
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder)
    {
        try
        {
            if (currentCamera != null)
            {

                parameters = currentCamera!!.parameters

                val orientation = getCameraOrientation()

                currentCamera!!.setDisplayOrientation(orientation)
                currentCamera!!.parameters.setRotation(orientation)

                currentCamera!!.setPreviewDisplay(holder)
            }
        } catch (exception: IOException)
        {
            Log.e(TAG, "IOException caused by setPreviewDisplay()", exception)
        }

    }

    @Suppress("DEPRECATION")
    private fun getCameraOrientation(): Int
    {
        val info = Camera.CameraInfo()
        Camera.getCameraInfo(Camera.CameraInfo.CAMERA_FACING_BACK, info)

        val rotation = activity.windowManager.defaultDisplay.rotation

        var degrees = 0
        when (rotation)
        {
            Surface.ROTATION_0 -> degrees = 0
            Surface.ROTATION_90 -> degrees = 90
            Surface.ROTATION_180 -> degrees = 180
            Surface.ROTATION_270 -> degrees = 270
        }

        var orientation: Int
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT)
        {
            orientation = (info.orientation + degrees) % 360
            orientation = (360 - orientation) % 360
        } else
        {
            orientation = (info.orientation - degrees + 360) % 360
        }

        return orientation
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int)
    {
        if (changed && childCount > 0)
        {
            val child = getChildAt(0)

            val width = right - left
            val height = bottom - top

            var previewWidth = width
            var previewHeight = height
            if (previewSize != null)
            {
                previewWidth = previewSize!!.width
                previewHeight = previewSize!!.height
            }

            if (width * previewHeight > height * previewWidth)
            {
                val scaledChildWidth = previewWidth * height / previewHeight
                child.layout((width - scaledChildWidth) / 2, 0,
                        (width + scaledChildWidth) / 2, height)
            } else
            {
                val scaledChildHeight = previewHeight * width / previewWidth
                child.layout(0, (height - scaledChildHeight) / 2,
                        width, (height + scaledChildHeight) / 2)
            }
        }
    }

    private fun generateProjectionMatrix()
    {
        Log.e("camera width",this.cameraWidth.toString())
        Log.e("camera height",this.cameraHeight.toString())
        val ratio: Float = (this.cameraWidth).toFloat() / this.cameraHeight
        val OFFSET: Int = 0
        val LEFT: Float = -ratio
        val RIGHT: Float = ratio
        val BOTTOM: Float = -1f
        val TOP: Float = 1f
        Log.e("LEFT",LEFT.toString())
        Log.e("RIGHT",RIGHT.toString())
        Matrix.frustumM(projectionMatrix, OFFSET, LEFT, RIGHT, BOTTOM, TOP, Z_NEAR, Z_FAR)
    }

}