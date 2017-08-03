package com.example.root.augmentedreality.utility

import java.nio.Buffer

/**
 * Created by parita on 2/8/17.
 * This gives all buffer for image target instead any 3d object.
 */
class CustomImagePlane : MeshObject() {


    private val mVertBuff: Buffer
    private val mTexCoordBuff: Buffer
    private val mNormBuff: Buffer
    private val mIndBuff: Buffer

    init {
        mVertBuff = fillBuffer(planeVertices)
        mTexCoordBuff = fillBuffer(planeTexcoords)
        mNormBuff = fillBuffer(planeNormals)
        mIndBuff = fillBuffer(planeIndices)
    }

    override fun getBuffer(bufferType: MeshObject.BUFFER_TYPE): Buffer? {
        var result: Buffer? = null
        when (bufferType) {
            MeshObject.BUFFER_TYPE.BUFFER_TYPE_VERTEX -> result = mVertBuff
            MeshObject.BUFFER_TYPE.BUFFER_TYPE_TEXTURE_COORD -> result = mTexCoordBuff
            MeshObject.BUFFER_TYPE.BUFFER_TYPE_INDICES -> result = mIndBuff
            MeshObject.BUFFER_TYPE.BUFFER_TYPE_NORMALS -> result = mNormBuff
            else -> {
            }
        }
        return result
    }

    override fun getNumObjectVertex(): Int {
        return planeVertices.size / 3
    }

    override fun getNumObjectIndex(): Int {
        return planeIndices.size
    }

    companion object {

        private val planeVertices = doubleArrayOf(-50.0, -50.0, 0.0, 50.0, -50.0, 0.0, 50.0, 50.0, 0.0, -50.0, 50.0, 0.0)
        private val planeTexcoords = doubleArrayOf(0.0, 0.0, 1.0, 0.0, 1.0, 1.0, 0.0, 1.0)
        private val planeNormals = doubleArrayOf(0.0, 0.0, 1.0, 0.0, 0.0, 1.0, 0.0, 0.0, 1.0, 0.0, 0.0, 1.0)
        private val planeIndices = shortArrayOf(0, 1, 2, 0, 2, 3)
    }
}