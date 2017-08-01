package com.example.root.augmentedreality.activity

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.hardware.Camera
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import com.example.root.augmentedreality.R
import com.example.root.augmentedreality.renderer.TextRecognitionRenderer
import com.example.root.augmentedreality.utility.LoadingDialogHandler
import com.example.root.augmentedreality.utility.SampleUtils
import com.example.root.augmentedreality.vuforia.ApplicationControl
import com.example.root.augmentedreality.vuforia.ApplicationException
import com.example.root.augmentedreality.vuforia.ApplicationSession
import com.example.root.textrecognitionar.utils.ApplicationGLView
import com.vuforia.*
import java.util.*

class TextRecognitionActivity : Activity(), ApplicationControl {

    internal var vuforiaAppSession: ApplicationSession? = null

    // Our OpenGL view:
    private var mGlView: ApplicationGLView? = null

    // Our renderer:
    private var mRenderer: TextRecognitionRenderer? = null

    private var mSettingsAdditionalViews: ArrayList<View>? = null

    private var mUILayout: RelativeLayout? = null

    private val loadingDialogHandler = LoadingDialogHandler(
            this)
    private val mIsTablet = false

    private var mIsVuforiaStarted = false

    private var mGestureDetector: GestureDetector? = null

    // Alert Dialog used to display SDK errors
    private var mErrorDialog: AlertDialog? = null

    // Called when the activity first starts or the user navigates back to an
    // activity.
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(LOGTAG, "onCreate")
        super.onCreate(savedInstanceState)

        vuforiaAppSession = ApplicationSession(this)

        startLoadingAnimation()

        vuforiaAppSession!!
                .initAR(this, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)

