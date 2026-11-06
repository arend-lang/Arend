### Algebra.Ring.Solver

Reflective decision procedures for ring, semiring, commutative ring, lattice, and algebra equalities.

This module implements a generic normalization-based equality solver for (semi)ring expressions over a coefficient algebra. Terms are represented syntactically by `RingTerm`, then normalized into sorted, collapsed lists of monomials of the form `(List V, C)` — a variable word with a coefficient — whose interpretation back into the carrier is provably equal to the original term. The hierarchy of `*Data` classes specializes the framework to different algebraic settings (semiring, commutative semiring, ring, commutative ring, rationals, ℚ-algebras, distributive lattices), each plugging in the appropriate coefficient ring, comparison function, negation, and normalization strategy. The resulting `terms-equality` and `replace-consistent` lemmas are the entry points used by reflection meta-tactics to discharge ring/lattice equations.

#### Term Syntax

- **`RingTerm`**: Inductive datatype of ring expressions over coefficients `C` and variables `V`, with constructors `coef`, `var`, `:zro`, `:ide`, `:negative`, and the infix binary constructors `:_+` (sum, `infixl 6`) and `:*` (product, `infixl 7`).

#### Base Framework

- **`BaseData`**: Base class extending `CMonoidData`. Fixes a coefficient semiring `C` with decidable linear order `S`, a target semiring `R`, and a semiring homomorphism `alg : C -> R`. Requires `alg-comm` (coefficients commute with `R`-elements) and embeds monomial-level data via `mData : MonoidData`.
  - **`interpretRingNF`**: Interprets a normal-form list `List (\Sigma (List V) C)` as `Σ alg(c_i) * monomial(m_i)` in `R`.
  - **`BaseData.cons`**: Rewrites `interpretRingNF (x :: l)` as the head term plus the tail.

#### Algebra-Level Solver

- **`AlgData`**: Extends `BaseData` with negation infrastructure (`pnegative`, `negate`, `interpretNF_negate`). Its body contains the interpretation of terms and the normalization pipeline, listed below.
  - **`interpret`**: Recursive interpretation of a `RingTerm` into `R`.
  - **`interpretRingNF_++`**: Interpretation respects list concatenation as addition.
  - **`interpretNF_map`, `interpretNF_map-left`**: Interpretation of monomial lists scaled and prefixed/suffixed by a fixed monomial.
  - **`interpretNF_multiply`, `interpretNF_multiply'`**: Correctness of polynomial multiplication via the helper `multiply`/`multiply'`.
  - **`AlgData.perm-consistent`**: Interpretation of a normal form is invariant under a permutation `Perm l l'` of its monomials.
  - **`AlgData.sort-consistent`**: Sorting a monomial list preserves its interpretation.
  - **`collapse1`**, **`AlgData.collapse`**: Merge adjacent monomials sharing the same variable word by adding their coefficients.
  - **`collapse1-consistent`**, **`AlgData.collapse-consistent`**: Correctness of collapsing: `interpretRingNF l = interpretRingNF (collapse l)`.
  - **`AlgData.remove0`**, **`AlgData.remove0-consistent`**: Drop monomials with zero coefficient, preserving the interpretation.
  - **`normalize'`**: Recursive normalization of a `RingTerm` to a list of monomials (without sorting).
  - **`AlgData.normalize`**: Full normalization: `normalize' t` followed by `sort`, `collapse`, and `remove0`.
  - **`normalize-consistent'`**, **`AlgData.normalize-consistent`**, **`normalizeList-consistent`**: The interpretation of a normalized term equals that of the original.
  - **`AlgData.terms-equality`**: Main solver entry — equality of normal forms implies equality of interpretations.
  - **`AlgData.replace-consistent`**, **`replace-consistent-lem`**: Supports rewriting by a hypothesis `l = r` at selected monomial positions, used for guided rewriting tactics.
  - **`multiply`, `multiply'`** (in `\where`): Polynomial multiplication of monomial lists with an accumulator.

#### Commutative Algebra Solver

