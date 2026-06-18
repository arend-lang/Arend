### Topology

This directory formalizes topology in multiple complementary styles — open sets, Cauchy covers, uniformities, metrics, norms, and locales — together with their interactions with algebraic structures (groups, rings, modules, lattices, Banach and C*-algebras).

#### Foundational Spaces

- **`TopSpace.md`** — Topological spaces in the open-set style: continuous maps, neighborhood filters, density, Hausdorff conditions, and initial-topology subspaces.
- **`CoverSpace.md`** — Cover and precover spaces axiomatized by Cauchy families of covers, with regularity, rather-below relations `<=<`/`s<=<`, embeddings, the closure construction, and the lattice of (pre)cover structures.
- **`UniformSpace.md`** — Uniform and preuniform spaces presented via uniform covers, with star-refinement axioms, regularity variants, and uniform/locally-uniform maps.
- **`RatherBelow.md`** — Abstract "well-inside" relations on topological meet-semilattices, with derived one-step (`<=<o`) and fully interpolative (`<=<c`) refinements.

#### Compactness and Metrics

- **`Compact.md`** — Total boundedness, compactness, and local uniformity, including the upgrade of Cauchy covers to uniform covers under proper regularity.
- **`MetricSpace.md`** — Pseudo-metric and metric spaces with extended-upper-real or real distances, ball geometry, completeness, and the metric-map hierarchy (locally uniform, uniform, non-expanding, isometric).

#### Topological Algebra

- **`TopRing.md`** — Topological semigroups, monoids, rings, and near-(skew)fields, with rigidity lemmas for continuous maps into Hausdorff modules.
- **`TopAbGroup.md`** — Topological abelian groups with translation-invariant uniformity from `0`-neighborhoods, the `<=<ta` shrinking relation, and Hausdorff/morphism variants.
- **`TopModule.md`** — Topological left modules over a topological ring, with Hausdorff/complete variants and product instances.
- **`TopRieszSpace.md`** — Topological Riesz (vector lattice) spaces, with solid neighborhood bases making lattice operations and absolute value uniformly continuous.
- **`TopPoset.md`** — Hausdorff topological posets where `<=` is closed in the product topology, with monotone-net limits as joins/meets.

#### Normed and Banach Structures

- **`NormedAbGroup.md`** — Normed abelian groups in extended/real, pseudo/separated, bounded/unbounded variants, with norm-based maps and bilinear local uniformity.
- **`NormedRing.md`** — Normed and valued (pseudo-)rings with submultiplicative or multiplicative norms, including `RatValuedRing` and `RealValuedRing`.
- **`BanachSpace.md`** — (Pre-)Banach spaces over rationals or reals, with bounded linear maps and the Banach completion of a pre-Banach space.
- **`BanachAlgebra.md`** — Banach algebras with a constructive Newton-iteration square root for elements near the identity and a Banach-algebra completion.
- **`BanachLattice.md`** — Banach lattices (normed Riesz spaces with completeness), specializing to abstract L-spaces and M-spaces.
- **`StoneCStarAlgebra.md`** — Commutative real C*-algebras characterized equivalently by order or by the C*-norm identity, with `Real` as the canonical instance.

#### Specialized Constructions

- **`Elem.md`** — Subspace structures on `Elem S`: induced topological, cover, uniform, and metric structures via transfer along inclusion.
- **`Partial.md`** — Topology on `Partial Y`, characterizing continuous maps into partial values and lifting continuity through `plift` and `plift2`.
- **`ContGerm.md`** — Continuous germs at a point as quotients of locally-defined continuous maps, with pointwise algebraic structure inherited from the codomain.

#### Pointfree Topology

- **`Locale.md`** — Locales (point-free spaces) as frames satisfying the infinite distributive law, with nuclei, presentations, way-below/rather-below, Hausdorff conditions, and the bicomplete category `LocaleCat`.

#### Subdirectories

- **`TopSpace/`** — Further constructions on topological spaces (products, sums, etc.).
- **`CoverSpace/`** — Extended cover-space constructions (completions, products, sums).
- **`UniformSpace/`** — Extended uniform-space constructions and completions.
- **`MetricSpace/`** — Concrete metric spaces, completions, and metric-specific constructions.
- **`TopAbGroup/`** — Further topological abelian group constructions (products, completions, quotients).
- **`TopRing/`** — Topological ring constructions and instances.
- **`NormedAbGroup/`** — Concrete normed abelian groups and reflection/completion constructions.
- **`CStarAlgebra/`** — C*-algebra-specific constructions and theorems.
- **`Locale/`** — Specific locales and locale-theoretic constructions.
