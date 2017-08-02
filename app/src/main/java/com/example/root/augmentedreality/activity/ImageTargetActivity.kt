package com.example.root.augmentedreality.activity

import android.app.AlertDialog
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import com.example.root.augmentedreality.R
import com.example.root.augmentedreality.renderer.ImageTargetRenderer
import com.example.root.augmentedreality.utility.LoadingDialogHandler
import com.example.root.augmentedreality.utility.Texture
import com.example.root.augmentedreality.vuforia.ApplicationControl
import com.example.root.augmentedreality.vuforia.ApplicationException
import com.example.root.augmentedreality.vuforia.ApplicationSession
import com.example.root.textrecognitionar.utils.ApplicationGLView
import com.vuforia.*
import java.util.*

class ImageTargetActivity : AppCompatActivity(), ApplicationControl
{
    private var vuforiaAppSession: ApplicationSession? = null

    private var mGestureDetector: GestureDetector? = null

    private var mCurrentDataset: DataSet? = null

    val mDataSetString = mutableListOf<String>()

    private var mCurrentDataSelectionIndex: Int = 0

    private var mExtendedTracking: Boolean = false

    private lateinit var mGLView: ApplicationGLView

    private lateinit var mRenderer: ImageTargetRenderer

    private lateinit var mTexture: Vector<Texture>

    private lateinit var mUILayout: RelativeLayout

    val loadingDialogHandler: LoadingDialogHandler = LoadingDialogHandler(this)

    var mConsAutoFocus: Boolean = false

    private var mErrorDialog: AlertDialog? = null

