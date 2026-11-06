### Arith.Trig.Real

Real sine and cosine defined as the real and imaginary parts of exp(ix), with addition formulas, parity, Pythagorean identity and sum-to-product formulas.

`cos x` is defined as the real part and `sin x` as the imaginary part of `exp (i * x)` for a real `x` embedded in the complex numbers. The module proves values at zero, the addition formulas, continuity (`cosMap`, `sinMap`), parity, and the Pythagorean identity `pythagoras`, which follows from `conj_exp` (the conjugate of exp z is exp of the conjugate) and the unit-modulus identity `exp-conj-cancel`. It also gives subtraction formulas, small ring identities, and the sum-to-product formulas for cosine and sine.

#### Definitions

- **`cos`**: Real cosine, `Re (exp (i * x))`.
- **`sin`**: Real sine, `Im (exp (i * x))`.

#### Values and addition formulas

- **`cos_zro`** / **`sin_zro`**: `cos 0 = 1` and `sin 0 = 0`.
- **`cos_+`** / **`sin_+`**: Addition formulas `cos (x + y) = cos x * cos y - sin x * sin y` and `sin (x + y) = cos x * sin y + sin x * cos y`.
- **`cos_minus`** / **`sin_minus`**: Subtraction formulas `cos (x - y) = cos x * cos y + sin x * sin y` and `sin (x - y) = sin x * cos y - cos x * sin y`.

#### Continuity

- **`cosMap`** / **`sinMap`**: `cos` and `sin` are cover maps `CoverMap RealNormed RealNormed`.

#### Conjugation, parity and the Pythagorean identity

- **`conj_exp`**: Complex conjugation commutes with the exponential: `conj (exp z) = exp (conj z)`.
- **`cos_negative`** / **`sin_negative`**: Cosine is even and sine is odd.
- **`exp-conj-cancel`**: The unit-modulus identity `exp (i * x) * conj (exp (i * x)) = 1` for real `x`.
- **`pythagoras`**: Pythagorean identity `cos x * cos x + sin x * sin x = 1`.

#### Sum-to-product formulas

- **`cos-diff-ring`** / **`sin-diff-ring`**: Ring identities, in an arbitrary commutative ring, behind the sum-to-product formulas, stated abstractly so that they are fast to check.
- **`half-plus-ring`** / **`half-minus-ring`**: In a commutative ring with `h + h = 1`, `(x + y) * h + (x - y) * h = x` and `(x + y) * h - (x - y) * h = y`.
- **`cos-sum-to-product`**: `cos x - cos y = -2 sin((x+y)/2) sin((x-y)/2)`.
- **`sin-sum-to-product`**: `sin x - sin y = 2 cos((x+y)/2) sin((x-y)/2)`.
