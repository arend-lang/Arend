### Arith.Algebraic

The algebraic numbers form a discrete subfield of the complex numbers.

The module takes the integral closure of the rationals in `ComplexField` (Theorem VI.1.9 of Mines-Richman-Ruitenburg, instantiated at Q ⊆ C). It first builds the ring homomorphism `ratToComplex` and shows that the complex numbers have characteristic zero. It then packages the numbers integral over Q as a subring `AlgNumbersSubring`, a ring `AlgNumbersRing`, and a field `AlgNumbersField`, and derives decidable equality of algebraic numbers (`algebraic-dec`). Everything is axiom-free, using the general theory of integral and zero-dimensional extensions already in the library.

#### The embedding of the rationals

- **`ratToComplex`**: The ring homomorphism `RatField → ComplexField` sending `q` to `Complex.fromReal (Real.fromRat q)`.

#### Characteristic zero

- **`complex-char0`**: The complex numbers have characteristic zero: `natCoef k /= 0` in `ComplexField` for every `k > 0`.
- **`subfield-char0`**: Any ring `R` with a ring homomorphism `RingHom R ComplexField` has characteristic zero (`natCoef k /= 0` for `k > 0`); injectivity of the homomorphism is not needed.

#### Algebraic numbers

- **`IsAlgNumber`**: A complex number `z` is algebraic if it is integral over the rationals, i.e. `isIntegral ratToComplex z` (a proposition).
- **`AlgNumbersSubring`**: The algebraic numbers as a `SubRing ComplexField`, the integral closure of `Q` in `C` along `ratToComplex`.
- **`AlgNumbersRing`**: The algebraic numbers as a commutative ring in their own right.
- **`algNumbersToComplex`**: The inclusion ring homomorphism `AlgNumbersRing → ComplexField`; `algNumbersToComplex.isInj` shows it is injective.

#### Discreteness

- **`AlgNumbersImpotent`**: The ring of algebraic numbers is an `ImpotentCRing`: no nilpotents other than `0` and no idempotents other than `0` and `1`.
- **`AlgNumbersField`**: The algebraic numbers form a `DiscreteField`, obtained because the integral extension of Q is zero-dimensional and impotent.
- **`algebraic-dec`**: Equality of two algebraic complex numbers `a`, `b` (given `IsAlgNumber a` and `IsAlgNumber b`) is decidable: `Dec (a = b)`.
