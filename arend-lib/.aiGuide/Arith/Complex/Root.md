### Arith.Complex.Root

Existence of $k$-th roots of nonzero complex numbers.

The module proves that every complex number of positive modulus has a $k$-th root for every $k \geq 1$, constructively. The root is obtained from the polar form `complex-arg` (in `Arith.Complex.Arg`): the real $k$-th root of the modulus times $e^{i\theta/k}$. The root is only asserted to exist (a truncated `∃`), not given as a function.

#### Roots

- **`complex-root`**: if `0 < cabs z` and `k > 0`, then there exists `w : Complex` with `Monoid.pow w k = z`.
