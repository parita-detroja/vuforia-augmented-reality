package com.example.root.augmentedreality.utility

/**
 * Created by root on 10/8/17.
 */
class Shaders {
    companion object
    {
        val cubeMeshVertexShader =
                "attribute vec4 vertexPosition; " +
                        "attribute vec2 vertexTexCoord; " +
                        " " +
                        "varying vec2 texCoord; " +
                        " " +
                        "uniform mat4 modelViewProjectionMatrix; " +
                        " " +
                        "void main() " +
                        "{ " +
                        "   gl_Position = modelViewProjectionMatrix * vertexPosition; " +
                        "   texCoord = vertexTexCoord; " +
                        "} "

        val cubeFragmentShader =
                "precision mediump float; " +
                        " " +
                        "varying vec2 texCoord; " +
                        " " +
                        "uniform sampler2D texSampler2D; " +
                        " " +
                        "void main() " +
                        "{ " +
                        "   gl_FragColor = texture2D(texSampler2D, texCoord); " +
                        "} "
    }
}