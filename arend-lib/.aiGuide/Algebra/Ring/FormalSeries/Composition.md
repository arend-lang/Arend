### Algebra.Ring.FormalSeries.Composition

Composition (substitution) of formal power series, its monoid and group of substitutions, and the compositional inverse.

`fcompose a b` is the series `a(b(x))`, with `k`-th coefficient `sum_{n<=k} a_n * (b^n)_k`; it is well defined as a formal substitution when `b` has zero constant term. The module proves that composition is multiplicative in the outer series and associative, and that the series `subst-ide` (the variable `x`) is a two-sided identity. Substitutions with zero constant term form the monoid `FormalSubstMonoid`; those with invertible linear coefficient form the group `FormalLineDiffeomorphismGroup`. For such `f`, a right compositional inverse `subst-rinv` is constructed coefficient by coefficient through a prefix accumulator `g-prefix`, and is shown to satisfy `fcompose f (subst-rinv f finv1) = subst-ide`.

#### Composition and its laws

- **`fcompose`**: the composition `a(b(x))` of formal power series.
- **`compose-coef-extend`**: if `c 0 = 0`, the coefficient of `fcompose f c` at `j <= k` equals the sum of the first `k+1` terms `f m * pow c m j`.
- **`compose-coef-*`**: composition with `c` (`c 0 = 0`) is multiplicative in a commutative ring: `fcompose (a * b) c = fcompose a c * fcompose b c`.
- **`pow-conv-compose`**: `pow (fcompose b c) n` and `fcompose (pow b n) c` agree coefficientwise when `c 0 = 0`.
- **`compose-coef-assoc`**: associativity of composition for `b 0 = 0` and `c 0 = 0`.
- **`compose-coef_1`**: the coefficient at index 1 of `fcompose s t` is `s 1 * t 1`.

#### Identity substitution

- **`subst-ide`**: the series `x = [0, 1, 0, 0, ...]`.
- **`pow-subst-ide-diag`**: the `k`-th coefficient of the `k`-th power of `subst-ide` is `1`.
- **`pow-subst-ide-off`**: the `k`-th coefficient of the `n`-th power of `subst-ide` is `0` when `n /= k`.
- **`compose-coef-subst-ide-right`**: `fcompose f subst-ide` has the coefficients of `f`.
- **`compose-coef-subst-ide-left`**: `fcompose subst-ide f` has the coefficients of `f` when `f 0 = 0`.

#### Substitution monoid

- **`FormalSubst`**: a formal series with zero constant term.
- **`subst-compose`**: composition of two `FormalSubst`s.
- **`FormalSubstMonoid`**: the monoid of `FormalSubst R` under composition with identity `subst-ide`.

#### Compositional right inverse

- **`new-coef`**: the `n`-th coefficient of the inverse of `f` computed from the previous coefficients `prev`, given an inverse `finv1` of `f 1`.
- **`g-prefix`**: accumulator series whose first `n` coefficients agree with the inverse being built.
- **`subst-rinv`**: the right inverse of `f` under composition, when `f 1` is invertible.
- **`g-prefix-stable`**: extending the stage `n` of `g-prefix` does not change coefficients below `n`.
- **`subst-rinv-eq-prefix`**: `subst-rinv` agrees with `g-prefix` at stage `n` below index `n`.
- **`g-prefix_0`**: `g-prefix` vanishes at index 0.
- **`g-prefix-top-eq`**: the top new coefficient of `g-prefix (suc K)` is `new-coef` applied to the previous stage.
- **`g-prefix-vanish-top`**: `g-prefix f finv1 n k = 0` when `n <= k`.
- **`subst-rinv-eq-prefix-up-to`**: `subst-rinv` agrees with `g-prefix (suc K)` at all indices `k <= K`.
- **`pow-ext`**: the coefficients of `pow g m` up to `K` depend only on the coefficients of `g` up to `K`.
- **`pow-zero-at-zero`**: if `g 0 = 0`, then `pow g (suc m') 0 = 0`.
- **`pow-tail-eq`**: two series vanishing at 0 and agreeing up to `K` have equal powers `m+2` at index `suc K`.
- **`subst-rinv-correct`**: if `f 0 = 0` and `f 1` is invertible, then `fcompose f (subst-rinv f finv1) = subst-ide`.

#### Group of formal line diffeomorphisms

- **`FormalLineDiffeomorphism`**: a `FormalSubst` whose linear coefficient `f 1` is invertible.
- **`subst-ide-f1-inv`**: the linear coefficient of `subst-ide` is invertible.
- **`subst-compose-inv`**: composition of two `FormalLineDiffeomorphism`s over a commutative ring, again with invertible linear coefficient.
- **`subst-rinv-inv`**: the inverse of a `FormalLineDiffeomorphism`, built from `subst-rinv`.
- **`FormalLineDiffeomorphismGroup`**: the group of formal line diffeomorphisms under composition.
