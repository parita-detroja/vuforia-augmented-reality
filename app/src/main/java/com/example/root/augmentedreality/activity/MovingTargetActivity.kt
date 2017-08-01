package com.example.root.augmentedreality.activity

import android.app.Activity
import android.app.AlertDialog
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.*
import android.widget.RelativeLayout
import com.example.root.augmentedreality.R
import com.example.root.augmentedreality.renderer.MovingTargetRenderer
import com.example.root.augmentedreality.utility.LoadingDialogHandler
import com.example.root.augmentedreality.utility.Texture
import com.example.root.augmentedreality.vuforia.ApplicationControl
import com.example.root.augmentedreality.vuforia.ApplicationException
import com.example.root.augmentedreality.vuforia.ApplicationSession
import com.example.root.textrecognitionar.utils.ApplicationGLView
import com.vuforia.*
import java.util.*

// The main activity for the MultiTargets sample.
class MovingTargetActivity : Activity(), ApplicationControl {

    internal lateinit var vuforiaAppSession: ApplicationSession

    // Our OpenGL view:
    private var mGlView: ApplicationGLView? = null

    // Our renderer:
    private var mRenderer: MovingTargetRenderer? = null

    private var mUILayout: RelativeLayout? = null

    private var mGestureDetector: GestureDetector? = null

    private val loadingDialogHandler = LoadingDialogHandler(
            this)

    // The textures we will use for rendering:
    private var mTextures: Vector<Texture>? = null

    private var mit: MultiTarget? = null

    private var dataSet: DataSet? = null

    // Alert Dialog used to display SDK errors
    private var mErrorDialog: AlertDialog? = null

    internal var mIsDroidDevice = false

    // Called when the activity first starts or the user navigates back to an
    // activity.
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(LOGTAG, "onCreate")
        super.onCreate(savedInstanceState)

        vuforiaAppSession = ApplicationSession(this)

        startLoadingAnimation()

        vuforiaAppSession
                .initAR(this, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)

        // Load any sample specific textures:
        mTextures = Vector<Texture>()
        loadTextures()

        mGestureDetector = GestureDetector(this, GestureListener())

