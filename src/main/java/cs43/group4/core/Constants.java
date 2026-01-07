package cs43.group4.core;

/**
 * Global domain constants for allocation logic.
 */
public final class Constants {
    private Constants() {}

    /**
     * Flood depth threshold (feet) below which a barangay is considered unaffected
     * and must receive zero allocation. Equivalent to 0.2 meters.
     */
    public static final double UNAFFECTED_FLOOD_DEPTH_FT = 0.656168;
}
