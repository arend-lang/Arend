### Arith.Complex.Euler

The circle map $t \mapsto e^{it}$ on the reals, Euler's formula, and its basic algebraic laws.

The module defines `exp^i*` as the complex exponential of $i t$ and proves Euler's formula `euler` (cosine and sine as the real and imaginary parts), the homomorphism property $e^{i(a+b)} = e^{ia} e^{ib}$, de Moivre's formula `pow_exp^i*` and Euler's identity $e^{i\pi} = -1$. It rests on the Banach algebra structure of `Complex` from `Arith.Complex.Banach`.

#### Decomposition

- **`complex-decomp`**: every complex `z` equals `fromReal z.re + i * z.im`, where `i` is the imaginary unit.

#### The circle map

- **`exp^i*`**: `exp^i* t` is `exp (i * t)`, the complex exponential of $it$ for real `t`.
- **`euler`**: Euler's formula, `exp^i* x = fromReal (cos x) + i * sin x`.

#### Algebraic laws

- **`exp^i*_zro`**: `exp^i* 0 = 1`.
- **`exp^i*_+`**: `exp^i* (a + b) = exp^i* a * exp^i* b`.
- **`pow_exp^i*`**: de Moivre's formula, `pow (exp^i* a) k = exp^i* (k * a)` for a natural number `k`.
- **`exp^i*_pi`**: Euler's identity, `exp^i* pi = -1`.