        mIsDroidDevice = android.os.Build.MODEL.toLowerCase().startsWith(
                "droid")

    }

    override fun doStartTracker(): Boolean {
        // Indicate if the trackers were started correctly
        val result = true

        val objectTracker = TrackerManager.getInstance().getTracker(
                ObjectTracker.getClassType())
        objectTracker?.start()

        return result
    }

    override fun doStopTracker(): Boolean {
        // Indicate if the trackers were stopped correctly
        val result = true

        val objectTracker = TrackerManager.getInstance().getTracker(
                ObjectTracker.getClassType())
        objectTracker?.stop()

        return result
    }

    override fun doDeInitTrackerData(): Boolean {
        // Indicate if the trackers were deinitialized correctly
        val result = true

        val tManager = TrackerManager.getInstance()
        tManager.deinitTracker(ObjectTracker.getClassType())

        return result
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


    // We want to load specific textures from the APK, which we will later use
    // for rendering.
    private fun loadTextures() {
        mTextures!!.add(Texture.loadTextureFromApk(
                "MultiTargets/TextureWireframe.png", assets))
        mTextures!!.add(Texture.loadTextureFromApk(
                "MultiTargets/TextureBowlAndSpoon.png", assets))
    }


    // Called when the activity will start interacting with the user.
    override fun onResume() {
        Log.d(LOGTAG, "onResume")
        super.onResume()

        // This is needed for some Droid devices to force portrait
        if (mIsDroidDevice) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        try {
            vuforiaAppSession.resumeAR()
        } catch (e: ApplicationException) {
            Log.e(LOGTAG, e.string)
        }

        // Resume the GL view:
        if (mGlView != null) {
            mGlView!!.visibility = View.VISIBLE
            mGlView!!.onResume()
        }

    }


    override fun onConfigurationChanged(config: Configuration) {
        Log.d(LOGTAG, "onConfigurationChanged")
        super.onConfigurationChanged(config)

        vuforiaAppSession.onConfigurationChanged()
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
            vuforiaAppSession.pauseAR()
        } catch (e: ApplicationException) {
            Log.e(LOGTAG, e.string)
        }

    }


    // The final call you receive before your activity is destroyed.
    override fun onDestroy() {
        Log.d(LOGTAG, "onDestroy")
        super.onDestroy()

        try {
            vuforiaAppSession.stopAR()
        } catch (e: ApplicationException) {
            Log.e(LOGTAG, e.string)
        }

        // Unload texture:
        mTextures!!.clear()
        mTextures = null

        System.gc()
    }


    override fun onTouchEvent(event: MotionEvent): Boolean {
        return mGestureDetector!!.onTouchEvent(event)
    }


    private fun startLoadingAnimation() {
        val inflater = LayoutInflater.from(this)
        mUILayout = inflater.inflate(R.layout.camera_overlay, null, false) as RelativeLayout

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

        mRenderer = MovingTargetRenderer(this, vuforiaAppSession)
        mRenderer!!.setTextures(mTextures)
        mGlView!!.setRenderer(mRenderer)

    }


    internal fun initMIT() {
        //
        // This function checks the current tracking setup for completeness. If
        // it finds that something is missing, then it creates it and configures
        // it:
        // Any MultiTarget and Part elements missing from the config.xml file
        // will be created.
        //

        Log.d(LOGTAG, "Beginning to check the tracking setup")

        // Configuration data - identical to what is in the config.xml file
        //
        // If you want to recreate the trackable assets using the on-line TMS
        // server using the original images provided in the sample's media
        // folder, use the following trackable sizes on creation to get
        // identical visual results:
        // create a cuboid with width = 90 ; height = 120 ; length = 60.

        val names = arrayOf("FlakesBox.Front", "FlakesBox.Back", "FlakesBox.Left", "FlakesBox.Right", "FlakesBox.Top", "FlakesBox.Bottom")
        val trans = floatArrayOf(0.0f, 0.0f, 30.0f, 0.0f, 0.0f, -30.0f, -45.0f, 0.0f, 0.0f, 45.0f, 0.0f, 0.0f, 0.0f, 60.0f, 0.0f, 0.0f, -60.0f, 0.0f)
        val rots = floatArrayOf(1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 180.0f, 0.0f, 1.0f, 0.0f, -90.0f, 0.0f, 1.0f, 0.0f, 90.0f, 1.0f, 0.0f, 0.0f, -90.0f, 1.0f, 0.0f, 0.0f, 90.0f)

        val trackerManager = TrackerManager.getInstance()
        val objectTracker = trackerManager
                .getTracker(ObjectTracker.getClassType()) as ObjectTracker

        if (objectTracker == null || dataSet == null) {
            return
        }

        // Go through all Trackables to find the MultiTarget instance
        //
        for (i in 0..dataSet!!.numTrackables - 1) {
            if (dataSet!!.getTrackable(i).type === MultiTargetResult
                    .getClassType()) {
                Log.d(LOGTAG, "MultiTarget exists -> no need to create one")
                mit = dataSet!!.getTrackable(i) as MultiTarget
                break
            }
        }

        // If no MultiTarget was found, then let's create one.
        if (mit == null) {
            Log.d(LOGTAG, "No MultiTarget found -> creating one")
            mit = dataSet!!.createMultiTarget("FlakesBox")

            if (mit == null) {
                Log.d(LOGTAG,
                        "ERROR: Failed to create the MultiTarget - probably the Tracker is running")
                return
            }
        }

        // Try to find each ImageTarget. If we find it, this actually means that
        // it is not part of the MultiTarget yet: ImageTargets that are part of
        // a MultiTarget don't show up in the list of Trackables.
        // Each ImageTarget that we found, is then made a part of the
        // MultiTarget and a correct pose (reflecting the pose of the
        // config.xml file) is set).
        //
        var numAdded = 0
        for (i in 0..5) {
            val it = findImageTarget(names[i])
            if (it != null) {
                Log.d(LOGTAG,
                        "ImageTarget '%s' found -> adding it as to the MultiTarget" + names[i])

                val idx = mit!!.addPart(it)
                val t = Vec3F(trans[i * 3], trans[i * 3 + 1],
                        trans[i * 3 + 2])
                val a = Vec3F(rots[i * 4], rots[i * 4 + 1],
                        rots[i * 4 + 2])
                val mat = Matrix34F()

                Tool.setTranslation(mat, t)
                Tool.setRotation(mat, a, rots[i * 4 + 3])
                mit!!.setPartOffset(idx, mat)
                numAdded++
            }
        }

        Log.d(LOGTAG, "Added " + numAdded
                + " ImageTarget(s) to the MultiTarget")

        if (mit!!.numParts != 6) {
            Log.d(LOGTAG,
                    "ERROR: The MultiTarget should have 6 parts, but it reports "
                            + mit!!.numParts + " parts")
        }

        Log.d(LOGTAG, "Finished checking the tracking setup")
    }


    internal fun findImageTarget(name: String): ImageTarget? {
        val trackerManager = TrackerManager.getInstance()
        val objectTracker = trackerManager
                .getTracker(ObjectTracker.getClassType()) as ObjectTracker

        if (objectTracker != null) {
            for (i in 0..dataSet!!.numTrackables - 1) {
                if (dataSet!!.getTrackable(i).type === MultiTargetResult
                        .getClassType()) {
                    if (dataSet!!.getTrackable(i).name.compareTo(name) == 0)
                        return dataSet!!.getTrackable(i) as ImageTarget
                }
            }
        }
        return null
    }


    override fun doInitTrackers(): Boolean {
        // Indicate if the trackers were initialized correctly
        var result = true

        val tManager = TrackerManager.getInstance()
        val tracker: Tracker?

        tracker = tManager.initTracker(ObjectTracker.getClassType())
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


    override fun doLoadTrackersData(): Boolean {
        // Get the image tracker:
        val trackerManager = TrackerManager.getInstance()
        val objectTracker = trackerManager
                .getTracker(ObjectTracker.getClassType()) as ObjectTracker
        if (objectTracker == null) {
            Log.d(
                    LOGTAG,
                    "Failed to load tracking data set because the ObjectTracker has not been initialized.")
            return false
        }

        // Create the data set:
        dataSet = objectTracker.createDataSet()
        if (dataSet == null) {
            Log.d(LOGTAG, "Failed to create a new tracking data.")
            return false
        }

        // Load the data set:
        if (!dataSet!!.load("MultiTargets/FlakesBox.xml",
                STORAGE_TYPE.STORAGE_APPRESOURCE)) {
            Log.d(LOGTAG, "Failed to load data set.")
            return false
        }

        Log.d(LOGTAG, "Successfully loaded the data set.")

        // Validate the MultiTarget and setup programmatically if required:
        initMIT()

        if (!objectTracker.activateDataSet(dataSet))
            return false

        return true
    }

    override fun doUnloadTrackersData(): Boolean {
        // Indicate if the trackers were unloaded correctly
        var result = true

        // Get the image tracker:
        val trackerManager = TrackerManager.getInstance()
        val objectTracker = trackerManager
                .getTracker(ObjectTracker.getClassType()) as ObjectTracker
        if (objectTracker == null) {
            Log.d(
                    LOGTAG,
                    "Failed to destroy the tracking data set because the ObjectTracker has not been initialized.")
            return false
        }

        if (dataSet != null) {
            if (!objectTracker.deactivateDataSet(dataSet)) {
                Log.d(
                        LOGTAG,
                        "Failed to destroy the tracking data set because the data set could not be deactivated.")
                result = false
            } else if (!objectTracker.destroyDataSet(dataSet)) {
                Log.d(LOGTAG, "Failed to destroy the tracking data set.")
                result = false
            }

            if (result)
                Log.d(LOGTAG, "Successfully destroyed the data set.")

            dataSet = null
            mit = null
        }

        return result
    }

    override fun onInitARDone(mApplicationException: ApplicationException?) {

        if (mApplicationException == null) {
            initApplicationAR()

            mRenderer!!.setActive(true)

            // Now add the GL surface view. It is important
            // that the OpenGL ES surface view gets added
            // BEFORE the camera is started and video
            // background is configured.
            addContentView(mGlView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT))

            // Sets the UILayout to be drawn in front of the camera
            mUILayout!!.bringToFront()

            // Hides the Loading Dialog
            loadingDialogHandler
                    .sendEmptyMessage(LoadingDialogHandler.HIDE_LOADING_DIALOG)

            // Sets the layout background to transparent
            mUILayout!!.setBackgroundColor(Color.TRANSPARENT)

            try {
                vuforiaAppSession.startAR(CameraDevice.CAMERA_DIRECTION.CAMERA_DIRECTION_DEFAULT)
            } catch (e: ApplicationException) {
                Log.e(LOGTAG, e.string)
            }

            val result = CameraDevice.getInstance().setFocusMode(
                    CameraDevice.FOCUS_MODE.FOCUS_MODE_CONTINUOUSAUTO)

            if (!result)
                Log.e(LOGTAG, "Unable to enable continuous autofocus")

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
                    this@MovingTargetActivity)
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


    override fun onVuforiaUpdate(state: State) {}

    companion object {
        private val LOGTAG = "MultiTargets"
    }
}