- **`CAlgData`**: Specialization of `AlgData` to commutative `R` (a `CSemiring`) with commutative monomial data. Variable lists inside monomials are sorted, allowing finer normalization.
  - **`CAlgData.normalize`**: Commutative variant — sorts each monomial's variable list before collapsing.
  - **`CAlgData.map_sort-consistent`**: Sorting variable lists inside each monomial preserves interpretation.
  - **`CAlgData.normalize-consistent`**, **`CAlgData.terms-equality`**, **`CAlgData.replace-consistent`**: Commutative analogues of the `AlgData` lemmas.

#### Specializations

- **`SemiringData`**: Instantiates `AlgData` with `C = NatSemiring` and trivial negation, giving a solver for arbitrary semirings.
- **`CSemiringData`**: Combines `CAlgData` and `SemiringData` for commutative semirings.
- **`LatticeData`**: Solver for bounded distributive lattices, reusing the commutative semiring framework with `C = Bool` (treating `∨` as `+`, `∧` as `*`).
  - **`ComparisonResult`, `compare`**: Three-valued comparison of variable lists under the lattice order (`lessOrEquals`, `greater`, `uncomparable`), used to absorb redundant joinands.
  - **`insert`, `insert-consistent`**: Insert a meet-monomial into a sorted join, eliminating absorbed entries.
  - **`LatticeData.collapse`**, **`LatticeData.collapse-consistent`**: Build the join of a list using `insert` to keep it minimal; the lemma states the interpretation is unchanged.
  - **`LatticeData.remove0`**, **`LatticeData.remove0-consistent`**: Drop monomials with `false` (zero) Bool coefficient, converting to a plain `List (List V)`; the lemma relates `interpretRingNF l` to `interpretLatticeNF (remove0 l)`.
  - **`LatticeData.interpretLatticeNF`** (with `LatticeData.interpretLatticeNF.cons`): Interpret a `List (List V)` as `⋁ (⋀ vars)`.
  - **`lData`**: Bundled `LData` instance for variable-list duplicate removal.
  - **`LatticeData.normalize`**, **`LatticeData.normalize-consistent`**, **`LatticeData.terms-equality`**: Full lattice-normal-form pipeline plus the equality entry point.
  - **`LatticeData.perm-consistent`**, **`LatticeData.sort-consistent`**, **`LatticeData.map_sort-consistent`**, **`map_removeDuplicates-consistent`**: Invariance lemmas for the lattice normalization stages (permutation, sorting, sorting inside each monomial, duplicate removal).
  - **`lattice-lem`** (in `\where`): Absorption lemma `x ∧ a ∨ b = b` when `a ∨ b = b`.

#### Ring Specializations

- **`BaseRingData`**: Specializes `AlgData` (adding the negation fields) to genuine rings with `RingHom` and pointwise negation of monomial coefficients.
  - **`BaseRingData.interpretNF_negate`**: Default proof that `negative (interpretRingNF l) = interpretRingNF (negate l)`.
  - **`BaseRingData.negate`** (in `\where`): Negates each monomial coefficient in a list.
- **`RingData`**: Concrete solver for arbitrary rings with `C = IntRing`.
- **`RatData`**: Solver for `RatField` itself (identity coefficient embedding).
- **`RatAlgebraData`**: Solver for ℚ-algebras, using the canonical coefficient homomorphism from ℚ.
- **`CRingData`**: Solver for commutative rings, combining `CAlgData` and `RingData`. Provides commutative versions of `normalize`, `normalize-consistent`, `terms-equality`, and `replace-consistent`.
  - **`CRingData.normalize`**, **`CRingData.normalize-consistent`**, **`CRingData.terms-equality`**, **`CRingData.replace-consistent`**: The ring-level (with negation) counterparts of the `CAlgData` entries.

#### Ideal Membership Helpers

- **`idealElem`**: Sum `Σ cᵢ * genᵢ` over a list of (coefficient, generator) pairs — a generic element of the ideal generated by the `genᵢ`.
- **`gensZeroToIdealZero`**: If every generator is zero, the resulting ideal element is zero.
  - **`argZeroToProdZero`**: `a * b = 0` when `b = 0`.
  - **`argsZeroToSumZero`**: `a + b = 0` when both are zero.
