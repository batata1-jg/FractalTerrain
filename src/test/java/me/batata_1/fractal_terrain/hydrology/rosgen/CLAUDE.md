# rosgen/ (test)

Tests for Rosgen Level-I classification; see `../../../main/java/.../hydrology/rosgen/README.md` for
the rules being pinned.

## Files

| File                              | What                                                            | When to read                                                  |
| --------------------------------- | --------------------------------------------------------------- | ------------------------------------------------------------- |
| `RosgenKeyTest.java`              | Key ordering and totality, including saturated entrenchment     | Changing type boundaries or the key's branch order            |
| `ReachMetricsSamplerTest.java`    | Slope and entrenchment measured over synthetic valley fields    | Changing transect bounds, step sizing, or the slope floor     |
| `ReachRosgenClassifierTest.java`  | Reach segmentation and downstream-first graph ordering          | Changing the graph walk or reach segmentation                 |