    private var mSwitchDatasetAsap: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my)

        vuforiaAppSession = ApplicationSession(this)

        vuforiaAppSession?.initAR(this, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)

        mGestureDetector = GestureDetector(this,GestureListener())

        mTexture = Vector<Texture>()
        loadTexture()

        mDataSetString.add("StonesAndChips.xml")
        mDataSetString.add("Tarmac.xml")

        startLoadingAnimation()
    }

    override fun doInitTrackers(): Boolean {
        // Indicate if the trackers were initialized correctly
        var result = true

        val tManager = TrackerManager.getInstance()
        val tracker: Tracker?

        tracker = tManager.initTracker(ObjectTracker.getClassType())
        if (tracker == null) {
            Log.e("ImageTargetActivity","Tracker not initialized. Tracker already initialized or the camera is already started")
            result = false
        } else {
            Log.i("ImageTargetActivity", "Tracker successfully initialized")
        }

        return result
    }

    override fun doLoadTrackersData(): Boolean {

        val mTrackerManger: TrackerManager = TrackerManager.getInstance()

        val mObjectTracker: ObjectTracker? = mTrackerManger.getTracker(ObjectTracker.getClassType()) as ObjectTracker

        if(mObjectTracker == null)
        {
            Log.d("ImageTargetActivity","Failed to load tracking data set because the Tracker has not been initialized.")
            return false
        }

        if(mCurrentDataset == null)
        {
            mCurrentDataset = mObjectTracker.createDataSet()
        }

        if(mCurrentDataset == null)
        {
            Log.d("ImageTargetActivity","Failed to load data set.")
            return false
        }

        if(!mCurrentDataset!!.load(mDataSetString[mCurrentDataSelectionIndex], STORAGE_TYPE.STORAGE_APPRESOURCE))
        {
            Log.d("ImageTargetActivity","Failed to load data.")
            return false
        }

        if(!mObjectTracker.activateDataSet(mCurrentDataset))
        {
            Log.d("ImageTargetActivity","Failed to activate data set.")
        }

        val numTrackables: Int = mCurrentDataset!!.getNumTrackables()

        for(count in 0..numTrackables-1)
        {
            val mTrackable: Trackable = mCurrentDataset!!.getTrackable(count)

            if(isExtendedTrackingActive())
            {
                mTrackable.startExtendedTracking()
            }

            val name: String = "Current Dataset : " + mTrackable.getName()

            mTrackable.setUserData(name)

            Log.d("ImageTargetActivity","UserData:Set the following user data "+mTrackable.getUserData())


        }

        Log.d("MYActivity", "Tracker Initialize Successfully")

        return true
    }

    override fun doStartTracker(): Boolean {
        val result = true

        val objectTracker: Tracker = TrackerManager.getInstance().getTracker(ObjectTracker.getClassType())
        if(objectTracker != null)
        {
            objectTracker.start()
        }
        return result
    }

    override fun doStopTracker(): Boolean {

        val result: Boolean = true
        val mObjectTracker: Tracker = TrackerManager.getInstance().getTracker(ObjectTracker.getClassType())
        if(mObjectTracker != null)
        {
            mObjectTracker.stop()
        }

        return result

    }

    override fun doUnloadTrackersData(): Boolean {

        var result: Boolean = true

        val mTrackerManager: TrackerManager = TrackerManager.getInstance()
        val mObjectTracker: ObjectTracker = mTrackerManager.getTracker(ObjectTracker.getClassType()) as ObjectTracker
        if(mObjectTracker == null)
        {
            return false
        }
        if(mCurrentDataset != null && mCurrentDataset!!.isActive)
        {
            if(mObjectTracker.getActiveDataSet(0).equals(mCurrentDataset) && !mObjectTracker.deactivateDataSet(mCurrentDataset))
            {
                return false
            }else if(!mObjectTracker.destroyDataSet(mCurrentDataset)){
                return false
            }

            mCurrentDataset = null

        }

        return result
    }

    override fun doDeInitTrackerData(): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onInitARDone(mApplicationException: ApplicationException?)
    {
        if(mApplicationException == null)
        {
            initApplicationAR()

            mRenderer.setActive(true)

            addContentView(mGLView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

            mUILayout.bringToFront()

            mUILayout.setBackgroundColor(Color.TRANSPARENT)

            try
            {
                vuforiaAppSession!!.startAR(CameraDevice.CAMERA_DIRECTION.CAMERA_DIRECTION_DEFAULT)
            }catch (e: Exception)
            {
                Log.e("ImageTargetActivity",e.message)
            }

            val result: Boolean = CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_CONTINUOUSAUTO)

            if(result)
            {
                mConsAutoFocus = true
            }else
            {
                Log.e("ImageTargetActivity","Unable to enable auto focus")
            }
        }else
        {
            Log.e("ImageTargetActivity",mApplicationException.message)

            showInitializationErrorMessage(mApplicationException.toString())
        }
    }

    override fun onVuforiaUpdate(state: State) {

        if(mSwitchDatasetAsap)
        {
            mSwitchDatasetAsap = false
            val mTrackerManager: TrackerManager = TrackerManager.getInstance()
            val mObjectTracker: ObjectTracker = mTrackerManager.getTracker(ObjectTracker.getClassType()) as ObjectTracker
            if(mObjectTracker == null || mCurrentDataset == null || mObjectTracker.getActiveDataSet(0) == null)
            {
                Log.d("ImageTargetActivity","Failed to swap dataset.")
                return
            }

            doUnloadTrackersData()
            doLoadTrackersData()
        }
    }

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

    fun isExtendedTrackingActive() : Boolean
    {
        return mExtendedTracking
    }

    fun initApplicationAR()
    {
        val depthSize: Int = 16
        val stencilSize: Int = 0

        val translucent: Boolean = Vuforia.requiresAlpha()

        mGLView = ApplicationGLView(this)

        mGLView.init(translucent,depthSize,stencilSize)

        mRenderer = ImageTargetRenderer(this, vuforiaAppSession!!)

        mRenderer.setTextures(mTexture)

        mGLView.setRenderer(mRenderer)

    }

    private fun startLoadingAnimation() {
        mUILayout = View.inflate(this, R.layout.camera_overlay,
                null) as RelativeLayout

        mUILayout.setVisibility(View.VISIBLE)
        mUILayout.setBackgroundColor(Color.BLACK)

        // Gets a reference to the loading dialog
        loadingDialogHandler.mLoadingDialogContainer = mUILayout
                .findViewById(R.id.loading_indicator)

        // Shows the loading indicator at start
        loadingDialogHandler
                .sendEmptyMessage(LoadingDialogHandler.SHOW_LOADING_DIALOG)

        // Adds the inflated layout to the view
        addContentView(mUILayout, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT))

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
                    this@ImageTargetActivity)
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

    fun loadTexture()
    {
        mTexture.add(Texture.loadTextureFromApk("TextureTeapotBrass.png", assets))
        mTexture.add(Texture.loadTextureFromApk("TextureTeapotBlue.png",assets))
        mTexture.add(Texture.loadTextureFromApk("TextureTeapotRed.png", assets))
        mTexture.add(Texture.loadTextureFromApk("ImageTargets/Buildings.jpeg",assets))
    }

}
