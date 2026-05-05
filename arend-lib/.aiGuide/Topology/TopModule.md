### Topology.TopModule

Topological left modules over a topological ring, combining module structure with continuous scalar multiplication.

#### Classes

- **`TopLModule`**: Extends `LModule` and `TopAbGroup` over a `TopRing` `R`. Requires scalar multiplication `*c : R × M → M` to be continuous (`*c-cont`). Negation continuity is derived automatically from continuity of `*c` via `-1 *c x = -x`.
- **`HausdorffTopLModule`**: Extends `TopLModule` and `HausdorffTopAbGroup`; a topological module whose underlying space is Hausdorff.
- **`CompleteTopLModule`**: Extends `HausdorffTopLModule` and `CompleteTopAbGroup`; a Hausdorff topological module that is complete with respect to its uniform structure.

#### Constructions

- **`RingTopLModule`**: Canonical `TopLModule R R` structure on a topological ring `R` viewed as a module over itself, with scalar multiplication given by ring multiplication.
- **`ProductTopLModule`**: Product `TopLModule R (\Sigma X Y)` of two topological `R`-modules `X` and `Y`, with componentwise scalar action and product topology inherited from `ProductTopAbGroup`.
- **`TopLModuleHasProduct`**: `HasProduct` instance for `TopLModule R`, exposing `ProductTopLModule` as the binary product in the category of topological `R`-modules.
