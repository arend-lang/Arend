### Algebra.Field

Fields and discrete fields as the top of the commutative-algebra hierarchy.

A `Field` is presented as a local commutative ring in which apartness `#` coincides with invertibility, automatically yielding a GCD domain (every nonzero pair has gcd 1 via the inverse). A `DiscreteField` strengthens this with a decidable inversion operation `finv` satisfying `x ≠ 0 → x * finv x = 1`, which forces decidable equality, makes the ring Euclidean (with trivial division), and is equivalent to the constructive disjunction `(x = 0) ∨ Inv x`. The `\where` block also provides transport of the discrete-field structure across a ring isomorphism.

#### Classes

- **`Field`**: Extends `LocalCRing` and `GCDDomain`; fixes the apartness relation `#` to invertibility (`Inv x`) and proves all apartness axioms (sum-inv, nonzero zro, factor-left, etc.) plus the GCD-domain structure where every nonzero pair has gcd `1`.
- **`DiscreteField`**: Extends `Field` and `EuclideanDomain` with constructive inversion. Adds the field `eitherZeroOrInv : (x = 0) || Inv x` and a function `finv` with `finv 0 = 0` and `x ≠ 0 → x * finv x = 1`. Derives `decideEq`, locality, the Euclidean structure (quotient/remainder by inverse-multiplication), and the strict disjunction `Or` form via `aux`.

#### Discrete Field Operations

- **`finv`**: Total inversion function on `E`; returns `0` on zero and a multiplicative inverse otherwise.
- **`finv_zro`**: `finv 0 = 0`.
- **`finv-right`**: `x ≠ 0 → x * finv x = 1` (right inverse law).
- **`eitherZeroOrInv`**: Field axiom: every element is either zero or invertible (propositional `||`).

#### Default Implementations

- **`finv-impl`**: Default `finv` derived from `eitherZeroOrInv` via the strict-disjunction lemma `aux`.
- **`finv_zroImpl`**, **`id_finvImpl`**: Default proofs that the canonical `finv-impl` satisfies `finv_zro` and `finv-right`.

#### Lemmas about `finv`

- **`finv-left`**: `x ≠ 0 → finv x * x = 1` (left inverse law).
- **`finv-Inv`**: Witness that `finv x` is a left/right inverse of `x`, packaged as `Inv (finv x) x`.
- **`nonZero-Inv`**: Witness that `x` is invertible with inverse `finv x` (`Inv x (finv x)`).
- **`finv/=0`**: `x ≠ 0 → finv x ≠ 0`; the inverse of a nonzero element is nonzero.
- **`finv_ide`**: `finv 1 = 1`.
- **`finv_*`**: `finv (x * y) = finv y * finv x`; antimultiplicativity of inversion.
- **`finv_pow`**: `finv (x ^ n) = (finv x) ^ n`; commutes with natural-number powers.
- **`finv_finv`**: Involutivity: `finv (finv x) = x`.
- **`finv-diff`**: Telescoping identity `finv x - finv (x+1) = finv (x * (x+1))` for nonzero `x`, `x+1`.
- **`finv-square`**: The inverse of a square is a square.

#### Constructive Properties

- **`zeroDimensional`**: A discrete field has Krull dimension zero (`IsZeroDimensional`).
- **`decideZeroOrInv`**: Decides `(x = 0) Or Inv x` using the strict `Or` rather than the propositional `||`.

#### Transport Along Isomorphisms (`\where` block)

- **`aux`**: Upgrades the propositional `(x = 0) || Inv x` to the strict disjunction `Or`, using `0 ≠ 1` to show the two cases are mutually exclusive.
- **`backwards`**: Transports a `DiscreteField` structure on `f.Cod` back to `f.Dom` along a ring-hom equivalence `e : Equiv f`.
- **`forward`**: Transports a `DiscreteField` structure on `f.Dom` to `f.Cod` along an equivalence, by applying `backwards` to the inverse ring homomorphism.
