### Arith.Trig.Complex

Complex sine and cosine defined from the complex exponential, with Euler's formula, the Pythagorean identity and the addition and sum-to-product formulas.

`cos z` and `sin z` are defined by the usual exponential formulas via `exp`, as cos z = (e^{iz} + e^{-iz})/2 and sin z = (i/2)(e^{-iz} - e^{iz}). The module proves values at zero, parity, the addition formulas, Euler's formula `euler`, the Pythagorean identity `pythagoras`, the difference formulas and sum-to-product formulas, and that the complex functions extend the real ones (`cos-fromReal`, `sin-fromReal`).

#### Definitions

- **`cos`**: Complex cosine `cos z = (exp (i * z) + exp (i * negative z)) * 1/2`.
- **`sin`**: Complex sine `sin z = i * (exp (i * negative z) - exp (i * z)) * 1/2`.

#### Basic values and parity

- **`cos_zro`** / **`sin_zro`**: `cos 0 = 1` and `sin 0 = 0`.
- **`cos_negative`** / **`sin_negative`**: Cosine is even (`cos (negative z) = cos z`) and sine is odd (`sin (negative z) = negative (sin z)`).

#### Addition formulas and identities

- **`cos_+`** / **`sin_+`**: Addition formulas `cos (x + y) = cos x * cos y - sin x * sin y` and `sin (x + y) = cos x * sin y + sin x * cos y`.
- **`cos_minus`** / **`sin_minus`**: Subtraction formulas `cos (x - y) = cos x * cos y + sin x * sin y` and `sin (x - y) = sin x * cos y - cos x * sin y`.
- **`euler`**: Euler's formula `exp (i * z) = cos z + i * sin z`.
- **`pythagoras`**: Pythagorean identity `cos z * cos z + sin z * sin z = 1`.
- **`cos-sum-to-product`** / **`sin-sum-to-product`**: `cos x - cos y = -2 sin((x+y)/2) sin((x-y)/2)` and `sin x - sin y = 2 cos((x+y)/2) sin((x-y)/2)`.

#### Agreement with the real functions

- **`cos-fromReal`** / **`sin-fromReal`**: The complex `cos` and `sin` on a real argument embedded in the complex numbers equal the real `cos` and `sin` of `Arith.Trig.Real`, embedded.
