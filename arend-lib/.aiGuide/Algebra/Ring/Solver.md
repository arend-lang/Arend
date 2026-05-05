### Algebra.Ring.Solver

Reflective normalization machinery for solving equalities in semirings, rings, commutative rings, lattices, and algebras over a base ring.

#### Term Syntax

- **`RingTerm`**: Inductive datatype of ring expressions over coefficient type `C` and variables `V`, with constructors `coef`, `var`, `:zro`, `:ide`, `:negative`, `:+`, `:*`.

#### Base Data Classes

- **`BaseData`**: Extends `CMonoidData`. Bundles a coefficient `Semiring C` with decidable linear order `S`, a target `Semiring R`, a `SemiringHom alg : C -> R`, a commutativity proof `alg-comm` (coefficients commute with all elements), and monomial monoid data `mData`.
- **`AlgData`**: Extends `BaseData`. Adds a unary operation `pnegative : R -> R`, a list-level `negate` on normal-form monomials, and the compatibility lemma `interpretNF_negate`. Provides multiplication helpers `multiply'`/`multiply` that distribute polynomial products in normal form.
- **`CAlgData`**: Extends `AlgData` with commutative `R : CSemiring` and commutative monomial data; `alg-comm` is derived from `*-comm`.

#### Specialized Instances

- **`SemiringData`**: Extends `AlgData` with `C = NatSemiring`, the canonical `natMap` coefficient embedding, and trivial negation (identity), used to solve semiring identities.
- **`CSemiringData`**: Combines `CAlgData` and `SemiringData` for commutative semirings.
- **`LatticeData`**: Extends `CAlgData` to bounded distributive lattices `L`, viewed as a semiring over `BoolLattice`; coefficients `false`/`true` map to `bottom`/`top`. Includes `lattice-lem` for absorption-style rewriting.
- **`BaseRingData`**: Extends `AlgData` for rings, using ring `negative` for `pnegative` and a list negation `negate` that pushes negation pointwise through monomial coefficients.
- **`RingData`**: `BaseRingData` with `C = IntRing` via `intMap`; the standard integer-ring solver instance.
- **`RatData`**: `BaseRingData` with `C = R = RatField` and identity coefficient hom; specializes the solver to `RatField`.
- **`RatAlgebraData`**: `BaseRingData` for an `AAlgebra RatField`, using `coefHom` and proving `alg-comm` via `*c`/`*` interaction lemmas.
- **`CRingData`**: Combines `CAlgData` and `RingData` for commutative rings.

#### Ideal Membership

- **`idealElem`**: Given a list of `(coefficient, generator)` pairs in a `CRing`, returns the linear combination `Σ cᵢ · genᵢ`.
- **`gensZeroToIdealZero`**: If every generator in the list is zero, then `idealElem` evaluates to `0`. Used to discharge ideal-membership goals where generators are known to vanish.
- **`argZeroToProdZero`**, **`argsZeroToSumZero`**: Helper lemmas for zero-propagation through `*` and `+` in a `CRing`.
