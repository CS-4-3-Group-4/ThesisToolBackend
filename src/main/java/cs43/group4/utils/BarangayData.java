package cs43.group4.utils;

public class BarangayData {
    public int id;
    public String name;
    public String hazard_level_text;
    public double flood_depth_ft;
    public int population;
    public double vulnerability_index;
    public int total_personnel;
    public int sar_current;
    public int ems_current;
    public double lat;
    public double lon;

    public BarangayData(
            int id,
            String name,
            String hazard_level_text,
            double flood_depth_ft,
            int population,
            double vulnerability_index,
            int total_personnel,
            int sar_current,
            int ems_current,
            double lat,
            double lon) {
        this.id = id;
        this.name = name;
        this.hazard_level_text = hazard_level_text;
        this.flood_depth_ft = flood_depth_ft;
        this.population = population;
        this.vulnerability_index = vulnerability_index;
        this.total_personnel = total_personnel;
        this.sar_current = sar_current;
        this.ems_current = ems_current;
        this.lat = lat;
        this.lon = lon;
    }
}
