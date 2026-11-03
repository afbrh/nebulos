// SPDX-License-Identifier: GPL-2.0-or-later

package com.noryan.nebulos.core.dolphin.jni.features.settings.model

interface AbstractStringSetting : AbstractSetting {
    val string: String

    fun setString(settings: Settings, newValue: String)
}
