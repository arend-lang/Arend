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
