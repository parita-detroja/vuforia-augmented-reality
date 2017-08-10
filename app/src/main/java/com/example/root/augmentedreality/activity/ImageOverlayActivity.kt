package com.example.root.augmentedreality.activity

import android.app.Activity
import android.app.AlertDialog
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.TranslateAnimation
import android.widget.Button
import android.widget.RelativeLayout
import android.widget.TextView
import com.example.root.augmentedreality.R
import com.example.root.augmentedreality.customview.ImageOverlayView
import com.example.root.augmentedreality.renderer.ImageOverlayRenderer
import com.example.root.augmentedreality.utility.LoadingDialogHandler
import com.example.root.augmentedreality.utility.Texture
import com.example.root.augmentedreality.vuforia.ApplicationControl
import com.example.root.augmentedreality.vuforia.ApplicationException
import com.example.root.augmentedreality.vuforia.ApplicationSession
import com.example.root.textrecognitionar.utils.ApplicationGLView
import com.vuforia.*
import java.lang.ref.WeakReference

/**
 * Created by root on 9/8/17.
 * The main activity for the Books sample.
 */
class ImageOverlayActivity : Activity(), ApplicationControl
{
    override fun doStartTracker(): Boolean
    {
        // Indicate if the trackers were started correctly
        val result = true

        // Start the tracker:
        val trackerManager = TrackerManager.getInstance()
        val objectTracker = trackerManager
                .getTracker(ObjectTracker.getClassType()) as ObjectTracker
        objectTracker.start()

        // Start cloud based recognition if we are in scanning mode:
        if (mRenderer!!.scanningMode)
        {
            val targetFinder = objectTracker.targetFinder
            targetFinder.startRecognition()
        }

        return result
    }

    override fun doStopTracker(): Boolean {
        // Indicate if the trackers were stopped correctly
        var result = true

        val trackerManager = TrackerManager.getInstance()
        val objectTracker = trackerManager
                .getTracker(ObjectTracker.getClassType()) as ObjectTracker?

        if (objectTracker != null)
        {
            objectTracker.stop()

            // Stop cloud based recognition:
            val targetFinder = objectTracker.targetFinder
            targetFinder.stop()

            // Clears the trackables
            targetFinder.clearTrackables()
        } else
        {
            result = false
        }

        return result
    }

    override fun doDeInitTrackerData(): Boolean {
        // Indicate if the trackers were deinitialized correctly
        val result = true

        val tManager = TrackerManager.getInstance()
        tManager.deinitTracker(ObjectTracker.getClassType())

        return result
    }

    internal lateinit var vuforiaAppSession: ApplicationSession

    // Status Bar Text
    private var mStatusBarText: String? = null

    /** Returns the current Book Data Texture  */
    var productTexture: Texture? = null

    // Indicates if the app is currently loading the book data
    private var mIsLoadingBookData = false

    // Our OpenGL view:
    private var mGlView: ApplicationGLView? = null

    // Our renderer:
    private var mRenderer: ImageOverlayRenderer? = null

    // View overlays to be displayed in the Augmented View
    private var mUILayout: RelativeLayout? = null
    private var mStatusBar: TextView? = null
    private var mCloseButton: Button? = null

    // Error message handling:
    private var mlastErrorCode = 0
    private var mInitErrorCode = 0
    private var mFinishActivityOnError: Boolean = false

    // Alert Dialog used to display SDK errors
    private var mErrorDialog: AlertDialog? = null

    // Detects the double tap gesture for launching the Camera menu
    private var mGestureDetector: GestureDetector? = null

    private var lastTargetId = ""

    // declare scan line and its animation
    private var scanLine: View? = null
    private var scanAnimation: TranslateAnimation? = null
    private var scanLineStarted: Boolean = false

    private var mBookInfoStatus = BOOKINFO_NOT_DISPLAYED

    var mBookDataTexture: Texture? = null

    fun deinitBooks()
    {
        // Get the object tracker:
        val trackerManager = TrackerManager.getInstance()
        val objectTracker = trackerManager
                .getTracker(ObjectTracker.getClassType()) as ObjectTracker?
        if (objectTracker == null)
        {
            Log.e(LOGTAG,
                    "Failed to destroy the tracking data set because the ObjectTracker has not" + " been initialized.")
            return
        }

        // Deinitialize target finder:
        val finder = objectTracker.targetFinder
        finder.deinit()
    }


