package com.example.root.textrecognitionar.utils

import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.util.Log
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.egl.EGLDisplay

/**
 * Created by root on 26/5/17.
 */

class ApplicationGLView(context: Context) : GLSurfaceView(context) {

    // Initialization.
    fun init(translucent: Boolean, depth: Int, stencil: Int) {
        Log.i(TAG, "Using OpenGL ES 2.0")
        Log.i(TAG, "Using " + (if (translucent) "translucent" else "opaque")
                + " GLView, depth buffer size: " + depth + ", stencil size: "
                + stencil)

        if (translucent) {
            this.holder.setFormat(PixelFormat.TRANSLUCENT)
        }

        setEGLContextFactory(ContextFactory())

        setEGLConfigChooser(if (translucent)
            ConfigChooser(8, 8, 8, 8, depth,
                    stencil)
        else
            ConfigChooser(5, 6, 5, 0, depth, stencil))
    }

    private class ContextFactory : GLSurfaceView.EGLContextFactory {


        override fun createContext(egl: EGL10, display: EGLDisplay,
                                   eglConfig: EGLConfig): EGLContext {
            val context: EGLContext

            Log.i(TAG, "Creating OpenGL ES 2.0 context")
            checkEglError("Before eglCreateContext", egl)
            val attrib_list_gl20 = intArrayOf(EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE)
            context = egl.eglCreateContext(display, eglConfig,
                    EGL10.EGL_NO_CONTEXT, attrib_list_gl20)

            checkEglError("After eglCreateContext", egl)
            return context
        }


        override fun destroyContext(egl: EGL10, display: EGLDisplay,
                                    context: EGLContext) {
            egl.eglDestroyContext(display, context)
        }

        companion object {
            private val EGL_CONTEXT_CLIENT_VERSION = 0x3098
        }
    }

    private class ConfigChooser(// Subclasses can adjust these values:
            protected var mRedSize: Int, protected var mGreenSize: Int, protected var mBlueSize: Int, protected var mAlphaSize: Int, protected var mDepthSize: Int, protected var mStencilSize: Int) : GLSurfaceView.EGLConfigChooser {


        private fun getMatchingConfig(egl: EGL10, display: EGLDisplay,
                                      configAttribs: IntArray): EGLConfig {
            // Get the number of minimally matching EGL configurations
            val num_config = IntArray(1)
            egl.eglChooseConfig(display, configAttribs, null, 0, num_config)

            val numConfigs = num_config[0]
            if (numConfigs <= 0)
                throw IllegalArgumentException("No matching EGL configs")

            // Allocate then read the array of minimally matching EGL configs
            val configs = arrayOfNulls<EGLConfig>(numConfigs)
            egl.eglChooseConfig(display, configAttribs, configs, numConfigs,
                    num_config)

            // Now return the "best" one
            return chooseConfig(egl, display, configs)
        }


        override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig {
            // This EGL config specification is used to specify 2.0
            // rendering. We use a minimum size of 4 bits for
            // red/green/blue, but will perform actual matching in
            // chooseConfig() below.
            val EGL_OPENGL_ES2_BIT = 0x0004
            val s_configAttribs_gl20 = intArrayOf(EGL10.EGL_RED_SIZE, 4, EGL10.EGL_GREEN_SIZE, 4, EGL10.EGL_BLUE_SIZE, 4, EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT, EGL10.EGL_NONE)

            return getMatchingConfig(egl, display, s_configAttribs_gl20)
        }


        fun chooseConfig(egl: EGL10, display: EGLDisplay,
                         configs: Array<EGLConfig?>): EGLConfig {
            for (config in configs) {
                if(config != null) {
                    val d = findConfigAttrib(egl, display, config,
                            EGL10.EGL_DEPTH_SIZE, 0)
                    val s = findConfigAttrib(egl, display, config,
                            EGL10.EGL_STENCIL_SIZE, 0)

                    // We need at least mDepthSize and mStencilSize bits
                    if (d < mDepthSize || s < mStencilSize)
                        continue

                    // We want an *exact* match for red/green/blue/alpha
                    val r = findConfigAttrib(egl, display, config,
                            EGL10.EGL_RED_SIZE, 0)
                    val g = findConfigAttrib(egl, display, config,
                            EGL10.EGL_GREEN_SIZE, 0)
                    val b = findConfigAttrib(egl, display, config,
                            EGL10.EGL_BLUE_SIZE, 0)
                    val a = findConfigAttrib(egl, display, config,
                            EGL10.EGL_ALPHA_SIZE, 0)

                    if (r == mRedSize && g == mGreenSize && b == mBlueSize
                            && a == mAlphaSize)
                        return config
                }
            }
            return null!!
        }


        private fun findConfigAttrib(egl: EGL10, display: EGLDisplay,
                                     config: EGLConfig, attribute: Int, defaultValue: Int): Int {

            if (egl.eglGetConfigAttrib(display, config, attribute, mValue))
                return mValue[0]

            return defaultValue
        }

        private val mValue = IntArray(1)
    }

    companion object {

        private val TAG = "ApplicationGLView"

        private fun checkEglError(prompt: String, egl: EGL10) {
            var error: Int = egl.eglGetError()
            while (error  != EGL10.EGL_SUCCESS) {
                Log.e(TAG, String.format("%s: EGL error: 0x%x", prompt, error))
            }
        }
    }

}
