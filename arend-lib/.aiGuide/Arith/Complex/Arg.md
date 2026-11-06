### Arith.Complex.Arg

Arguments of unit complex numbers and the polar decomposition of nonzero complex numbers, without a total inverse trigonometric function.

The main results are `unit-arg`, saying every point of the unit circle is $e^{i\theta}$ for some real $\theta$, and the polar form `complex-arg`, saying a nonzero `z` equals $|z| e^{i\theta}$; the latter is used for roots in `Arith.Complex.Root`. Surjectivity of $\theta \mapsto e^{i\theta}$ is obtained from a chart on the open right half of the circle, given by the stereographic parameter `stereo` and `arctan`, and four rotated copies of this chart that cover the circle. Only a truncated existence statement is proved: there is no total argument function (an `atan2`), since choosing a chart needs a decision available only inside a proposition.

#### Chart on the right half circle

- **`stereo`**: the half-angle tangent $\Im u / (1 + \Re u)$ of a complex `u` with positive real part.
- **`stereo<1`**: for `u` on the unit circle with positive real part, `abs (stereo u re>0) < 1`, the domain of `arctan`.
- **`arg-right`**: the argument $2\arctan(\mathrm{stereo}\ u)$ of a unit `u` with positive real part; a real-valued function on this chart only.
- **`exp^i*_arg-right`**: `exp^i* (arg-right unit re>0) = u`.

#### Arguments

- **`unit-arg`**: every `u` on the unit circle has an argument, `∃ theta, exp^i* theta = u`.
- **`complex-arg`**: polar decomposition: for `0 < cabs z`, there is `theta` with `z = fromReal (cabs z) * exp^i* theta`.
