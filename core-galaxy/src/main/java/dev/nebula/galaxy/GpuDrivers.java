package dev.nebula.galaxy;

/** Stand-in for Nebula's launcher-side GpuDrivers (Kotlin, not part of this core): GameActivity only reads the name of the start extra. */
final class GpuDrivers {
    static final String EXTRA = "nebula.gpuDriver";
    private GpuDrivers() {}
}