    private fun initStateVariables()
    {
        mRenderer!!.renderState = ImageOverlayRenderer.RS_SCANNING
        mRenderer!!.setProductTexture(null)

        mRenderer!!.scanningMode = true
        mRenderer!!.isShowing2DOverlay(false)
        mRenderer!!.showAnimation3Dto2D(false)
        mRenderer!!.stopTransition3Dto2D()
        mRenderer!!.stopTransition2Dto3D()

        cleanTargetTrackedId()
    }


    /**
     * Function to generate the OpenGL Texture Object in the renderFrame thread
     */
    fun productTextureIsCreated()
    {
        mRenderer!!.renderState = ImageOverlayRenderer.RS_TEXTURE_GENERATED
    }


    /** Sets current device Scale factor based on screen dpi  */
    fun setDeviceDPIScaleFactor(dpiSIndicator: Float)
    {
        mRenderer!!.setDPIScaleIndicator(dpiSIndicator)

        // MDPI devices
        if (dpiSIndicator <= 1.0f)
        {
            mRenderer!!.setScaleFactor(1.6f)
        } else if (dpiSIndicator <= 1.5f)
        {
            mRenderer!!.setScaleFactor(1.3f)
        } else if (dpiSIndicator <= 2.0f)
        {
            mRenderer!!.setScaleFactor(1.0f)
        } else
        {
            mRenderer!!.setScaleFactor(0.6f)
        }
    }


    /** Cleans the lastTargetTrackerId variable  */
    fun cleanTargetTrackedId()
    {
        synchronized(lastTargetId)
        {
            lastTargetId = ""
        }
    }

    /**
     * Crates a Handler to Show/Hide the status bar overlay from an UI Thread
     */
    internal class StatusBarHandler(books: ImageOverlayActivity) : Handler()
    {
        private val mBooks: WeakReference<ImageOverlayActivity> = WeakReference(books)


        override fun handleMessage(msg: Message)
        {
            val books = mBooks.get() ?: return

            if (msg.what == SHOW_STATUS_BAR)
            {
                books.mStatusBar!!.text = books.mStatusBarText
                books.mStatusBar!!.visibility = View.VISIBLE
            } else
            {
                books.mStatusBar!!.visibility = View.GONE
            }
        }
    }

    private val statusBarHandler = StatusBarHandler(this)

    /**
     * Creates a handler to Show/Hide the UI Overlay from an UI thread
     */
    internal class Overlay2dHandler(books: ImageOverlayActivity) : Handler()
    {
        private val mBooks: WeakReference<ImageOverlayActivity> = WeakReference(books)


        override fun handleMessage(msg: Message)
        {
            val books = mBooks.get() ?: return

            if (books.mCloseButton != null)
            {
                if (msg.what == SHOW_2D_OVERLAY)
                {
                    books.mCloseButton!!.visibility = View.VISIBLE
                } else
                {
                    books.mCloseButton!!.visibility = View.GONE
                }
            }
        }
    }

    private val overlay2DHandler = Overlay2dHandler(this)

    private val loadingDialogHandler = LoadingDialogHandler(this)

    private val mLastErrorTime: Double = 0.toDouble()

    private var mdpiScaleIndicator: Float = 0.toFloat()

    private var mActivity: Activity? = null


    // Called when the activity first starts or needs to be recreated after
    // resuming the application or a configuration change.
    override fun onCreate(savedInstanceState: Bundle?)
    {
        Log.d(LOGTAG, "onCreate")
        super.onCreate(savedInstanceState)

        mActivity = this

        vuforiaAppSession = ApplicationSession(this)

        startLoadingAnimation()

        vuforiaAppSession
                .initAR(this, ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR)

        // Creates the GestureDetector listener for processing double tap
        mGestureDetector = GestureDetector(this, GestureListener())

        mdpiScaleIndicator = applicationContext.resources
                .displayMetrics.density

        // Use an OrientationChangeListener here to capture all orientation changes.  Android
        // will not send an Activity.onConfigurationChanged() callback on a 180 degree rotation,
        // ie: Left Landscape to Right Landscape.  Vuforia needs to react to this change and the
        // SampleApplicationSession needs to update the Projection Matrix.
        val orientationEventListener = object : OrientationEventListener(mActivity) {
            override fun onOrientationChanged(i: Int) {
                val activityRotation = mActivity!!.windowManager.defaultDisplay.rotation
                if (mLastRotation != activityRotation) {
                    // Update video background for 180 degree rotation
                    if (Math.abs(mLastRotation - activityRotation) == 2) {
                        mRenderer!!.updateVideoBackground()
                    }
                    mLastRotation = activityRotation
                }
            }

            internal var mLastRotation = -1
        }

        if (orientationEventListener.canDetectOrientation())
            orientationEventListener.enable()
    }


