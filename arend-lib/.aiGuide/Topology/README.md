### Topology Directory Overview

This directory provides point-free and point-set topology, uniform and metric structures, normed algebraic structures, and functional-analytic constructions.

#### Foundational Structures

- **`TopSpace.md`**: Topological spaces, continuous maps, density, Hausdorff conditions, and subspace/transfer topologies.
- **`TopSpace/`**: Subdirectory contains `Category.md` (category of topological spaces) and `Product.md` (product topology with projections, pairing, and density lemmas).
- **`CoverSpace.md`**: Cover spaces and precover spaces defined via Cauchy covers, with refinement, regularity, and the rather-below relation.
- **`CoverSpace/`**: Subdirectory contains `Category.md` (category instances), `Complete.md` (Cauchy filters and completions), `CompletionTools.md` (lifting maps on completions), `Directed.md` (cover spaces on directed sets), `Locale.md` (adjunction between precover spaces and locales), `Product.md` (product cover spaces), `RelativelyComplete.md` (relative completion), `StronglyComplete.md` (strong completion via strongly regular Cauchy filters), `Subspace.md` (open/closed subspace cover structures), `TopSpace.md` (cover space structure on regular topological spaces).
- **`UniformSpace.md`**: Uniform spaces and morphisms via uniform covers, star refinements, and regularity.
- **`UniformSpace/`**: Subdirectory contains `Complete.md` (uniform completion), `InfReal.md` (uniform structure on extended non-negative reals), `Product.md` (product uniform spaces), `StronglyComplete.md` (strong uniform completion).
- **`Locale.md`**: Locales as complete distributive lattices, frame homomorphisms, nuclei, frame presentations, and categories of locales.
- **`Locale/`**: Subdirectory contains `Points.md` (adjunction between locales and topological spaces via completely prime filters), `Real.md` (locale of real numbers via rational intervals), `Uniform.md` (uniform locales with completion).

#### Metric Spaces

- **`MetricSpace.md`**: Metric and pseudometric spaces on extended upper reals, with uniform structures, open balls, continuity, and completeness.
- **`MetricSpace/`**: Subdirectory contains `Compact.md` (total boundedness of Manhattan product balls), `Complete.md` (pseudometric completion), `ExComplete.md` (extended metric completion), `ManhattanProduct.md` (L¹ product distance), `Nat.md` (metric on natural numbers), `UpperReal.md` (complete extended metric on upper reals).

#### Topological Algebra

- **`TopAbGroup.md`**: Topological abelian groups with continuous addition/negation and induced uniform structure.
- **`TopAbGroup/`**: Subdirectory contains `Complete.md` (completion of topological abelian groups) and `Product.md` (product construction and continuity lemmas).
- **`TopPoset.md`**: Hausdorff topological partially ordered sets with closed order relation.
- **`TopModule.md`**: Topological left modules over a topological ring with continuous scalar multiplication.
- **`TopRing.md`**: Topological rings/monoids with continuity, including near-fields with dense invertible elements.
- **`TopRing/`**: Subdirectory contains `Real.md` (real numbers as a topological near-field).
- **`TopRieszSpace.md`**: Topological Riesz spaces with continuous lattice operations and solid neighborhoods of zero.

#### Normed Structures

- **`NormedAbGroup.md`**: Normed abelian groups with norm-compatible (pseudo)metric structure, including extended-real-valued, bounded, complete, and uniform variants.
- **`NormedAbGroup/`**: Subdirectory contains `ExComplete.md` (completion of extended pseudo-normed abelian groups), `Real.md` (normed structures on rationals/reals with density lifting).
- **`NormedAbGroup/Real/`**: Subdirectory contains `Compact.md` (total boundedness of bounded real intervals) and `Functions.md` (continuity/uniform-continuity of fundamental real-valued operations).
- **`NormedRing.md`**: Normed and valued (pseudo) rings with submultiplicative norms, completeness, and canonical instances on `Rat`/`Real`.
- **`BanachSpace.md`**: Banach spaces (complete normed abelian groups with scalar multiplication) and bounded linear maps.
- **`BanachAlgebra.md`**: Banach algebras (complete normed algebras) with real instances and a completion functor.
- **`BanachLattice.md`**: Banach lattices (Banach spaces with compatible Riesz structure), including L-space and M-space variants.

#### C*-Algebras

- **`StoneCStarAlgebra.md`**: C*-algebras over the reals with Stone-style square-sum and square-norm laws.
- **`CStarAlgebra/`**: Subdirectory contains `CompleteStoneCStarAlgebra.md` (homomorphisms and completion of ordered C*-algebras into Stone C*-algebras) and `UnitCStarAlgebra.md` (unitization of non-unital C*-algebras with Riesz space structure).

#### Utilities

- **`Compact.md`**: Total boundedness, compactness, and local uniformity for cover spaces via uniform/Cauchy covers and metric balls.
- **`RatherBelow.md`**: The "rather below" relation on topological meet-semilattices, axiomatizing the way-below/well-inside relation.
- **`Elem.md`**: Subspace structures on `Elem S` (elements of a subset) for various topological structures, obtained by transfer along inclusion.
- **`Partial.md`**: Topology on partial elements `Partial Y`, lifting continuous maps along partiality.
- **`ContGerm.md`**: Continuous germs at a point (equivalence classes of maps on open neighborhoods) with inherited algebraic structure.
