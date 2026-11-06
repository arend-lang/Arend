### Arith.Complex

Complex numbers built on top of the real numbers as pairs of reals, equipped with their field structure.

A `Complex` number is a record of two `Real` components (real and imaginary parts), and the module assembles the standard field operations on `Complex` from the underlying real arithmetic. Addition is pointwise, multiplication uses the usual `(re*re - im*im, re*im + im*re)` formula, and the field instance is built up via an intermediate commutative monoid for multiplication. Invertibility is characterized in terms of invertibility of the components, reflecting that a complex number is nonzero (and hence invertible in the field) precisely when at least one of its real or imaginary parts is.

#### Core Type

- **`Complex`**: Record of a complex number with fields `re : Real` and `im : Real`.

#### Field Structure

- **`ComplexField`**: `Field Complex` instance. Provides zero `(0,0)`, pointwise addition, componentwise negation, multiplicative structure inherited from `ComplexMonoid`, distributivity, the field axioms `zro/=ide`, locality, and `#0`-tightness.

#### Multiplicative Structure

- **`ComplexMonoid`**: `CMonoid Complex` instance defining the multiplicative monoid. Identity is `(1,0)` and product is `(x.re*y.re - x.im*y.im, x.re*y.im + x.im*y.re)`.

#### Invertibility

- **`inv-char`**: Characterization of invertibility: `Inv x <-> Inv x.re || Inv x.im`, i.e. a complex number is invertible iff its real or imaginary part is invertible as a real number.

#### Embedding of the Reals

- **`fromRealRingHom`**: The embedding `Complex.fromReal` of the reals into the complex numbers as a ring homomorphism `RealField -> ComplexField`.

#### Conjugation

- **`conj`**: Complex conjugation `conj (a + bi) = a - bi`, keeping `re` and negating `im`.
- **`conj_conj`**: `conj` is an involution: `conj (conj z) = z`.
- **`conj_zro`**, **`conj_ide`**: `conj` fixes `0` and `1`.
- **`conj_+`**, **`conj_*`**: `conj` preserves addition and multiplication: `conj (x + y) = conj x + conj y` and `conj (x * y) = conj x * conj y`.
- **`conj_i`**: `conj i = negative i`.
- **`conj_i_*`**: `conj (i * x) = negative (i * x)` for a real `x`.
- **`conj_fromReal`**: `conj` fixes real numbers embedded via `Complex.fromReal`: `conj x = Complex.fromReal x`.
- **`conj_pow`**: `conj` commutes with powers: `conj (z ^ n) = (conj z) ^ n` (via `Monoid.pow`).
- **`conjAddHom`**: `conj` packaged as an additive group homomorphism `ComplexField -> ComplexField`.

#### Tactics

- **`complex-ext`**: Meta that proves an equality of complex numbers componentwise, closing the `re` and `im` goals with the commutative-ring solver `equation.cRing`.
