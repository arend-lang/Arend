### Algebra.Ring.Integral.MinPoly

Minimal polynomials of elements over a ring homomorphism: definitions, characterizations, existence, and uniqueness.

#### Core Definitions

- **`isMinPoly`**: Predicate stating that `p : Poly f.Dom` is a minimal polynomial of `a : f.Cod` for `f : RingHom`: `p` evaluates to zero at `a` (via `polyMapEval`), and any polynomial `q` vanishing at `a` is divisible by `p`.
- **`isMonicMinPoly`**: Strengthens `isMinPoly` by additionally requiring `p` to be monic (`isMonic p`).

#### Uniqueness and Normalization

- **`isMonicMinPoly.unique`**: Over a strict domain, the monic minimal polynomial of `a` is unique: any two such `p, q` are equal.
- **`isMonicMinPoly.fromMinPoly`**: Over a discrete field, normalizes a nonzero minimal polynomial to a monic one by scaling by `finv (leadCoef p)`.

#### Quotient/Factor Equivalence

- **`integral_factor-equiv`**: For a minimal polynomial `p`, the canonical map `integral_factor f a p` from the polynomial ring quotient is an `Equiv`.

#### Degree-Based Characterization

- **`minPoly_degree-char`**: Over a discrete field, `p` being a minimum-degree annihilator of `a` is equivalent to `p` dividing every annihilator of `a`. Provides the bridge between the divisibility and minimum-degree formulations.
- **`minPoly_degree-char.minPoly_degree`**: Direct corollary over a decidable domain: if `p` divides every annihilator of `a`, then `degree p <= degree q` for any nonzero annihilator `q`.

#### Irreducibility and Primality

- **`irr_minPoly`**: Over a discrete field with nonzero target ring, an irreducible polynomial `p` (in `PolyAlgebra K`) that vanishes at `a` is automatically a minimal polynomial.
- **`irr_minPoly.aux`**: Inductive helper bounding `degree p` by `degree q` for nonzero annihilators `q` of `a`, given irreducibility of `p`.
- **`minPoly_prime`**: Over a discrete field with impotent target ring, a nonzero minimal polynomial is prime in the polynomial ring.
- **`minPoly-char`**: TFAE characterization (over discrete field, impotent target) of being a minimal polynomial: divisibility property, primality, irreducibility, and minimum-degree annihilator condition.

#### Existence Theorems

- **`finDim_minPoly`**: Existence (and uniqueness as a propositional `\level`) of a monic minimal polynomial when the subring `S` containing `f` and `a` is finitely generated as a `K`-module via a basis `l`.
- **`finDim_minPoly.finDim_minDegree`**: Existence of a nonzero minimum-degree annihilator over a Smith domain `R`, given a finite basis of `S` as an `R`-module.
- **`finDim_minPoly.finDim_minDegree.poly-dec`**: Decidability of "there exists a nonzero polynomial of degree `< n` annihilating `a`."
- **`finDim_minPoly.finDim_minDegree.minimize`**: From any nonzero annihilator of bounded degree, produces an annihilator of minimum degree.
- **`finExt_minPoly`**: Specialization to finite field extensions: if `E` admits a basis as a `K`-module via `f`, every `a : E` has a monic minimal polynomial.
- **`minPoly-exists`**: Equivalence between the existence of a monic minimal polynomial of `a` and the existence of a finite basis of the polynomial image subring `polyImage f a` as a `K`-module.
