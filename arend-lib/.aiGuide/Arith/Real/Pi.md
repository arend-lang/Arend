### Arith.Real.Pi

The constant π, defined by Machin's formula, together with cos π = -1 and the periodicity of sine and cosine.

`pi` is defined as 16 arctan(1/5) - 4 arctan(1/239), Machin's formula for π, using `arctan` from `Arith.Trig.ArcTan`. The module proves that tan(pi/4) = 1 for the corresponding quarter angle (internal to `cos_pi`), hence cos(π/2) = 0, cos π = -1 and sin π = 0, then cos(2π) = 1, sin(2π) = 0 and 2π-periodicity of `cos` and `sin`.

#### Definition

- **`pi`**: π defined by Machin's formula `16 * arctan (1/5) - 4 * arctan (1/239)`. The `\where` block has `ratio-norm-<1`, the proof that `|1/(m+2)| < 1`, the domain witness for those arctangents.

#### Values of cos and sin

- **`cos_pi`**: `cos pi = -1`. Its `\where` block contains `pi/4`, `tan_pi/4` (`sin (pi/4) = cos (pi/4)`), `pi=4*pi/4` and `cos_pi/2` (`cos (2 * pi/4) = 0`).
- **`sin_pi`**: `sin pi = 0`.
- **`cos_two_pi`**: `cos (2 * pi) = 1`.
- **`sin_two_pi`**: `sin (2 * pi) = 0`.

#### Periodicity

- **`cos_period`**: `cos (x + 2 * pi) = cos x`.
- **`sin_period`**: `sin (x + 2 * pi) = sin x`.
