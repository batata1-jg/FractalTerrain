# parameters/

Biome-parameter enums (relocated here in M-011). Each maps a climate/relief value onto a vanilla
noise-parameter band.

## Files

| File                   | What                                            | When to read                          |
| ---------------------- | ----------------------------------------------- | ------------------------------------- |
| `Band.java`            | Shared band interface; `containing` resolves a value by one forward scan, so enum constant order is load-bearing | Adding a parameter enum, reordering bands |
| `Continentalness.java` | Continentalness bands                           | Continentalness classification        |
| `ErosionLevel.java`    | Erosion bands                                   | Erosion classification                |
| `TemperatureLevel.java`| Temperature bands                               | Temperature classification            |
| `HumidityLevel.java`   | Humidity bands                                  | Humidity classification               |
| `PeaksValleys.java`    | Peaks-and-valleys bands                         | PV classification                     |
| `TempBand.java`        | Temperature-band grouping                       | Temperature grouping                  |