        mGestureDetector = GestureDetector(this, GestureListener())

    }

    // Process Single Tap event to trigger autofocus
    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        // Used to set autofocus one second after a manual focus is triggered
        private val autofocusHandler = Handler()

        override fun onDown(e: MotionEvent): Boolean {
            return true
        }


        override fun onSingleTapUp(e: MotionEvent): Boolean {
            // Generates a Handler to trigger autofocus
            // after 1 second
            autofocusHandler.postDelayed({
                val result = CameraDevice.getInstance().setFocusMode(
                        CameraDevice.FOCUS_MODE.FOCUS_MODE_TRIGGERAUTO)

                if (!result)
                    Log.e("SingleTapUp", "Unable to trigger focus")
            }, 1000L)

            return true
        }
    }


    // Called when the activity will start interacting with the user.
    override fun onResume() {
        Log.d(LOGTAG, "onResume")
        super.onResume()

        try {
            vuforiaAppSession!!.resumeAR()
        } catch (e: ApplicationException) {
            Log.e(LOGTAG, e.string)
        }

        if (mIsVuforiaStarted)
            postStartCamera()

        // Resume the GL view:
        if (mGlView != null) {
            mGlView!!.visibility = View.VISIBLE
            mGlView!!.onResume()
        }

    }


    // Callback for configuration changes the activity handles itself
    override fun onConfigurationChanged(config: Configuration) {
        Log.d(LOGTAG, "onConfigurationChanged")
        super.onConfigurationChanged(config)

        vuforiaAppSession!!.onConfigurationChanged()

        mRenderer!!.updateConfiguration()

        if (mIsVuforiaStarted)
            configureVideoBackgroundROI()
    }

    // Called when the system is about to start resuming a previous activity.
    override fun onPause() {
        Log.d(LOGTAG, "onPause")
        super.onPause()

        if (mGlView != null) {
            mGlView!!.visibility = View.INVISIBLE
            mGlView!!.onPause()
        }

        try {
            vuforiaAppSession!!.pauseAR()
        } catch (e: ApplicationException) {
            Log.e(LOGTAG, e.string)
        }

        stopCamera()
    }


    // The final call you receive before your activity is destroyed.
    override fun onDestroy() {
        Log.d(LOGTAG, "onDestroy")
        super.onDestroy()

        try {
            vuforiaAppSession!!.stopAR()
        } catch (e: ApplicationException) {
            Log.e(LOGTAG, e.string)
        }

        System.gc()
    }


    private fun startLoadingAnimation() {
        val inflater = LayoutInflater.from(this)
        mUILayout = inflater.inflate(
                R.layout.camera_overlay_textreco, null, false) as RelativeLayout

        mUILayout!!.visibility = View.VISIBLE
        mUILayout!!.setBackgroundColor(Color.BLACK)

        // Gets a reference to the loading dialog
        loadingDialogHandler.mLoadingDialogContainer = mUILayout!!
                .findViewById(R.id.loading_indicator)

        // Shows the loading indicator at start
        loadingDialogHandler
                .sendEmptyMessage(LoadingDialogHandler.SHOW_LOADING_DIALOG)

        // Adds the inflated layout to the view
        addContentView(mUILayout, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT))

    }


    // Initializes AR application components.
    private fun initApplicationAR() {
        // Create OpenGL ES view:
        val depthSize = 16
        val stencilSize = 0
        val translucent = Vuforia.requiresAlpha()

        mGlView = ApplicationGLView(this)
        mGlView!!.init(translucent, depthSize, stencilSize)

        mRenderer = TextRecognitionRenderer(this, vuforiaAppSession!!)
        mGlView!!.setRenderer(mRenderer)

        showLoupe(false)

    }


    private fun postStartCamera() {
        // Sets the layout background to transparent
        mUILayout!!.setBackgroundColor(Color.TRANSPARENT)

        // start the image tracker now that the camera is started
        val t = TrackerManager.getInstance().getTracker(
                TextTracker.getClassType())
        t?.start()

        configureVideoBackgroundROI()
    }


    fun configureVideoBackgroundROI() {
        val vm = CameraDevice.getInstance().getVideoMode(
                CameraDevice.MODE.MODE_DEFAULT)
        val config = Renderer.getInstance()
                .videoBackgroundConfig

        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels

        run {
            // calc ROI
            // width of margin is :
            // 5% of the width of the screen for a phone
            // 20% of the width of the screen for a tablet
            val marginWidth = if (mIsTablet)
                screenWidth * 20 / 100
            else
                screenWidth * 5 / 100

            // loupe height is :
            // 15% of the screen height for a phone
            // 10% of the screen height for a tablet
            val loupeHeight = if (mIsTablet)
                screenHeight * 10 / 100
            else
                screenHeight * 15 / 100

            // lupue width takes the width of the screen minus 2 margins
            val loupeWidth = screenWidth - 2 * marginWidth

            // definition of the region of interest
            mRenderer!!.setROI((screenWidth / 2).toFloat(), (marginWidth + loupeHeight / 2).toFloat(),
                    loupeWidth.toFloat(), loupeHeight.toFloat())
        }

        // Get the camera rotation
        val cameraDirection: Int
        when (CameraDevice.getInstance().cameraDirection) {
            CameraDevice.CAMERA_DIRECTION.CAMERA_DIRECTION_BACK -> cameraDirection = Camera.CameraInfo.CAMERA_FACING_BACK
            CameraDevice.CAMERA_DIRECTION.CAMERA_DIRECTION_FRONT -> cameraDirection = Camera.CameraInfo.CAMERA_FACING_FRONT
            else -> cameraDirection = Camera.CameraInfo.CAMERA_FACING_BACK
        }
        var cameraRotation = 0
        val numberOfCameras = Camera.getNumberOfCameras()
        for (i in 0..numberOfCameras - 1) {
            val cameraInfo = Camera.CameraInfo()
            Camera.getCameraInfo(i, cameraInfo)
            if (cameraInfo.facing == cameraDirection) {
                cameraRotation = cameraInfo.orientation
                break
            }
        }

        // Get the display rotation
        val display = (this.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        val displayRotation = display.rotation

        // convert into camera coords
        val loupeCenterX = intArrayOf(0)
        val loupeCenterY = intArrayOf(0)
        val loupeWidth = intArrayOf(0)
        val loupeHeight = intArrayOf(0)
        SampleUtils.screenCoordToCameraCoord(mRenderer!!.ROICenterX.toInt(),
                mRenderer!!.ROICenterY.toInt(), mRenderer!!.ROIWidth.toInt(),
                mRenderer!!.ROIHeight.toInt(), screenWidth, screenHeight,
                vm.width, vm.height, loupeCenterX, loupeCenterY,
                loupeWidth, loupeHeight, displayRotation, cameraRotation)

        // Compute the angle by which the camera image should be rotated clockwise so that it is
        // shown correctly on the display given its current orientation.
        val correctedRotation = (displayRotation * 90 - cameraRotation + 360) % 360 / 90

        val upDirection: Int
        when (correctedRotation) {
            0 -> upDirection = TextTracker.UP_DIRECTION.REGIONOFINTEREST_UP_IS_0_HRS
            1 -> upDirection = TextTracker.UP_DIRECTION.REGIONOFINTEREST_UP_IS_3_HRS
            2 -> upDirection = TextTracker.UP_DIRECTION.REGIONOFINTEREST_UP_IS_6_HRS
            3 -> upDirection = TextTracker.UP_DIRECTION.REGIONOFINTEREST_UP_IS_9_HRS
            else -> upDirection = TextTracker.UP_DIRECTION.REGIONOFINTEREST_UP_IS_9_HRS
        }

        val detROI = RectangleInt(loupeCenterX[0] - loupeWidth[0] / 2, loupeCenterY[0] - loupeHeight[0] / 2,
                loupeCenterX[0] + loupeWidth[0] / 2, loupeCenterY[0] + loupeHeight[0] / 2)

        val tt = TrackerManager.getInstance().getTracker(
                TextTracker.getClassType()) as TextTracker?

        tt?.setRegionOfInterest(detROI, detROI,
                upDirection)

        mRenderer!!.setViewport(0, 0, metrics.widthPixels, metrics.heightPixels)
    }


    private fun stopCamera() {
        doDeInitTrackerData()

        CameraDevice.getInstance().stop()
        CameraDevice.getInstance().deinit()
    }


    fun updateWordListUI(words: List<TextRecognitionRenderer.WordDesc>) {
        runOnUiThread {
            val wordListLayout = mUILayout!!
                    .findViewById(R.id.wordList) as RelativeLayout
            wordListLayout.removeAllViews()

            if (words.size > 0) {
                val params = wordListLayout.layoutParams
                // Changes the height and width to the specified *pixels*
                val maxTextHeight = params.height - 2 * WORDLIST_MARGIN

                val textInfo = fontSizeForTextHeight(maxTextHeight,
                        words.size, params.width, 32, 12)

                var count = -1
                val nbWords = textInfo[2] // number of words we can display
                var previousView: TextView? = null
                var tv: TextView
                for (word in words) {
                    count++
                    if (count == nbWords) {
                        break
                    }
                    tv = TextView(this@TextRecognitionActivity)
                    tv.text = word.text
                    val txtParams = RelativeLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT)

                    if (previousView != null)
                        txtParams.addRule(RelativeLayout.BELOW,
                                previousView.id)

                    txtParams.setMargins(0, if (count == 0)
                        WORDLIST_MARGIN
                    else
                        0, 0, if (count == nbWords - 1)
                        WORDLIST_MARGIN
                    else
                        0)
                    tv.layoutParams = txtParams
                    tv.gravity = Gravity.CENTER_VERTICAL or Gravity.CENTER_HORIZONTAL
                    tv.textSize = textInfo[0].toFloat()
                    tv.setTextColor(Color.WHITE)
                    tv.height = textInfo[1]
                    tv.id = count + 100

                    wordListLayout.addView(tv)
                    previousView = tv
                }
            }
        }
    }


    private fun showLoupe(isActive: Boolean) {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels

        // width of margin is :
        // 5% of the width of the screen for a phone
        // 20% of the width of the screen for a tablet
        val marginWidth = if (mIsTablet) width * 20 / 100 else width * 5 / 100

        // loupe height is :
        // 33% of the screen height for a phone
        // 20% of the screen height for a tablet
        val loupeHeight = if (mIsTablet) height * 10 / 100 else height * 15 / 100

        // lupue width takes the width of the screen minus 2 margins
        val loupeWidth = width - 2 * marginWidth

        val wordListHeight = height - (loupeHeight + marginWidth)

        // definition of the region of interest
        mRenderer!!.setROI((width / 2).toFloat(), (marginWidth + loupeHeight / 2).toFloat(),
                loupeWidth.toFloat(), loupeHeight.toFloat())

        // Gets a reference to the loading dialog
        val loadingIndicator = mUILayout!!.findViewById(R.id.loading_indicator)

        val loupeLayout = mUILayout!!
                .findViewById(R.id.loupeLayout) as RelativeLayout

        val topMargin = mUILayout!!
                .findViewById(R.id.topMargin) as ImageView

        val leftMargin = mUILayout!!
                .findViewById(R.id.leftMargin) as ImageView

        val rightMargin = mUILayout!!
                .findViewById(R.id.rightMargin) as ImageView

        val loupeArea = mUILayout!!.findViewById(R.id.loupe) as ImageView

        val wordListLayout = mUILayout!!
                .findViewById(R.id.wordList) as RelativeLayout

        wordListLayout.setBackgroundColor(COLOR_OPAQUE)

        if (isActive) {
            topMargin.layoutParams.height = marginWidth
            topMargin.layoutParams.width = width

            leftMargin.layoutParams.width = marginWidth
            leftMargin.layoutParams.height = loupeHeight

            rightMargin.layoutParams.width = marginWidth
            rightMargin.layoutParams.height = loupeHeight

            var params: RelativeLayout.LayoutParams

            params = loupeLayout
                    .layoutParams as RelativeLayout.LayoutParams
            params.height = loupeHeight
            loupeLayout.layoutParams = params

            loupeArea.layoutParams.width = loupeWidth
            loupeArea.layoutParams.height = loupeHeight
            loupeArea.visibility = View.VISIBLE

            params = wordListLayout
                    .layoutParams as RelativeLayout.LayoutParams
            params.height = wordListHeight
            params.width = width
            wordListLayout.layoutParams = params

            loadingIndicator.visibility = View.GONE
            loupeArea.visibility = View.VISIBLE
            topMargin.visibility = View.VISIBLE
            loupeLayout.visibility = View.VISIBLE
            wordListLayout.visibility = View.VISIBLE

        } else {
            loadingIndicator.visibility = View.VISIBLE
            loupeArea.visibility = View.GONE
            topMargin.visibility = View.GONE
            loupeLayout.visibility = View.GONE
            wordListLayout.visibility = View.GONE
        }

    }


    // the funtions returns 3 values in an array of ints
    // [0] : the text size
    // [1] : the text component height
    // [2] : the number of words we can display
    private fun fontSizeForTextHeight(totalTextHeight: Int, nbWords: Int,
                                      textWidth: Int, textSizeMax: Int, textSizeMin: Int): IntArray {

        val result = IntArray(3)
        val text = "Agj"
        val tv = TextView(this)
        tv.text = text
        tv.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT)
        tv.gravity = Gravity.CENTER_VERTICAL or Gravity.CENTER_HORIZONTAL
        // tv.setTextSize(30);
        // tv.setHeight(textHeight);
        var textSize = 0
        var layoutHeight = 0

        val densityMultiplier = resources.displayMetrics.density

        textSize = textSizeMax
        while (textSize >= textSizeMin) {
            // Get the font size setting
            val fontScale = Settings.System.getFloat(contentResolver,
                    Settings.System.FONT_SCALE, 1.0f)
            // Text view line spacing multiplier
            val spacingMult = 1.0f * fontScale
            // Text view additional line spacing
            val spacingAdd = 0.0f
            val paint = TextPaint(tv.paint)
            paint.textSize = textSize * densityMultiplier
            // Measure using a static layout
            val layout = StaticLayout(text, paint, textWidth,
                    Layout.Alignment.ALIGN_NORMAL, spacingMult, spacingAdd, true)
            layoutHeight = layout.height
            if (layoutHeight * nbWords < totalTextHeight) {
                result[0] = textSize
                result[1] = layoutHeight
                result[2] = nbWords
                return result
            }
            textSize -= 2
        }

        // we won't be able to display all the fonts
        result[0] = textSize
        result[1] = layoutHeight
        result[2] = totalTextHeight / layoutHeight
        return result
    }


    override fun onInitARDone(mApplicationException: ApplicationException?) {

        if (mApplicationException == null) {
            initApplicationAR()

            // Hint to the virtual machine that it would be a good time to
            // run the garbage collector:
            //
            // NOTE: This is only a hint. There is no guarantee that the
            // garbage collector will actually be run.
            System.gc()

            mRenderer!!.setActive(true)

            // Now add the GL surface view. It is important
            // that the OpenGL ES surface view gets added
            // BEFORE the camera is started and video
            // background is configured.
            addContentView(mGlView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT))

            // Hides the Loading Dialog
            loadingDialogHandler
                    .sendEmptyMessage(LoadingDialogHandler.HIDE_LOADING_DIALOG)
            showLoupe(true)

            // Sets the UILayout to be drawn in front of the camera
            mUILayout!!.bringToFront()

            try {
                vuforiaAppSession!!.startAR(CameraDevice.CAMERA_DIRECTION.CAMERA_DIRECTION_DEFAULT)
            } catch (e: ApplicationException) {
                Log.e(LOGTAG, e.string)
            }

            mIsVuforiaStarted = true

            postStartCamera()

            setSampleAppMenuAdditionalViews()


        } else {
            Log.e(LOGTAG, mApplicationException.string)
            showInitializationErrorMessage(mApplicationException.string)
        }
    }


    // Shows initialization error messages as System dialogs
    fun showInitializationErrorMessage(message: String) {
        val errorMessage = message
        runOnUiThread {
            if (mErrorDialog != null) {
                mErrorDialog!!.dismiss()
            }

            // Generates an Alert Dialog to show the error message
            val builder = AlertDialog.Builder(
                    this@TextRecognitionActivity)
            builder
                    .setMessage(errorMessage)
                    .setTitle(getString(R.string.INIT_ERROR))
                    .setCancelable(false)
                    .setIcon(0)
                    .setPositiveButton(getString(R.string.button_OK)
                    ) { dialog, id -> finish() }

            mErrorDialog = builder.create()
            mErrorDialog!!.show()
        }
    }


    // Functions to load and destroy tracking data.
    override fun doLoadTrackersData(): Boolean {
        val tm = TrackerManager.getInstance()
        val tt = tm
                .getTracker(TextTracker.getClassType()) as TextTracker
        val wl = tt.wordList

        return wl.loadWordList("TextReco/Vuforia-English-word.vwl",
                STORAGE_TYPE.STORAGE_APPRESOURCE)
    }

    override fun doStartTracker(): Boolean {
        // Indicate if the trackers were started correctly
        val result = true

        val textTracker = TrackerManager.getInstance().getTracker(
                TextTracker.getClassType())
        textTracker?.start()

        return result
    }

    override fun doStopTracker(): Boolean {
        // Indicate if the trackers were stopped correctly
        val result = true

        val textTracker = TrackerManager.getInstance().getTracker(
                TextTracker.getClassType())
        textTracker?.stop()

        return result
    }


    override fun doUnloadTrackersData(): Boolean {
        // Indicate if the trackers were unloaded correctly
        val result = true
        val tm = TrackerManager.getInstance()
        val tt = tm.getTracker(TextTracker.getClassType()) as TextTracker?

        if (tt != null) {
            val wl = tt.wordList
            wl.unloadAllLists()
        }

        return result
    }

    override fun doDeInitTrackerData(): Boolean {
        // Indicate if the trackers were deinitialized correctly
        val result = true
        Log.e(LOGTAG, "UnloadTrackersData")

        val tManager = TrackerManager.getInstance()
        tManager.deinitTracker(TextTracker.getClassType())

        return result
    }

    override fun onVuforiaUpdate(state: State) {}


    override fun doInitTrackers(): Boolean {
        val tManager = TrackerManager.getInstance()
        val tracker: Tracker?

        // Indicate if the trackers were initialized correctly
        var result = true

        tracker = tManager.initTracker(TextTracker.getClassType())
        if (tracker == null) {
            Log.e(
                    LOGTAG,
                    "Tracker not initialized. Tracker already initialized or the camera is already started")
            result = false
        } else {
            Log.i(LOGTAG, "Tracker successfully initialized")
        }

        return result
    }


    // This method sets the additional views to be moved along with the GLView
    private fun setSampleAppMenuAdditionalViews() {
        mSettingsAdditionalViews = ArrayList<View>()
        mSettingsAdditionalViews!!.add(mUILayout!!.findViewById(R.id.topMargin))
        mSettingsAdditionalViews!!.add(mUILayout!!.findViewById(R.id.loupeLayout))
        mSettingsAdditionalViews!!.add(mUILayout!!.findViewById(R.id.wordList))
    }

    companion object {
        private val LOGTAG = "MainActivity"

        private val COLOR_OPAQUE = Color.argb(178, 0, 0, 0)
        private val WORDLIST_MARGIN = 10

    }

}