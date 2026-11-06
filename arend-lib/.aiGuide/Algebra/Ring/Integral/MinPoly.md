### Algebra.Ring.Integral.MinPoly

Minimal polynomials of elements over a ring homomorphism, characterizing them via divisibility, irreducibility, and degree-minimality.

This module formalizes the theory of minimal polynomials in the general setting of a ring homomorphism `f : R -> E` and an element `a : E`. The central notion `isMinPoly` requires both that `p` annihilates `a` and that `p` divides every other annihilating polynomial — a divisibility-based definition that works constructively without assuming the field is decidable. Over discrete fields and impotent rings the module proves the classical equivalences between the divisibility, primality, irreducibility, and minimal-degree characterizations, and connects existence of a minimal polynomial to finiteness of the generated subalgebra as an `f`-module.

#### Core Definitions

- **`isMinPoly`**: The proposition that `p : Poly f.Dom` is a minimal polynomial of `a` over `f`: it evaluates to zero at `a` and left-divides every other polynomial that does so.
- **`isMonicMinPoly`**: A minimal polynomial that is additionally monic; pairs `isMonic p` with `isMinPoly f a p`.

#### Uniqueness and Normalization

- **`isMonicMinPoly.unique`**: Over a strict domain, the monic minimal polynomial of `a` is unique: any two such polynomials are equal.
- **`isMonicMinPoly.fromMinPoly`**: Over a discrete field, normalizes a nonzero minimal polynomial to a monic one by scaling by the inverse of its leading coefficient.

#### Quotient Equivalence

- **`integral_factor-equiv`**: For a minimal polynomial `p`, the canonical map from the polynomial-ring quotient `Poly R / (p)` into `E` (sending the class of `q` to `polyMapEval f q a`) is an equivalence onto its image — capturing that `p` cuts out exactly the relations satisfied by `a`.

#### Degree Characterization

- **`minPoly_degree-char`**: Over a discrete field, the divisibility condition in `isMinPoly` is equivalent to degree-minimality among nonzero annihilating polynomials.
- **`minPoly_degree-char.minPoly_degree`**: The forward direction: a polynomial that left-divides all annihilators of `a` has minimal degree among nonzero annihilators (over a decidable domain).

#### Irreducibility, Primality, and Equivalent Characterizations

- **`irr_minPoly`**: Over a discrete field with target a nonzero commutative ring, an irreducible polynomial that vanishes at `a` is a minimal polynomial of `a`.
- **`irr_minPoly.aux`**: Helper bounding the degree of `p` against any nonzero annihilator `q` of degree below a fixed bound, by induction on degree.
- **`minPoly_prime`**: Over a discrete field with target an impotent commutative ring, a nonzero minimal polynomial is prime in `Poly K`.
- **`minPoly-char`**: TFAE characterization: under the same hypotheses, the conditions (divisibility, primality, irreducibility, minimal degree) are all equivalent for a nonzero `p` annihilating `a`.

#### Finite-Dimensional Existence

- **`finDim_minPoly`**: When the subring `S ⊆ E` containing `f(K)` and `a` is a finite-dimensional `K`-module via a basis `l`, produces the (unique) monic minimal polynomial of `a` as an h-proposition-level Σ-type.
- **`finDim_minPoly.finDim_minDegree`**: Over a Smith domain, weakens the field hypothesis: there exists a nonzero polynomial annihilating `a` that has minimal degree among such polynomials.
- **`finDim_minPoly.finDim_minDegree.poly-dec`**: Decidability of the existence of a nonzero annihilating polynomial of degree less than `n`.
- **`finDim_minPoly.finDim_minDegree.minimize`**: Given any nonzero annihilator of bounded degree, produces a degree-minimal one by descent.

#### Existence Criteria

- **`finExt_minPoly`**: When `E` itself is finite-free as a `K`-module (with explicit basis `l`), every `a : E` has a monic minimal polynomial.
- **`minPoly-exists`**: Existence of a monic minimal polynomial of `a` is equivalent to the polynomial subalgebra `K[a] ⊆ E` being finite-free as a `K`-module.
