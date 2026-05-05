### Arith.Complex

This module defines the complex numbers over the reals and provides a field instance.

#### Complex Class

- **`Complex`**: A class with two fields `re : Real` and `im : Real`, representing the real and imaginary parts of a complex number.

#### ComplexField Instance

- **`ComplexField`**: Instance of `Field` for `Complex`, with:
  - `zro` = `(0, 0)`, `negative (a, b)` = `(-a, -b)`, addition componentwise.
  - Multiplication: `(a, b) * (c, d) = (a*c - b*d, a*d + b*c)`.
  - `zro/=ide`: `0 ≠ 1` via the real part.
  - `locality`: Derived from locality of the real part.
  - `#0-tight`: Tightness of apartness from zero.
  - **`ComplexMonoid`**: Instance of `CMonoid` for `Complex` with `ide = (1, 0)` and standard complex multiplication. Provides `ide-left`, `*-assoc`, `*-comm`.
  - **`inv-char`**: `Inv x <-> Inv x.re || Inv x.im` — a complex number is invertible iff its real or imaginary part is invertible.
