### Arith.Exp.Real

Real-specific facts about the exponential function: it is positive, strictly increasing and injective on the reals.

General Banach-algebra `exp` is not injective (exp(2πi) = 1 in the complex numbers), so the real exponential needs its own argument, used to prove `ell-add` in `Arith.Log.Real`. The module first gives limit and series-sum comparison lemmas for real series, then derives lower bounds `1 <= exp x` for `x >= 0` and `1 < exp x` for `x > 0`, positivity of `exp` everywhere, strict monotonicity from the exponent law, and injectivity.

#### Limits and series comparison

- **`Real-limit->=`**: A real limit is bounded below by any lower bound on a tail of the sequence.
- **`Real-limit-<=`**: A real limit is bounded above by any upper bound on a tail of the sequence.
- **`partialSum_<=_seriesSum`**: Partial sums of a series of non-negative reals are at most the series sum.
- **`seriesSum_norm_<=`**: Majorant principle in a normed abelian group: if all partial sums of the norms of `S` are bounded by `B` and `S` sums to `l`, then `norm l <= B`.

#### Positivity and monotonicity of exp

- **`Real-exp->=1-of-nonneg`**: For `0 <= x`, `1 <= exp x`.
- **`Real-exp->1-of-pos`**: For `0 < x`, `1 < exp x` (via the partial sum `1 + x`).
- **`Real-exp-pos`**: `exp x` is strictly positive for every real `x`.
- **`Real-exp-strict-mono`**: `exp` is strictly increasing on the reals: `x < y` implies `exp x < exp y`.
- **`Real-exp-inj`**: `exp` is injective on the reals.
