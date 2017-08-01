package com.example.root.augmentedreality.vuforia

import com.vuforia.State

/**
 * Created by parita on 25/5/17.
 * interface having all method that need to implement for vuforia.
 */

interface ApplicationControl {

    fun doInitTrackers(): Boolean

    fun doLoadTrackersData(): Boolean

    fun doStartTracker(): Boolean

    fun doStopTracker(): Boolean

    fun doUnloadTrackersData(): Boolean

    fun doDeInitTrackerData(): Boolean

    fun onInitARDone(mApplicationException: ApplicationException?)

    fun onVuforiaUpdate(state: State)
}
