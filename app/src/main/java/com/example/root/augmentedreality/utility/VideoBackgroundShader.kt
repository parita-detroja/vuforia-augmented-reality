package com.example.root.textrecognitionar.utils

object VideoBackgroundShader {

    val VB_VERTEX_SHADER =
            "attribute vec4 vertexPosition;\n" +
                    "attribute vec2 vertexTexCoord;\n" +
                    "uniform mat4 projectionMatrix;\n" +

                    "varying vec2 texCoord;\n" +

                    "void main()\n" +
                    "{\n" +
                    "    gl_Position = projectionMatrix * vertexPosition;\n" +
                    "    texCoord = vertexTexCoord;\n" +
                    "}\n"

    val VB_FRAGMENT_SHADER =
            "precision mediump float;\n" +
                    "varying vec2 texCoord;\n" +
                    "uniform sampler2D texSampler2D;\n" +
                    "void main ()\n" +
                    "{\n" +
                    "    gl_FragColor = texture2D(texSampler2D, texCoord);\n" +
                    "}\n"

}
