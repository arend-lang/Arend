### Arith.Complex.Norm

The modulus on the complex numbers, its triangle inequality, the resulting normed-ring structure, and the unit circle.

The module defines the squared modulus `cabs2` (a polynomial expression, so that identities stay algebraic) and the modulus `cabs` as its square root. It proves that `cabs` is multiplicative, satisfies the triangle inequality (via Cauchy-Schwarz) and vanishes only at zero. This makes `Complex` a normed abelian group `ComplexNormed` and a pseudo-valued ring `ComplexValuedRing`. The module also has the real and imaginary part projections, the embedding of the reals and multiplication by $i$ and conjugation as normed maps, the characterization of invertibility by positive modulus, and the unit circle predicate `OnUnitCircle` with its closure properties.

#### Squared modulus

- **`cabs2`**: `cabs2 z = z.re * z.re + z.im * z.im`.
- **`cabs2>=0`**: `cabs2` is nonnegative.
- **`cabs2_zro` / `cabs2_ide`**: `cabs2 0 = 0` and `cabs2 1 = 1`.
- **`cabs2_negative`**: `cabs2` is invariant under negation.
- **`cabs2_*`**: `cabs2` is multiplicative, $|xy|^2 = |x|^2|y|^2$.
- **`cabs2_*_CS`**: the two-square (Brahmagupta-Fibonacci) identity $|x|^2|y|^2 - (\Re x \Re y + \Im x \Im y)^2 = (\Re x \Im y - \Im x \Re y)^2$.
- **`CS_<=`**: Cauchy-Schwarz in squared form, $(\Re x \Re y + \Im x \Im y)^2 \leq |x|^2|y|^2$.
- **`cabs2_i`**: `cabs2 i = 1`.
- **`cabs2_conj`**: `cabs2` is invariant under conjugation.

#### Modulus

- **`cabs`**: the modulus $|z| = \sqrt{(\Re z)^2 + (\Im z)^2}$.
- **`cabs>=0`**: `cabs` is nonnegative.
- **`cabs_zro` / `cabs_ide`**: `cabs 0 = 0` and `cabs 1 = 1`.
- **`cabs_negative`**: `cabs (negative z) = cabs z`.
- **`cabs-square`**: `cabs z * cabs z = cabs2 z`.
- **`cabs_fromReal`**: the modulus of a real number viewed as complex is its absolute value.
- **`cabs_*`**: the modulus is multiplicative.
- **`cabs_pow`**: `cabs (pow z k) = pow (cabs z) k`.
- **`cabs_zro-ext`**: `cabs c = 0` implies `c = 0`.
- **`abs_i`**: `cabs i = 1`.
- **`cabs_i_*`**: `cabs (i * z) = cabs z`.
- **`cabs_conj`**: `cabs (conj z) = cabs z`.

#### Triangle inequalities

- **`cabs_+`**: the triangle inequality, `cabs (x + y) <= cabs x + cabs y`.
- **`cabs_-`**: `cabs (x - y) <= cabs x + cabs y`.
- **`cabs_BigSum`**: the modulus of a finite sum is at most the sum of the moduli.
- **`cabs_-comm`**: `cabs (x - y) = cabs (y - x)`.
- **`cabs_-triang`**: `cabs (x - z) <= cabs (x - y) + cabs (y - z)`.
- **`cabs-rev-triang`**: the reverse triangle inequality `cabs x - cabs y <= cabs (x + y)`.
- **`cabs_re_<=` / `cabs_im_<=`**: the absolute values of the real and imaginary parts are at most the modulus.

#### Normed structure

- **`ComplexNormed`**: `Complex` as a normed abelian group with norm `cabs`; its metric structure is derived from the norm.
- **`ComplexValuedRing`**: `Complex` as a pseudo-valued ring, the norm being multiplicative with `|1| = 1`.
- **`Re` / `Im`**: the real and imaginary part projections as non-expansive normed group maps `ComplexNormed -> RealNormed`.
- **`fromRealMap`**: `Complex.fromReal` as an isometric normed group map from `RealNormed` to `ComplexNormed`.
- **`mulIMap`**: multiplication by `i` as an isometric normed group map.
- **`conjMap`**: complex conjugation as an isometric normed group map.
- **`cabs_dist_U`**: for rational `eps`, `cabs (x - y) < eps` if and only if `eps` lies in the upper cut of `dist x y`.

#### Conjugation and invertibility

- **`mul-conj`**: `z * conj z = Complex.fromReal (cabs2 z)`.
- **`cabs>0_Inv`**: `0 < cabs z` if and only if `z` is invertible.

#### Unit circle

- **`OnUnitCircle`**: `u` lies on the unit circle, expressed as `cabs2 u = 1`.
- **`cabs=1_OnUnitCircle`**: `cabs u = 1` implies `OnUnitCircle u`.
- **`OnUnitCircle_*`**: the unit circle is closed under products.
- **`OnUnitCircle_negative`**: the unit circle is closed under negation.
- **`OnUnitCircle_conj`**: the unit circle is closed under conjugation.
- **`OnUnitCircle_conj-inv`**: for `v` on the unit circle, `conj v * v = 1`.
- **`OnUnitCircle-rotate`**: for `v` on the unit circle, `u = (u * conj v) * v`.