    // Called when the activity will start interacting with the user.
    override fun onResume()
    {
        Log.d(LOGTAG, "onResume")
        super.onResume()

        try
        {
            vuforiaAppSession.resumeAR()
        } catch (e: ApplicationException)
        {
            Log.e(LOGTAG, e.string)
        }

        // Resume the GL view:
        if (mGlView != null) {
            mGlView!!.visibility = View.VISIBLE
            mGlView!!.onResume()
        }

        mBookInfoStatus = BOOKINFO_NOT_DISPLAYED

        // By default the 2D Overlay is hidden
        hide2DOverlay()

        // Display scan line when app is resumed
        if (loadingDialogHandler.mLoadingDialogContainer.visibility != View.VISIBLE) {
            scanlineStart()
        }
    }


    // Callback for configuration changes the activity handles itself
    override fun onConfigurationChanged(config: Configuration)
    {
        Log.d(LOGTAG, "onConfigurationChanged")
        super.onConfigurationChanged(config)

        vuforiaAppSession.onConfigurationChanged()
        scanCreateAnimation()
    }


    // Called when the system is about to start resuming a previous activity.
    override fun onPause()
    {
        Log.d(LOGTAG, "onPause")
        super.onPause()

        try
        {
            vuforiaAppSession.pauseAR()
        } catch (e: ApplicationException)
        {
            Log.e(LOGTAG, e.string)
        }

        // When the camera stops it clears the Product Texture ID so next time
        // textures
        // Are recreated
        if (mRenderer != null)
        {
            mRenderer!!.deleteCurrentProductTexture()

            // Initialize all state Variables
            initStateVariables()
        }

        // Pauses the OpenGLView
        if (mGlView != null)
        {
            mGlView!!.visibility = View.INVISIBLE
            mGlView!!.onPause()
        }
    }


    // The final call you receive before your activity is destroyed.
    override fun onDestroy()
    {
        Log.d(LOGTAG, "onDestroy")
        super.onDestroy()

        try
        {
            vuforiaAppSession.stopAR()
        } catch (e: ApplicationException)
        {
            Log.e(LOGTAG, e.string)
        }

        System.gc()
    }


    private fun startLoadingAnimation()
    {
        // Inflates the Overlay Layout to be displayed above the Camera View
        val inflater = LayoutInflater.from(this)
        mUILayout = inflater.inflate(
                R.layout.activity_image_overlay, null, false) as RelativeLayout

        mUILayout!!.visibility = View.VISIBLE
        mUILayout!!.setBackgroundColor(Color.BLACK)

        // By default
        loadingDialogHandler.mLoadingDialogContainer = mUILayout!!
                .findViewById(R.id.loading_layout)
        loadingDialogHandler.mLoadingDialogContainer.visibility = View.VISIBLE

        addContentView(mUILayout, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT))

        // Gets a Reference to the Bottom Status Bar
        mStatusBar = mUILayout!!.findViewById(R.id.overlay_status) as TextView

        // Shows the loading indicator at start
        loadingDialogHandler
                .sendEmptyMessage(LoadingDialogHandler.SHOW_LOADING_DIALOG)

        // Gets a reference to the Close Button
        mCloseButton = mUILayout!!
                .findViewById(R.id.overlay_close_button) as Button

        // Sets the Close Button functionality
        mCloseButton!!.setOnClickListener {

            // Updates application status
            mBookInfoStatus = BOOKINFO_NOT_DISPLAYED

            loadingDialogHandler.sendEmptyMessage(HIDE_LOADING_DIALOG)

            // Checks if the app is currently loading a book data
            if (mIsLoadingBookData)
            {

                // Cancels the AsyncTask
                mIsLoadingBookData = false

                // Cleans the Target Tracker Id
                cleanTargetTrackedId()
            }

            // Enters Scanning Mode
            enterScanningMode()
        }

        // As default the 2D overlay and Status bar are hidden when application
        // starts
        hide2DOverlay()
        hideStatusBar()

