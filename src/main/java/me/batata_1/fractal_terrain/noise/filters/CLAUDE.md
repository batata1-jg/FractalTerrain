# filters/

Noise post-filters. `ErosionFilter` is currently a stub (returns 0).

## Files

| File                | What                                          | When to read                    |
| ------------------- | --------------------------------------------- | ------------------------------- |
| `Filter.java`       | Abstract post-filter (`sample(x, z, y)`)      | Adding a noise filter           |
| `ErosionFilter.java`| Voronoi-based erosion filter (stub)           | Implementing erosion filtering  |
