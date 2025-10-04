package cs43.group4.utils;

import java.util.LinkedHashMap;
import java.util.Map;

public class AllocationResult {
    public String id;
    public String name;
    public Map<String, Long> allocations; // class_name -> amount
    public long total;

    public AllocationResult(String id, String name) {
        this.id = id;
        this.name = name;
        this.allocations = new LinkedHashMap<>();
    }
}
