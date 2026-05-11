Now I have enough context to write the README. The example provided actually matches the structure I need to produce.

### arend-lib AI Guide Overview

This guide documents the Arend standard library (`arend-lib`), a formalized mathematics library built on homotopy type theory.

#### Foundation

- **`Paths.md`**: Core path (identity type) operations, transport, J-eliminators, and equational reasoning.
- **`Equiv.md`**: Equivalence types — sections, retractions, equivalences, embeddings, and surjections.
- **`Equiv/`**: Theory of type equivalences in HoTT style — fiber characterizations, half-adjoint coherence, and univalence.
- **`Function.md`**: Identity, composition, injectivity, surjectivity, and images.
- **`Function/`**: Iteration of endofunctions and related utilities.
- **`Logic.md`**: The empty type, propositional truncation, disjunction, propositional extensionality, and classical hooks.
- **`Logic/`**: Classical axioms, h-levels, TFAE infrastructure, first-order logic, and rewriting systems.
- **`Operations.md`**: Type-class abstractions for binary product (`⨯`) and coproduct (`⨿`) operations.

#### Data Types

- **`Data/`**: Booleans, optionals, sums, sigma pairs, finite indices, lists, length-indexed arrays, sublists, and sequential colimits.

#### Sets and Relations

- **`Set.md`**: Decidable propositions, base sets, subsets, separated sets, apartness, decidable equality, and set truncation.
- **`Set/`**: Hedberg's theorem, set-category structure, subsets and powerset locales, finite/countable sets, filters, and partial elements.
- **`Relation/`**: `\Prop`-valued binary relations, closures, equivalence relations, and set-quotients.

#### Order Theory

- **`Order/`**: Preorders, partial/strict/linear orders, lattices, Heyting and Boolean algebras, lexicographic orders, directed sets, and the categorical view.

#### Algebra

- **`Algebra/`**: Pointed sets, monoids, groups, semirings, rings, domains, fields, modules, linear algebra, ordered structures, colimits, and equation solvers.

#### Arithmetic

- **`Arith/`**: Formalized arithmetic for `Bool`, `Nat`, `Int`, `Rat`, `Real`, `Complex`, `Fin`, primes, and exponentials.

#### Category Theory

- **`Category.md`**: Precategories, univalent categories, morphism classes, the structure identity principle, and free categories on graphs.
- **`Category/`**: Functors, natural transformations, limits, adjunctions, Kan extensions, comma/slice categories, toposes, and additive categories.

#### Homotopy Theory

- **`Homotopy/`**: Pointed types, h-levels, truncations, fibers, suspensions, spheres, joins, loop spaces, pushouts, the Hopf fibration, Eilenberg–MacLane spaces, the torus, and modal/localization theory.

#### Topology and Analysis

- **`Topology/`**: Topological, cover, uniform, and metric spaces; topological algebra; normed/Banach/C*-algebraic structures; and locales (pointfree topology).
- **`Analysis/`**: Limits and convergence, infinite series, power series, derivatives (classical, strong, and synthetic), and measure theory.

#### Algebraic Geometry

- **`AG/`**: Affine and projective schemes formalized as locally ringed locales via Zariski-topology frame presentations.

#### Combinatorics

- **`Combinatorics/`**: Factorials, binomial coefficients, combinations, and (weak) compositions with their counting equivalences.
