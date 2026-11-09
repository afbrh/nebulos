package com.noryan.nebulos.core.watermelonds

import android.app.Application

/**
 * Stand-in for the real class in :core-watermelonds, compiled only when that module is not part of the build (a build with a subset of
 * cores, -Pnebulos.cores=...). NebulosApp extends it either way; without WatermelonDS there is nothing extra to start.
 */
open class NebulosHiltApplication : Application()
