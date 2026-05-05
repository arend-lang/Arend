### Topology.CoverSpace.TopSpace

Constructs a cover space structure on a regular topological space, exhibiting topological spaces as cover spaces.

#### Cover Space from Topology

- **`TopCover`**: Given a regular topological space `X`, produces a `CoverSpace` on `X` whose Cauchy covers are families `C` such that every point `x` has some `U ∈ C` containing an open neighborhood `V` of `x` with `V ⊆ U`. Establishes that regular topological spaces canonically carry a cover space structure.

#### Continuity

- **`top-cover-unit`**: The identity map `X → TopCover Xr` is continuous (`ContMap`), witnessing that the cover space topology induced by `TopCover` is compatible with the original topology on the regular space `X`.
