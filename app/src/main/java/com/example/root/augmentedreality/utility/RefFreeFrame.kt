package com.example.root.augmentedreality.utility

import android.util.Log
import com.example.root.augmentedreality.activity.UserDefinedTargetsActivity
import com.example.root.augmentedreality.vuforia.ApplicationSession
import com.vuforia.*


/**
 * Created by root on 28/6/17.
 */
class RefFreeFrame(activity: UserDefinedTargetsActivity, session: ApplicationSession)
{
    val TAG: String = "RefFreeFrame"

    enum class STATUS
    {
        STATUS_IDLE, STATUS_SCANNING, STATUS_CREATING, STATUS_SUCCESS
    }

    var curStatus: STATUS

    var colorFrame: FloatArray

    var halfScreenSize: Vec2F

    var lastFrameTime: Long = 0L

    var lastSuccessTime: Long = 0L

    var frameGL: RefFreeFrameGL

    // The latest trackable source to be extracted from the Target Builder
    var trackableSource: TrackableSource?

    var mActivity: UserDefinedTargetsActivity

    var vuforiaAppSession: ApplicationSession


    // Function used to transition in the range [0, 1]
    fun transition(v0: Float, inc: Float, a: Float, b: Float): Float {
        val vOut = v0 + inc
        return if (vOut < a) a else if (vOut > b) b else vOut
    }


    fun transition(v0: Float, inc: Float): Float {
        return transition(v0, inc, 0.0f, 1.0f)
    }


    init{
        mActivity = activity
        vuforiaAppSession = session
        colorFrame = FloatArray(4)
        curStatus = STATUS.STATUS_IDLE
        lastSuccessTime = 0
        trackableSource = null
        colorFrame[0] = 1.0f
        colorFrame[1] = 0.0f
        colorFrame[2] = 0.0f
        colorFrame[3] = 0.75f

        frameGL = RefFreeFrameGL(mActivity, vuforiaAppSession)
        halfScreenSize = Vec2F()
    }


    fun init() {
        // load the frame texture
        frameGL.getTextures()

        trackableSource = null
    }


    fun deInit() {
        val trackerManager = TrackerManager.getInstance()
        val objectTracker = trackerManager
                .getTracker(ObjectTracker.getClassType()) as ObjectTracker
        if (objectTracker != null) {
            val targetBuilder = objectTracker
                    .imageTargetBuilder
            if (targetBuilder != null && targetBuilder.frameQuality != ImageTargetBuilder.FRAME_QUALITY.FRAME_QUALITY_NONE) {
                targetBuilder.stopScan()
            }
        }
    }


    fun initGL(screenWidth: Int, screenHeight: Int) {
        frameGL.init(screenWidth, screenHeight)

        val renderer = Renderer.getInstance()
        val vc = renderer.getVideoBackgroundConfig()
        val temp = vc.getSize().getData()
        val videoBackgroundConfigSize = FloatArray(2)
        videoBackgroundConfigSize[0] = temp[0] * 0.5f
        videoBackgroundConfigSize[1] = temp[1] * 0.5f

        halfScreenSize.data = videoBackgroundConfigSize

        // sets last frame timer
        lastFrameTime = System.currentTimeMillis()

        reset()
    }


    fun reset() {
        curStatus = STATUS.STATUS_IDLE

    }


    fun setCreating() {
        curStatus = STATUS.STATUS_CREATING
    }


    fun updateUIState(targetBuilder: ImageTargetBuilder, frameQuality: Int) {
        // ** Elapsed time
        val elapsedTimeMS = System.currentTimeMillis() - lastFrameTime
        lastFrameTime += elapsedTimeMS

        // This is a time-dependent value used for transitions in
        // the range [0,1] over the period of half of a second.
        val transitionHalfSecond = elapsedTimeMS * 0.002f

        var newStatus: STATUS = curStatus

        when (curStatus) {
            STATUS.STATUS_IDLE -> if (frameQuality != ImageTargetBuilder.FRAME_QUALITY.FRAME_QUALITY_NONE)
                newStatus = STATUS.STATUS_SCANNING

            STATUS.STATUS_SCANNING -> when (frameQuality) {
            // bad target quality, render the frame white until a match is
            // made, then go to green
                ImageTargetBuilder.FRAME_QUALITY.FRAME_QUALITY_LOW -> {
                    colorFrame[0] = 1.0f
                    colorFrame[1] = 1.0f
                    colorFrame[2] = 1.0f
                }

            // good target, switch to green over half a second
                ImageTargetBuilder.FRAME_QUALITY.FRAME_QUALITY_HIGH, ImageTargetBuilder.FRAME_QUALITY.FRAME_QUALITY_MEDIUM -> {
                    colorFrame[0] = transition(colorFrame[0],
                            -transitionHalfSecond)
                    colorFrame[1] = transition(colorFrame[1],
                            transitionHalfSecond)
                    colorFrame[2] = transition(colorFrame[2],
                            -transitionHalfSecond)
                }
            }

            STATUS.STATUS_CREATING -> run {
                // check for new result
                // if found, set to success, success time and:
                val newTrackableSource = targetBuilder
                        .trackableSource
                if (newTrackableSource != null) {
                    newStatus = STATUS.STATUS_SUCCESS
                    lastSuccessTime = lastFrameTime
                    trackableSource = newTrackableSource

                    mActivity.targetCreated()
                }
            }
            else -> {
            }
        }

        curStatus = newStatus
    }


    fun render() {
        // Get the image tracker
        val trackerManager = TrackerManager.getInstance()
        val objectTracker = trackerManager
                .getTracker(ObjectTracker.getClassType()) as ObjectTracker

        // Get the frame quality from the target builder
        val targetBuilder = objectTracker.imageTargetBuilder
        val frameQuality = targetBuilder.frameQuality

        // Update the UI internal state variables
        updateUIState(targetBuilder, frameQuality)

        if (curStatus === STATUS.STATUS_SUCCESS) {
            curStatus = STATUS.STATUS_IDLE

            Log.d(TAG, "Built target, reactivating dataset with new target")
            mActivity.doStartTracker()
        }

        // Renders the hints
        when (curStatus) {
            STATUS.STATUS_SCANNING -> renderScanningViewfinder(frameQuality)
            else -> {
            }
        }

        SampleUtils.checkGLError("RefFreeFrame render")
    }


    fun renderScanningViewfinder(quality: Int) {
        frameGL.setModelViewScale(2.0f)
        frameGL.setColor(colorFrame)
        frameGL.renderViewfinder()
    }


    fun hasNewTrackableSource(): Boolean {
        return trackableSource != null
    }


    fun getNewTrackableSource(): TrackableSource? {
        val result = trackableSource
        trackableSource = null
        return result
    }
}