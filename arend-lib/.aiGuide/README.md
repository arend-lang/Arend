### arend-lib AI Guide Overview

This guide documents the Arend standard library (`arend-lib`), a formalized mathematics library built on homotopy type theory. Each top-level module is described below; directories have their own `README.md` with detailed per-file descriptions.

#### Foundation

- **`Paths.md`**: Core path (identity type) operations and lemmas.
- **`Equiv.md`**: Core equivalence types — sections, retractions, equivalences, embeddings, surjections.
- **`Equiv/`**: Theory of type equivalences in HoTT style — univalence, sigma/pi equivalences, and closure properties.
- **`Function.md`**: Basic function combinators and properties.
- **`Function/`**: Utilities for working with functions.
- **`Logic.md`**: Core logical types and propositions.
- **`Logic/`**: Classical logic principles, propositional utilities, first-order logic, and rewriting theory.
- **`Operations.md`**: Type-level product and coproduct operations.

#### Data Types

- **`Data/`**: Core data types (`Bool`, `Maybe`, `Or`, `Sigma`, `Fin`), collections (`Array`, `List`, `SubList`), and sequential colimits.

#### Sets and Relations

- **`Set.md`**: Decidability, set-level structures, and decidable sets.
- **`Set/`**: Set-level types, finiteness notions, filters, subsets, and categorical structure for sets.
- **`Relation/`**: Theory of relations, equivalence relations, quotients, and closures.

#### Order Theory

- **`Order/`**: Preorders, partial orders, strict orders, linear orders, lattices, and their interactions.

#### Algebra

- **`Algebra/`**: Pointed sets, monoids, groups, semirings, rings, domains, fields, modules, linear algebra, ordered structures, and equation solvers.

#### Arithmetic

- **`Arith/`**: Formalized arithmetic for standard number types (`Nat`, `Int`, `Rat`, `Real`) and related algebraic structures.

#### Category Theory

- **`Category.md`**: Core category theory definitions.
- **`Category/`**: Precategories, functors, natural transformations, limits, adjunctions, and topos theory.

#### Homotopy Theory

- **`Homotopy/`**: Homotopy type theory — h-levels, truncations, loop spaces, suspensions, spheres, pushouts, fibrations, the Hopf construction, Eilenberg–MacLane spaces, the torus, localization (modalities, Blakers–Massey, accessible/separated/connected types), and pointed types.

#### Topology and Analysis

- **`Topology/`**: Point-free and point-set topology, uniform and metric structures, normed algebraic structures (Banach spaces, C*-algebras), and functional-analytic constructions.
- **`Analysis/`**: Limits, derivatives (classical and synthetic), series, power series, and measure theory.

#### Algebraic Geometry

- **`AG/`**: Algebraic geometry in the locale-theoretic setting.

#### Combinatorics

- **`Combinatorics/`**: Combinatorial functions, counting arguments, and bijective proofs over finite types.
