# rosgen/

Rosgen Level-I stream classification: measures reach slope and entrenchment from the raw decoded
elevation, then assigns the type that `RosgenProfile` uses to prescribe carve geometry.

## Files

| File                         | What                                                                        | When to read                                                       |
| ---------------------------- | --------------------------------------------------------------------------- | ------------------------------------------------------------------ |
| `README.md`                  | Key ordering rationale, measured-vs-prescribed inputs, dead-band scope, calibration state | Changing a threshold, diagnosing wrong stream types, before re-baselining |
| `RosgenKey.java`             | The ordered decision key + published dead band. Pure, no raster or graph    | Changing type boundaries, understanding why an ordering test fires |
| `ReachMetrics.java`          | The measured tuple one reach is classified from                             | Adding a measured input, checking what is observable vs prescribed |
| `ReachMetricsSampler.java`   | Slope from bed elevations; entrenchment from perpendicular transects        | Transect cost, walk bounds, why the raw buffer is required         |
| `ReachRosgenClassifier.java` | Reach segmentation + downstream-first graph walk; implements `ChannelTyper` | Reach length, cross-junction dead band, classification ordering    |
