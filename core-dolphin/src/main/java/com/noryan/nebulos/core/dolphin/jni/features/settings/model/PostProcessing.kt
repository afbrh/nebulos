// SPDX-License-Identifier: GPL-2.0-or-later

package com.noryan.nebulos.core.dolphin.jni.features.settings.model

object PostProcessing {
    @JvmStatic
    val shaderList: Array<String>
        external get

    @JvmStatic
    val anaglyphShaderList: Array<String>
        external get

    @JvmStatic
    val passiveShaderList: Array<String>
        external get
}
