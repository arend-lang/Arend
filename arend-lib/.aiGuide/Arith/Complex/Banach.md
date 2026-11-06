### Arith.Complex.Banach

The complex numbers as a complete normed ring and as a commutative real Banach algebra.

The module shows that `Complex` with the modulus `cabs` is complete (a Cauchy filter converges, componentwise via the real and imaginary parts), hence a complete extended-normed ring `ComplexCompleteRing`, and finally a real Banach algebra `ComplexBanach`, which also needs divisibility by naturals and the norm-doubling property. Supporting facts relate the modulus to the real and imaginary parts and describe how rationals embed into the complex numbers.

#### Norm comparison and doubling

- **`cabs_<=_re_+_im`**: $|c| \leq |\Re c| + |\Im c|$; together with the upper bounds of `Arith.Complex.Norm` it shows the modulus equivalent to the sum of component norms.
- **`cabs_+-double`**: `cabs c + cabs c = cabs (c + c)`, i.e. $|c + c| = 2|c|$.

#### Completeness and Banach algebra

- **`ComplexComplete`**: `Complex` with `ComplexNormed` is a complete extended-normed abelian group; the limit of a Cauchy filter is built from the limits of its real and imaginary parts.
- **`ComplexCompleteRing`**: `Complex` as a complete extended-normed ring, from `ComplexValuedRing` and `ComplexComplete`.
- **`ComplexBanach`**: `Complex` as a commutative real Banach algebra (`RealBanachCAlgebra`) built on `ComplexCompleteRing`, with divisibility by nonzero naturals and norm doubling. Its `\where` block has the divisibility helpers `real-div-cancel` and `complex-div-cancel`.

#### Rationals

- **`conj_fromRat`**: complex conjugation fixes the image of a rational `r` under `ComplexBanach.fromRat`.
- **`complexBanach-fromRat`**: `ComplexBanach.fromRat r = Complex.fromReal (Real.fromRat r)`, so the algebra's rational embedding agrees with the real one.
