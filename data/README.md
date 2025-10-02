# Data files

This folder contains the input CSVs used in our thesis.

- `classes.csv`

  - Columns: `class_id,class_name,lambda,supply`
  - Initial values based on the thesis summary:
    - SAR: lambda=0.75, supply=18207
    - EMS: lambda=0.25, supply=6423
  - You can tune `lambda` and update `supply` per scenario.

- `barangays.csv`
  - Columns: `id,name,hazard_level_text,flood_depth_ft,population,exposure,total_personnel`
  - hazard_level_text must be one of: Low, Medium, High
  - flood_depth_ft is in feet (convert from meters if needed)
  - population/exposure/total_personnel are optional; objective code can estimate missing values.
