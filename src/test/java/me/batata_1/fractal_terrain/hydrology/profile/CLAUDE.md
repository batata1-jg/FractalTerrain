# profile/ (test)

Gates the bed-carve geometry: one signed distance per channel, and the blend that cuts it in.

## Files

| File                           | What                                                                                  | When to read                                                          |
| ------------------------------ | ------------------------------------------------------------------------------------- | --------------------------------------------------------------------- |
| `NearestChannelSampleTest.java`| Foot-point sampling: exact perpendicular on a straight reach, sign agreement with the knot tangent line, downstream-oriented interpolation, cross-channel and non-consecutive-knot fallbacks | Changing `sampleNearestChannel`, segment selection, or normal interpolation |
| `BlendMinTest.java`            | `RosgenProfile.blendMin` contract: exact `min` outside the range, never above the hard min, continuous at the boundary, symmetric, and a river never raises terrain | Changing the carve blend or `CARVE_BLEND_RANGE`                       |
| `PolylineChordErrorTest.java`  | Bounds two-segment polyline error against an analytic arc; justifies not rebuilding the quintic | Revisiting the polyline approximation or knot spacing                 |
