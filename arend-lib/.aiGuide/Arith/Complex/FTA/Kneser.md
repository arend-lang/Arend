### Arith.Complex.FTA.Kneser

Kneser's lemma: a monic polynomial over the complex numbers can have its value at a point decreased by a fixed factor within a controlled radius.

The module follows the constructive proof of the fundamental theorem of algebra by Geuvers, Wiedijk and Zwanenburg, which needs no rationals. `KneserLemma` states that for a monic polynomial of degree $n > 0$ with $|f(0)| < c$, there is $z$ with $|z|^n < c$ and $|f(z)| < q c$, where $q$ is the explicit factor `kneser-q n` strictly between $0$ and $1$. The real-analytic core is `MainLemma`, which picks a radius and an index from a sequence of nonnegative coefficient moduli. `KneserLemma-poly` is the statement for `Poly ComplexField`. Iterating the lemma gives the approximate roots in `Arith.Complex.FTA.Approximation`.

#### The Kneser factor

- **`kneser-q`**: the explicit factor $q = 1 - 3^{-(2n^2 + n)}$.
- **`kneser-q>0`**: `kneser-q n` is positive when `n > 0`.
- **`kneser-q<1`**: `kneser-q n` is less than `1`.

#### The lemma

- **`MainLemma`**: the real-valued main lemma. For `n > 0`, `eps > 0`, nonnegative reals `a i` with `a 0 > eps` and `a n = 1`, there are a radius `r > 0` and an index `k < n` with `r^n < a 0` and further estimates relating `a (suc k) * r^(suc k)` to `a 0` and `eps`.
- **`KneserLemma`**: Kneser's lemma for a coefficient sequence `b` of complex numbers with `b n = 1` and `cabs (b 0) < c`: there is a complex `z` with `pow (cabs z) n < c` and `cabs` of the sum of `b i * z^i` for `i <= n` below `kneser-q n * c`.
- **`KneserLemma-poly`**: the same for a monic polynomial `f` of degree `< suc n` over `ComplexField`: if `cabs (polyEval f 0) < c`, some `z` has `pow (cabs z) n < c` and `cabs (polyEval f z) < kneser-q n * c`.
