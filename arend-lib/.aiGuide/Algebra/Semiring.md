### Algebra.Semiring

Semirings: additive abelian monoids with a multiplicative monoid structure connected by distributivity.

This module builds the semiring hierarchy in stages: `PseudoSemiring` adds distributivity and zero-absorption to a non-unital multiplicative semigroup over an abelian monoid, while `Semiring` adds a multiplicative identity together with a natural-number coefficient map `natCoef : Nat -> E` whose recursive definition is exposed as overridable defaults so subclasses can replace it with a more efficient or more lawful implementation. Commutative variants (`PseudoCSemiring`, `CSemiring`) derive one distributivity law from the other via commutativity, avoiding redundant proof obligations. The bulk of the API consists of distributivity-driven lemmas about finite sums (`BigSum`, `FinSum`), divisibility (`LDiv`), powers, and the interaction of `natCoef` with the iterated-addition operator `*n`.

#### Core Classes

- **`PseudoSemiring`**: Extends `AbMonoid` and `Semigroup`; adds left/right distributivity (`ldistr`, `rdistr`) and zero-absorption laws (`zro_*-left`, `zro_*-right`). The `op` field gives the opposite pseudo-semiring (multiplication reversed).
- **`Semiring`**: Extends `PseudoSemiring` and `Monoid`. Adds a natural-number coefficient embedding `natCoef : Nat -> E` with default implementation by recursion, plus equations `natCoefZero` and `natCoefSuc` characterizing it. The `op` field gives the opposite semiring.
- **`NonZeroSemiring`**: A `Semiring` with `zro/=ide`; provides `inv-nonZero` showing any invertible element is nonzero.
- **`PseudoCSemiring`**: Extends `PseudoSemiring` and `CSemigroup`; supplies all distributivity/zero-absorption laws automatically from commutativity, requiring only one direction in instances.
- **`CSemiring`**: Extends `Semiring`, `PseudoCSemiring`, and `CMonoid`. Provides `divQuotient_un0` lifting equality in the divisibility quotient back to equality with zero.

#### Divisibility

- **`zero-div`**: `LDiv x 0 0` — every element divides zero with quotient zero.
- **`ldiv=0`**: If `LDiv x y` and `x = 0`, then `y = 0`.
- **`ldiv/=0`**: If `y /= 0` and `x` divides `y`, then `x /= 0`.
- **`LDiv_+`**: If `a | b` and `a | c`, then `a | b + c`, with quotient the sum of quotients.
- **`LDiv_BigSum`**: If `a` divides each entry of an array `l`, then `a` divides `BigSum l`.
- **`associates0`**: If `a` and `b` are associates and `a = 0`, then `b = 0`.

#### Natural Coefficients and Iterated Addition

- **`natCoef_*_*n`**: `natCoef n * a = n *n a` — multiplying by `natCoef n` equals `n`-fold addition.
- **`natCoef_*_*n-right`**: Right-multiplication version: `a * natCoef n = n *n a`.
- **`natCoef_*n`**: `natCoef n = n *n 1`.
- **`*n-comm-left`**: `n *n (a * b) = (n *n a) * b`.
- **`*n-comm-right`**: `n *n (a * b) = a * (n *n b)`.
- **`natCoef_+`**: `natCoef (n + m) = natCoef n + natCoef m` (additive homomorphism).
- **`natCoef-comm`**: `natCoef n * a = a * natCoef n` — natural coefficients are central.
- **`natCoef_*`**: `natCoef (n * m) = natCoef n * natCoef m` (multiplicative homomorphism).

#### Distributivity over Sums

- **`BigSum-ldistr`**: `x * BigSum l = BigSum (map (x *) l)`.
- **`FinSum-ldistr`**: Left distributivity over finite indexed sums.
- **`BigSum-rdistr`**: `BigSum l * z = BigSum (\lam i => l i * z)`.
- **`FinSum-rdistr`**: Right distributivity over finite indexed sums.
- **`FinSum-distr`**: `FinSum x * FinSum y = FinSum (\lam s => x s.1 * y s.2)` — product of finite sums as a sum over the product index set.

#### Replication and Products

- **`BigSum_replicate`**: `BigSum (replicate n x) = natCoef n * x`.
- **`BigSum_replicate1`**: `BigSum (replicate n ide) = natCoef n`.
- **`FinSum_replicate`**: Constant `FinSum` over a finite set equals `natCoef (card) * x`.
- **`BigProd_zro`**: A finite product containing a zero entry equals zero.
- **`pow_0`**: `0^n = 0` for `n /= 0`.

#### Degenerate Cases

- **`trivial`**: If `0 = 1`, then any two elements are equal — the semiring is trivial.
