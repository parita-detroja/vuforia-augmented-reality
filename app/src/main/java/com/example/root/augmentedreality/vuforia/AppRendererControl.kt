package com.example.root.augmentedreality.vuforia

import com.vuforia.State

interface AppRendererControl {

    // This method has to be implemented by the Renderer class which handles the content rendering
    // of the sample, this one is called from SampleAppRendering class for each view inside a loop
    fun renderFrame(state: State, projectionMatrix: FloatArray)

}
