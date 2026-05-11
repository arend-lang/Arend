### Arith.Real.Root

Real n-th roots and square roots, defined via the inverse of the power map on nonnegative reals.

This module constructs `root n x` as the unique nonnegative real whose n-th power equals `x ∨ 0`, obtained by inverting the strictly monotone, continuous power function `pow _ n` on the subspace of nonnegative reals. The construction relies on `monotone-inverse` together with continuity of `pow` (via `TopMonoid.pow-cont`) and the intermediate value theorem machinery from `Arith.Real.IVT`, which is why the input is clamped to `x ∨ 0` to ensure a nonnegative argument. From this primitive, the standard algebraic and order-theoretic properties of roots are derived, and `sqrt` is defined as the specialization to `n = 2`.

#### N-th Root

- **`root`**: The n-th root function `root : Nat -> Real -> Real`. Returns `0` when `n = 0`, otherwise inverts `pow _ (suc n)` on nonnegative reals applied to `x ∨ 0`.
- **`root.rootEquiv`**: The underlying equivalence between nonnegative reals given by the n-th power map (for `n /= 0`), built via `monotone-inverse` using strict monotonicity and continuity of `pow`.

#### Root Properties

- **`root>=0`**: `0 <= root n x` — roots are always nonnegative.
- **`root_pow`**: `root n (pow x n) = x` for `n /= 0` and `x >= 0`; left inverse on nonnegative reals.
- **`pow_root`**: `pow (root n x) n = x` for `n /= 0` and `x >= 0`; right inverse on nonnegative reals.
- **`root_zro`**: `root n 0 = 0`.
- **`root_ide`**: `root n 1 = 1` for `n /= 0`.
- **`root-monotone`**: Strict monotonicity of `root n` on nonnegative reals: `x < y` implies `root n x < root n y`.
- **`root>0`**: Roots preserve positivity: `0 < x` implies `0 < root n x`.
- **`root-rat`**: Rational approximation of roots: for `0 <= a < b`, there exist rationals `0 < q < r` with `a < pow q n` and `pow r n < b`, sandwiching the n-th root by rational powers.

#### Square Root

- **`sqrt`**: Square root, defined as `root 2 x`.
- **`sqrt>=0`**: `0 <= sqrt x`.
- **`sqrt_pow`**: `sqrt (x * x) = x` for `x >= 0`.
- **`pow_sqrt`**: `sqrt x * sqrt x = x` for `x >= 0`.
- **`sqrt_zro`**: `sqrt 0 = 0`.
- **`sqrt_ide`**: `sqrt 1 = 1`.
- **`sqrt-monotone`**: Strict monotonicity of `sqrt` on nonnegative reals.
- **`sqrt>0`**: `sqrt` preserves strict positivity.
- **`sqrt-rat`**: Rational sandwich for square roots: for `0 <= a < b`, there exist rationals `0 < q < r` with `a < q * q` and `r * r < b`.
