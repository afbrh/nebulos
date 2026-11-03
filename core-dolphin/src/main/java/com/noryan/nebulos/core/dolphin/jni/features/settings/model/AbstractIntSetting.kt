// SPDX-License-Identifier: GPL-2.0-or-later

package com.noryan.nebulos.core.dolphin.jni.features.settings.model

interface AbstractIntSetting : AbstractSetting {
    val int: Int

    fun setInt(settings: Settings, newValue: Int)
}