        scanLine = mUILayout!!.findViewById(R.id.scan_line)
        scanLine!!.visibility = View.GONE
        scanLineStarted = false
        scanCreateAnimation()
    }


    // Initializes AR application components.
    private fun initApplicationAR()
    {
        // Create OpenGL ES view:
        val depthSize = 16
        val stencilSize = 0
        val translucent = Vuforia.requiresAlpha()

        // Initialize the GLView with proper flags
        mGlView = ApplicationGLView(this)
        mGlView!!.init(translucent, depthSize, stencilSize)

        // Setups the Renderer of the GLView
        mRenderer = ImageOverlayRenderer(this, vuforiaAppSession)
        mRenderer!!.mActivity = this
        mGlView!!.setRenderer(mRenderer)

        // Sets the device scale density
        setDeviceDPIScaleFactor(mdpiScaleIndicator)

        initStateVariables()
    }


    /** Sets the Status Bar Text in a UI thread  */
    fun setStatusBarText(statusText: String)
    {
        mStatusBarText = statusText
        statusBarHandler.sendEmptyMessage(SHOW_STATUS_BAR)
    }


    /** Hides the Status bar 2D Overlay in a UI thread  */
    fun hideStatusBar()
    {
        if (mStatusBar!!.visibility == View.VISIBLE)
        {
            statusBarHandler.sendEmptyMessage(HIDE_STATUS_BAR)
        }
    }


    /** Shows the Status Bar 2D Overlay in a UI thread  */
    fun showStatusBar()
    {
        if (mStatusBar!!.visibility == View.GONE)
        {
            statusBarHandler.sendEmptyMessage(SHOW_STATUS_BAR)
        }
    }


    /** Starts the WebView with the Book Extra Data  */
    /*fun startWebView(value: Int)
    {
        // Checks that we have a valid book data
        if (mBookData != null)
        {
            // Starts an Intent to open the book URL
            val viewIntent = Intent("android.intent.action.VIEW",
                    Uri.parse(mBookData!!.bookUrl))

            startActivity(viewIntent)
        }
    }*/


    /** Returns the error message for each error code  */
    private fun getStatusDescString(code: Int): String
    {
        if (code == UPDATE_ERROR_AUTHORIZATION_FAILED)
            return getString(R.string.UPDATE_ERROR_AUTHORIZATION_FAILED_DESC)
        if (code == UPDATE_ERROR_PROJECT_SUSPENDED)
            return getString(R.string.UPDATE_ERROR_PROJECT_SUSPENDED_DESC)
        if (code == UPDATE_ERROR_NO_NETWORK_CONNECTION)
            return getString(R.string.UPDATE_ERROR_NO_NETWORK_CONNECTION_DESC)
        if (code == UPDATE_ERROR_SERVICE_NOT_AVAILABLE)
            return getString(R.string.UPDATE_ERROR_SERVICE_NOT_AVAILABLE_DESC)
        if (code == UPDATE_ERROR_UPDATE_SDK)
            return getString(R.string.UPDATE_ERROR_UPDATE_SDK_DESC)
        if (code == UPDATE_ERROR_TIMESTAMP_OUT_OF_RANGE)
            return getString(R.string.UPDATE_ERROR_TIMESTAMP_OUT_OF_RANGE_DESC)
        if (code == UPDATE_ERROR_REQUEST_TIMEOUT)
            return getString(R.string.UPDATE_ERROR_REQUEST_TIMEOUT_DESC)
        if (code == UPDATE_ERROR_BAD_FRAME_QUALITY)
            return getString(R.string.UPDATE_ERROR_BAD_FRAME_QUALITY_DESC)
        else
        {
            return getString(R.string.UPDATE_ERROR_UNKNOWN_DESC)
        }
    }


    /** Returns the error message for each error code  */
    private fun getStatusTitleString(code: Int): String
    {
        if (code == UPDATE_ERROR_AUTHORIZATION_FAILED)
            return getString(R.string.UPDATE_ERROR_AUTHORIZATION_FAILED_TITLE)
        if (code == UPDATE_ERROR_PROJECT_SUSPENDED)
            return getString(R.string.UPDATE_ERROR_PROJECT_SUSPENDED_TITLE)
        if (code == UPDATE_ERROR_NO_NETWORK_CONNECTION)
            return getString(R.string.UPDATE_ERROR_NO_NETWORK_CONNECTION_TITLE)
        if (code == UPDATE_ERROR_SERVICE_NOT_AVAILABLE)
            return getString(R.string.UPDATE_ERROR_SERVICE_NOT_AVAILABLE_TITLE)
        if (code == UPDATE_ERROR_UPDATE_SDK)
            return getString(R.string.UPDATE_ERROR_UPDATE_SDK_TITLE)
        if (code == UPDATE_ERROR_TIMESTAMP_OUT_OF_RANGE)
            return getString(R.string.UPDATE_ERROR_TIMESTAMP_OUT_OF_RANGE_TITLE)
        if (code == UPDATE_ERROR_REQUEST_TIMEOUT)
            return getString(R.string.UPDATE_ERROR_REQUEST_TIMEOUT_TITLE)
        if (code == UPDATE_ERROR_BAD_FRAME_QUALITY)
            return getString(R.string.UPDATE_ERROR_BAD_FRAME_QUALITY_TITLE)
        else
        {
            return getString(R.string.UPDATE_ERROR_UNKNOWN_TITLE)
        }
    }


    // Shows error messages as System dialogs
    fun showErrorMessage(errorCode: Int, errorTime: Double, finishActivityOnError: Boolean)
    {
        if (errorTime < mLastErrorTime + 5.0 || errorCode == mlastErrorCode)
            return

        mlastErrorCode = errorCode
        mFinishActivityOnError = finishActivityOnError

        runOnUiThread {
            if (mErrorDialog != null)
            {
                mErrorDialog!!.dismiss()
            }

            // Generates an Alert Dialog to show the error message
            val builder = AlertDialog.Builder(
                    this@ImageOverlayActivity)
            builder
                    .setMessage(
                            getStatusDescString(this@ImageOverlayActivity.mlastErrorCode))
                    .setTitle(
                            getStatusTitleString(this@ImageOverlayActivity.mlastErrorCode))
                    .setCancelable(false)
                    .setIcon(0)
                    .setPositiveButton(getString(R.string.button_OK)
                    ) { dialog, id ->
                        if (mFinishActivityOnError)
                        {
                            finish()
                        } else
                        {
                            dialog.dismiss()
                        }
                    }

            mErrorDialog = builder.create()
            mErrorDialog!!.show()
        }
    }


    /**
     * Generates a texture for the book data fetching the book info from the
     * specified book URL
     */
    fun createProductTexture(bookJSONUrl: String) {

        // Cleans old texture reference if necessary
        if (mBookDataTexture != null) {
            mBookDataTexture = null

            System.gc()
        }

        mIsLoadingBookData = true

        // Shows the loading dialog
        loadingDialogHandler.sendEmptyMessage(SHOW_LOADING_DIALOG)

        mBookInfoStatus = BOOKINFO_NOT_DISPLAYED

        loadingDialogHandler.sendEmptyMessage(HIDE_LOADING_DIALOG)

        cleanTargetTrackedId()

        enterScanningMode()

        var productView: ImageOverlayView? = ImageOverlayView(
                this@ImageOverlayActivity)

        val imageBitmap: Bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.cloud_recognition)

        // Updates the view used as a 3d Texture
        updateProductView(productView!!,imageBitmap)

        // Sets the layout params
        productView.layoutParams = ViewGroup.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT)

        // Sets View measure - This size should be the same as the
        // texture generated to display the overlay in order for the
        // texture to be centered in screen
        productView.measure(View.MeasureSpec.makeMeasureSpec(mTextureSize,
                View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(
                mTextureSize, View.MeasureSpec.EXACTLY))

        // updates layout size
        productView.layout(0, 0, productView.getMeasuredWidth(),
                productView.getMeasuredHeight())

        // Draws the View into a Bitmap. Note we are allocating several
        // large memory buffers thus attempt to clear them as soon as
        // they are no longer required:
        var bitmap: Bitmap? = Bitmap.createBitmap(mTextureSize, mTextureSize,
                Bitmap.Config.ARGB_8888)

        var c: Canvas? = Canvas(bitmap!!)
        productView.draw(c)

        // Clear the product view as it is no longer needed
        productView = null
        System.gc()

        // Allocate int buffer for pixel conversion and copy pixels
        val width = bitmap.width
        val height = bitmap.height

        var data: IntArray? = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(data, 0, bitmap.width, 0, 0,
                bitmap.width, bitmap.height)

        // Recycle the bitmap object as it is no longer needed
        bitmap.recycle()
        bitmap = null
        c = null
        System.gc()

        // Generates the Texture from the int buffer
        mBookDataTexture = Texture.loadTextureFromIntBuffer(data,
                width, height)

        // Clear the int buffer as it is no longer needed
        data = null
        System.gc()

        // Hides the loading dialog from a UI thread
        loadingDialogHandler.sendEmptyMessage(HIDE_LOADING_DIALOG)

        mIsLoadingBookData = false

        productTextureIsCreated()
    }

    /** Updates a BookOverlayView with the Book data specified in parameters  */
    private fun updateProductView(productView: ImageOverlayView, bitmap: Bitmap)
    {
        productView.setImageViewFromBitmap(bitmap)
    }

    /**
     * Starts application content Mode Displays UI OVerlays and turns Cloud
     * Recognition off
     */
    fun enterContentMode()
    {
        // Updates state variables
        mBookInfoStatus = BOOKINFO_IS_DISPLAYED

        // Shows the 2D Overlay
        show2DOverlay()

        // Enters content mode to disable Cloud Recognition
        val trackerManager = TrackerManager.getInstance()
        val objectTracker = trackerManager
                .getTracker(ObjectTracker.getClassType()) as ObjectTracker
        val targetFinder = objectTracker.targetFinder

        // Stop Cloud Recognition
        targetFinder.stop()
        scanlineStop()

        // Remember we are in content mode:
        mRenderer!!.scanningMode = false
    }


    /** Hides the 2D Overlay view and starts C service again  */
    private fun enterScanningMode()
    {
        // Hides the 2D Overlay
        hide2DOverlay()

        // Enables Cloud Recognition Scanning Mode
        val trackerManager = TrackerManager.getInstance()
        val objectTracker = trackerManager
                .getTracker(ObjectTracker.getClassType()) as ObjectTracker
        val targetFinder = objectTracker.targetFinder

        // Start Cloud Recognition
        targetFinder.startRecognition()

        // Clear all trackables created previously:
        targetFinder.clearTrackables()

        mRenderer!!.scanningMode = true
        scanlineStart()

        // Updates state variables
        mRenderer!!.showAnimation3Dto2D(false)
        mRenderer!!.isShowing2DOverlay(false)
        mRenderer!!.renderState = ImageOverlayRenderer.RS_SCANNING
    }


    /** Displays the 2D Book Overlay  */
    fun show2DOverlay()
    {
        // Sends the Message to the Handler in the UI thread
        overlay2DHandler.sendEmptyMessage(SHOW_2D_OVERLAY)
    }


    /** Hides the 2D Book Overlay  */
    fun hide2DOverlay()
    {
        // Sends the Message to the Handler in the UI thread
        overlay2DHandler.sendEmptyMessage(HIDE_2D_OVERLAY)
    }


    override fun onTouchEvent(event: MotionEvent): Boolean
    {
        // Process the Gestures
        return mGestureDetector!!.onTouchEvent(event)
    }

    // Process Double Tap event for showing the Camera options menu
    private inner class GestureListener : GestureDetector.SimpleOnGestureListener()
    {
        override fun onDown(e: MotionEvent): Boolean
        {
            return true
        }


        override fun onSingleTapUp(event: MotionEvent): Boolean
        {

            // If the book info is not displayed it performs an Autofocus
            if (mBookInfoStatus == BOOKINFO_NOT_DISPLAYED)
            {
                // Calls the Autofocus Method
                val result = CameraDevice.getInstance().setFocusMode(
                        CameraDevice.FOCUS_MODE.FOCUS_MODE_TRIGGERAUTO)

                if (!result)
                    Log.e("SingleTapUp", "Unable to trigger focus")

                // If the book info is displayed it shows the book data web view
            } else if (mBookInfoStatus == BOOKINFO_IS_DISPLAYED)
            {

                val x = event.getX(0)
                val y = event.getY(0)

                val metrics = DisplayMetrics()
                windowManager.defaultDisplay.getMetrics(metrics)

                // Creates a Bounding box for detecting touches
                val screenLeft = metrics.widthPixels / 8.0f
                val screenRight = metrics.widthPixels * 0.8f
                val screenUp = metrics.heightPixels / 7.0f
                val screenDown = metrics.heightPixels * 0.7f

                // Checks touch inside the bounding box
                if (x < screenRight && x > screenLeft && y < screenDown
                        && y > screenUp)
                {
                    // Starts the webView
                    //startWebView(0)
                    scanlineStart()
                }
            }

            return true
        }
    }


    override fun doLoadTrackersData(): Boolean
    {
        Log.d(LOGTAG, "initBooks")

        // Get the object tracker:
        val trackerManager = TrackerManager.getInstance()
        val objectTracker = trackerManager
                .getTracker(ObjectTracker.getClassType()) as ObjectTracker

        // Initialize target finder:
        val targetFinder = objectTracker.targetFinder

        // Start initialization:
        if (targetFinder.startInit(kAccessKey, kSecretKey))
        {
            targetFinder.waitUntilInitFinished()
            scanlineStart()
        }

        val resultCode = targetFinder.initState
        if (resultCode != TargetFinder.INIT_SUCCESS)
        {
            if (resultCode == TargetFinder.INIT_ERROR_NO_NETWORK_CONNECTION)
            {
                mInitErrorCode = UPDATE_ERROR_NO_NETWORK_CONNECTION
            } else
            {
                mInitErrorCode = UPDATE_ERROR_SERVICE_NOT_AVAILABLE
            }

            Log.e(LOGTAG, "Failed to initialize target finder.")
            return false
        }

        return true
    }


    override fun doUnloadTrackersData(): Boolean
    {
        return true
    }


    override fun onInitARDone(mApplicationException: ApplicationException?)
    {

        if (mApplicationException == null)
        {
            initApplicationAR()

            // Now add the GL surface view. It is important
            // that the OpenGL ES surface view gets added
            // BEFORE the camera is started and video
            // background is configured.
            addContentView(mGlView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT))

            // Start the camera:
            try
            {
                vuforiaAppSession.startAR(CameraDevice.CAMERA_DIRECTION.CAMERA_DIRECTION_DEFAULT)
            } catch (e: ApplicationException)
            {
                Log.e(LOGTAG, e.string)
            }

            mRenderer!!.setActive(true)

            val result = CameraDevice.getInstance().setFocusMode(
                    CameraDevice.FOCUS_MODE.FOCUS_MODE_CONTINUOUSAUTO)

            if (!result)
                Log.e(LOGTAG, "Unable to enable continuous autofocus")

            mUILayout!!.bringToFront()

            // Hides the Loading Dialog
            loadingDialogHandler.sendEmptyMessage(HIDE_LOADING_DIALOG)

            mUILayout!!.setBackgroundColor(Color.TRANSPARENT)

        } else
        {
            Log.e(LOGTAG, mApplicationException.string)
            if (mInitErrorCode != 0)
            {
                showErrorMessage(mInitErrorCode, 10.0, true)
            } else
            {
                showInitializationErrorMessage(mApplicationException.string)
            }
        }
    }


    // Shows initialization error messages as System dialogs
    fun showInitializationErrorMessage(message: String)
    {
        val errorMessage = message
        runOnUiThread {
            if (mErrorDialog != null)
            {
                mErrorDialog!!.dismiss()
            }

            // Generates an Alert Dialog to show the error message
            val builder = AlertDialog.Builder(
                    this@ImageOverlayActivity)
            builder
                    .setMessage(errorMessage)
                    .setTitle(getString(R.string.INIT_ERROR))
                    .setCancelable(false)
                    .setIcon(0)
                    .setPositiveButton("OK"
                    ) { dialog, id -> finish() }

            mErrorDialog = builder.create()
            mErrorDialog!!.show()
        }
    }


    override fun onVuforiaUpdate(state: State)
    {
        // Get the tracker manager:
        val trackerManager = TrackerManager.getInstance()

        // Get the object tracker:
        val objectTracker = trackerManager
                .getTracker(ObjectTracker.getClassType()) as ObjectTracker

        // Get the target finder:
        val finder = objectTracker.targetFinder

        // Check if there are new results available:
        val statusCode = finder.updateSearchResults()

        // Show a message if we encountered an error:
        if (statusCode < 0)
        {

            val closeAppAfterError = statusCode == UPDATE_ERROR_NO_NETWORK_CONNECTION || statusCode == UPDATE_ERROR_SERVICE_NOT_AVAILABLE

            showErrorMessage(statusCode, state.frame.timeStamp, closeAppAfterError)

        } else if (statusCode == TargetFinder.UPDATE_RESULTS_AVAILABLE)
        {
            // Process new search results
            if (finder.resultCount > 0)
            {
                val result = finder.getResult(0)

                // Check if this target is suitable for tracking:
                if (result.trackingRating > 0)
                {
                    // Create a new Trackable from the result:
                    val newTrackable = finder.enableTracking(result)
                    if (newTrackable != null) {
                        Log.d(LOGTAG, "Successfully created new trackable '"
                                + newTrackable.name + "' with rating '"
                                + result.trackingRating + "'.")

                        // Checks if the targets has changed
                        Log.d(LOGTAG, "Comparing Strings. currentTargetId: "
                                + result.uniqueTargetId + "  lastTargetId: "
                                + lastTargetId)

                        if (result.uniqueTargetId != lastTargetId)
                        {
                            // If the target has changed then regenerate the
                            // texture
                            // Cleaning this value indicates that the product
                            // Texture needs to be generated
                            // again in Java with the new Book data for the new
                            // target
                            mRenderer!!.deleteCurrentProductTexture()

                            // Starts the loading state for the product
                            mRenderer!!
                                    .renderState = ImageOverlayRenderer.RS_LOADING

                            // Calls the Java method with the current product
                            // texture
                            createProductTexture(result.metaData)

                        } else
                            mRenderer!!
                                    .renderState = ImageOverlayRenderer.RS_NORMAL

                        // Initialize the frames to skip variable, used for
                        // waiting
                        // a few frames for getting the chance to tracking
                        // before
                        // starting the transition to 2D when there is no target
                        mRenderer!!.setFramesToSkipBeforeRenderingTransition(10)

                        // Initialize state variables
                        mRenderer!!.showAnimation3Dto2D(true)
                        mRenderer!!.resetTrackingStarted()

                        // Updates the value of the current Target Id with the
                        // new target found
                        synchronized(lastTargetId) {
                            lastTargetId = result.uniqueTargetId
                        }

                        enterContentMode()
                    } else
                        Log.e(LOGTAG, "Failed to create new trackable.")
                }
            }
        }
    }


    override fun doInitTrackers(): Boolean
    {
        val tManager = TrackerManager.getInstance()
        val tracker: Tracker?

        // Indicate if the trackers were initialized correctly
        var result = true

        tracker = tManager.initTracker(ObjectTracker.getClassType())
        if (tracker == null)
        {
            Log.e(
                    LOGTAG,
                    "Tracker not initialized. Tracker already initialized or the camera is already started")
            result = false
        } else
        {
            Log.i(LOGTAG, "Tracker successfully initialized")
        }

        return result
    }

    private fun scanlineStart()
    {
        this.runOnUiThread {
            scanLine!!.visibility = View.VISIBLE
            scanLine!!.animation = scanAnimation
            scanLineStarted = true
        }
    }

    private fun scanlineStop()
    {
        this.runOnUiThread {
            scanLine!!.visibility = View.GONE
            scanLine!!.clearAnimation()
            scanLineStarted = false
        }
    }

    private fun scanCreateAnimation()
    {
        val orientation = resources.configuration.orientation

        val display = windowManager.defaultDisplay
        val screenHeight = display.height

        if (orientation == Configuration.ORIENTATION_PORTRAIT)
        {
            scanAnimation = TranslateAnimation(
                    TranslateAnimation.ABSOLUTE, 0f,
                    TranslateAnimation.ABSOLUTE, 0f,
                    TranslateAnimation.RELATIVE_TO_PARENT, 0f,
                    TranslateAnimation.ABSOLUTE, screenHeight.toFloat())
            scanAnimation!!.duration = 4000
        } else
        {
            scanAnimation = TranslateAnimation(
                    TranslateAnimation.ABSOLUTE, 0f,
                    TranslateAnimation.ABSOLUTE, 0f,
                    TranslateAnimation.ABSOLUTE, screenHeight.toFloat(),
                    TranslateAnimation.RELATIVE_TO_PARENT, 0f)
            scanAnimation!!.duration = 2000
        }

        scanAnimation!!.repeatCount = -1
        scanAnimation!!.repeatMode = Animation.REVERSE
        scanAnimation!!.interpolator = LinearInterpolator()

        // if the animation was in progress we need to restart it
        // to take into account the new configuration
        if (scanLineStarted)
        {
            scanlineStop()
            scanlineStart()
        }
    }

    companion object
    {
        private val LOGTAG = "ImageOverlayActivity"

        // Defines the Server URL to get the books data
        private val mServerURL = "https://developer.vuforia.com/samples/cloudreco/json/"

        // Stores the current status of the target ( if is being displayed or not )
        private val BOOKINFO_NOT_DISPLAYED = 0
        private val BOOKINFO_IS_DISPLAYED = 1

        // These codes match the ones defined in TargetFinder in Vuforia.jar
        internal val INIT_SUCCESS = 2
        internal val INIT_ERROR_NO_NETWORK_CONNECTION = -1
        internal val INIT_ERROR_SERVICE_NOT_AVAILABLE = -2
        internal val UPDATE_ERROR_AUTHORIZATION_FAILED = -1
        internal val UPDATE_ERROR_PROJECT_SUSPENDED = -2
        internal val UPDATE_ERROR_NO_NETWORK_CONNECTION = -3
        internal val UPDATE_ERROR_SERVICE_NOT_AVAILABLE = -4
        internal val UPDATE_ERROR_BAD_FRAME_QUALITY = -5
        internal val UPDATE_ERROR_UPDATE_SDK = -6
        internal val UPDATE_ERROR_TIMESTAMP_OUT_OF_RANGE = -7
        internal val UPDATE_ERROR_REQUEST_TIMEOUT = -8

        // Handles Codes to display/Hide views
        internal val HIDE_STATUS_BAR = 0
        internal val SHOW_STATUS_BAR = 1

        internal val HIDE_2D_OVERLAY = 0
        internal val SHOW_2D_OVERLAY = 1

        internal val HIDE_LOADING_DIALOG = 0
        internal val SHOW_LOADING_DIALOG = 1

        private val kAccessKey = "669ab267d2332a9c8f8c05730f2abd00a8c34fbd"
        private val kSecretKey = "7afac700a02bd5d68ab2b0b4dcaca982dda5a17e"

        // size of the Texture to be generated with the book data
        private val mTextureSize = 768
    }
}
