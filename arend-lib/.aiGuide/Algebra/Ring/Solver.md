### Algebra.Ring.Solver

Reflective decision procedures for ring, semiring, commutative ring, lattice, and algebra equalities.

This module implements a generic normalization-based equality solver for (semi)ring expressions over a coefficient algebra. Terms are represented syntactically by `RingTerm`, then normalized into sorted, collapsed lists of monomials of the form `(List V, C)` — a variable word with a coefficient — whose interpretation back into the carrier is provably equal to the original term. The hierarchy of `*Data` classes specializes the framework to different algebraic settings (semiring, commutative semiring, ring, commutative ring, rationals, ℚ-algebras, distributive lattices), each plugging in the appropriate coefficient ring, comparison function, negation, and normalization strategy. The resulting `terms-equality` and `replace-consistent` lemmas are the entry points used by reflection meta-tactics to discharge ring/lattice equations.

#### Term Syntax

- **`RingTerm`**: Inductive datatype of ring expressions over coefficients `C` and variables `V`, with constructors `coef`, `var`, `:zro`, `:ide`, `:negative`, `:+`, `:*`.

#### Base Framework

- **`BaseData`**: Base class extending `CMonoidData`. Fixes a coefficient semiring `C` with decidable linear order `S`, a target semiring `R`, and a semiring homomorphism `alg : C -> R`. Requires `alg-comm` (coefficients commute with `R`-elements) and embeds monomial-level data via `mData : MonoidData`.
  - **`interpretRingNF`**: Interprets a normal-form list `List (\Sigma (List V) C)` as `Σ alg(c_i) * monomial(m_i)` in `R`.
  - **`cons`**: Rewrites `interpretRingNF (x :: l)` as the head term plus the tail.

#### Algebra-Level Solver

- **`AlgData`**: Extends `BaseData` with negation infrastructure (`pnegative`, `negate`, `interpretNF_negate`) and the full normalization/interpretation pipeline.
  - **`interpret`**: Recursive interpretation of a `RingTerm` into `R`.
  - **`interpretRingNF_++`**: Interpretation respects list concatenation as addition.
  - **`interpretNF_map`, `interpretNF_map-left`**: Interpretation of monomial lists scaled and prefixed/suffixed by a fixed monomial.
  - **`interpretNF_multiply`, `interpretNF_multiply'`**: Correctness of polynomial multiplication via the helper `multiply`/`multiply'`.
  - **`perm-consistent`, `sort-consistent`**: Normal-form interpretation is invariant under permutation/sorting of monomials.
  - **`collapse1`, `collapse`**: Merge adjacent monomials sharing the same variable word by adding their coefficients.
  - **`collapse1-consistent`, `collapse-consistent`**: Correctness of collapsing.
  - **`remove0`, `remove0-consistent`**: Drop monomials with zero coefficient.
  - **`normalize'`**: Recursive normalization of a `RingTerm` to a list of monomials (without sorting).
  - **`normalize`**: Full normalization: `normalize' t` followed by `sort`, `collapse`, and `remove0`.
  - **`normalize-consistent'`, `normalize-consistent`, `normalizeList-consistent`**: The interpretation of a normalized term equals that of the original.
  - **`terms-equality`**: Main solver entry — equality of normal forms implies equality of interpretations.
  - **`replace-consistent`, `replace-consistent-lem`**: Supports rewriting by a hypothesis `l = r` at selected monomial positions, used for guided rewriting tactics.
  - **`multiply`, `multiply'`** (in `\where`): Polynomial multiplication of monomial lists with an accumulator.

#### Commutative Algebra Solver

- **`CAlgData`**: Specialization of `AlgData` to commutative `R` (a `CSemiring`) with commutative monomial data. Variable lists inside monomials are sorted, allowing finer normalization.
  - **`normalize`**: Commutative variant — sorts each monomial's variable list before collapsing.
  - **`map_sort-consistent`**: Sorting variable lists inside each monomial preserves interpretation.
  - **`normalize-consistent`, `terms-equality`, `replace-consistent`**: Commutative analogues of the `AlgData` lemmas.

#### Specializations

- **`SemiringData`**: Instantiates `AlgData` with `C = NatSemiring` and trivial negation, giving a solver for arbitrary semirings.
- **`CSemiringData`**: Combines `CAlgData` and `SemiringData` for commutative semirings.
- **`LatticeData`**: Solver for bounded distributive lattices, reusing the commutative semiring framework with `C = Bool` (treating `∨` as `+`, `∧` as `*`).
  - **`ComparisonResult`, `compare`**: Three-valued comparison of variable lists under the lattice order (`lessOrEquals`, `greater`, `uncomparable`), used to absorb redundant joinands.
  - **`insert`, `insert-consistent`**: Insert a meet-monomial into a sorted join, eliminating absorbed entries.
  - **`collapse`, `collapse-consistent`**: Build the join of a list using `insert` to keep it minimal.
  - **`remove0`, `remove0-consistent`**: Drop monomials with `false` (zero) Bool coefficient, converting to a plain `List (List V)`.
  - **`interpretLatticeNF`** (with `cons`): Interpret a `List (List V)` as `⋁ (⋀ vars)`.
  - **`lData`**: Bundled `LData` instance for variable-list duplicate removal.
  - **`normalize`, `normalize-consistent`, `terms-equality`**: Full lattice-normal-form pipeline plus the equality entry point.
  - **`perm-consistent`, `sort-consistent`, `map_sort-consistent`, `map_removeDuplicates-consistent`**: Invariance lemmas for the lattice normalization stages.
  - **`lattice-lem`** (in `\where`): Absorption lemma `x ∧ a ∨ b = b` when `a ∨ b = b`.

#### Ring Specializations

- **`BaseRingData`**: Specializes `AlgData` to genuine rings with `RingHom` and pointwise negation of monomial coefficients.
  - **`interpretNF_negate`**: Default proof that `negative (interpretRingNF l) = interpretRingNF (negate l)`.
  - **`negate`** (in `\where`): Negates each monomial coefficient in a list.
- **`RingData`**: Concrete solver for arbitrary rings with `C = IntRing`.
- **`RatData`**: Solver for `RatField` itself (identity coefficient embedding).
- **`RatAlgebraData`**: Solver for ℚ-algebras, using the canonical coefficient homomorphism from ℚ.
- **`CRingData`**: Solver for commutative rings, combining `CAlgData` and `RingData`. Provides commutative versions of `normalize`, `normalize-consistent`, `terms-equality`, and `replace-consistent`.

#### Ideal Membership Helpers

- **`idealElem`**: Sum `Σ cᵢ * genᵢ` over a list of (coefficient, generator) pairs — a generic element of the ideal generated by the `genᵢ`.
- **`gensZeroToIdealZero`**: If every generator is zero, the resulting ideal element is zero.
  - **`argZeroToProdZero`**: `a * b = 0` when `b = 0`.
  - **`argsZeroToSumZero`**: `a + b = 0` when both are zero.
