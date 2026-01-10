package cs43.group4.utils;

public class MathUtils {

    /**
     * Rounds a double value to the specified number of decimal places.
     * @param value the value to round
     * @param decimalPlaces number of decimal places
     * @return rounded value
     */
    public static double round(double value, int decimalPlaces) {
        double scale = Math.pow(10, decimalPlaces);
        return Math.round(value * scale) / scale;
    }
}
