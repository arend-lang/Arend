### Arith.Complex.FTA.Approximation

Choice-free approximate roots and approximate factorisations of monic complex polynomials.

Iterating Kneser's lemma (`KneserLemma-poly`) shows that every monic polynomial of positive degree takes arbitrarily small values: `fta-approx` and `fta-approx-eps` give approximate roots, with no choice principle. This is the approximate fundamental theorem of algebra. Deflating by an approximate root repeatedly then yields `approx-factor`: every monic polynomial of degree $n$ is, to any accuracy, a product of $n$ linear factors $X - r_i$ (compared coefficientwise). The sets of such near factorisations, `nearFactorSet`, are the basis of Richman's spectrum in `Arith.Complex.FTA`. Only approximate roots and factorisations are produced here, not an exact root.

#### Near factorisations

- **`nearFactorSet`**: the set of arrays `r` of `n` complex numbers whose monic product `prodLin r` matches `p` coefficientwise to within `delta`.

#### Error propagation

- **`coefErr_*padd1`**: if all coefficients of `e` have modulus at most `d`, then those of `e * (X - z)` have modulus at most `d * (1 + cabs z)`.
- **`constCoef-bound`**: the coefficients of the constant polynomial `c` have modulus at most `cabs c`.

#### Approximate roots

- **`fta-approx`**: for monic `f` of degree `< suc n`, `n > 0`, and `cabs (polyEval f 0) < c`, for every `i` there is `z` with `cabs (polyEval f z) < pow (kneser-q n) i * c`.
- **`fta-approx-eps`**: for monic `f` of degree `< suc n`, `n > 0`, and every `eps > 0`, there is `z` with `cabs (polyEval f z) < eps`.

#### Approximate factorisation

- **`approx-factor`**: for monic `p` of degree `< suc n` and every `eps > 0`, there is an array `r` of `n` complex numbers in `nearFactorSet p eps`, i.e. a near factorisation of `p` into linear factors.
