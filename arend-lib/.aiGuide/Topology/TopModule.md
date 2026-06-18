### Topology.TopModule

Topological left modules over a topological ring, combining module structure with continuous scalar multiplication.

A `TopLModule` extends both `LModule` (over a `TopRing`) and `TopAbGroup`, requiring scalar multiplication `*c : R × M → M` to be jointly continuous. Continuity of negation is derived for free from continuity of `*c` via multiplication by `-1`, mirroring the standard reduction in topological module theory. Hausdorff and complete variants are provided by mixing in the corresponding `TopAbGroup` refinements, and the file establishes that a topological ring is a module over itself and that products of topological modules are again topological modules.

#### Main Classes

- **`TopLModule`**: Topological left module over a `TopRing R`, extending `LModule` and `TopAbGroup`. Requires `*c-cont`: scalar multiplication is a continuous map `R × M → M`. Continuity of negation (`negative-cont`) is derived automatically from `*c-cont` via the homotopy `-x = -1 *c x`.
- **`HausdorffTopLModule`**: Topological module whose underlying space is Hausdorff; extends `TopLModule` and `HausdorffTopAbGroup`.
- **`CompleteTopLModule`**: Hausdorff topological module that is also complete as a topological abelian group; extends `HausdorffTopLModule` and `CompleteTopAbGroup`.

#### Instances and Constructions

- **`RingTopLModule`**: Canonical `TopLModule R R` structure on a topological ring `R` itself, with scalar multiplication given by ring multiplication. Witnesses that every `TopRing` is a topological module over itself.
- **`ProductTopLModule`**: Binary product `TopLModule R (\Sigma X Y)` of two topological `R`-modules `X` and `Y`, with componentwise scalar action `r *c (x, y) = (r *c x, r *c y)` and the product topology from `ProductTopAbGroup`.
- **`TopLModuleHasProduct`**: `HasProduct` instance for `TopLModule R`, registering `ProductTopLModule` as the categorical product so generic product machinery applies to topological modules.
