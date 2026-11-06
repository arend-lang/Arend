### Analysis.Poly

Norm estimates for the evaluation of polynomials over a normed ring, and the continuity of polynomial evaluation over a complete normed ring.

For a polynomial `q : Poly X` of degree below `m` (`degree< q m`) over a `PseudoNormedRing`, the module bounds `lnorm (polyEval q z)` by the coefficient norms: first by `Σ ‖q_k‖ ‖z‖^k` (`lnorm_polyEval-bound`), then, using the uniform coefficient bound `coefMax`, by a constant times a geometric sum of `lnorm z` (`lnorm_polyEval-boundD`). A similar bound is given for the coefficients of the deflation `deflate p z`. Over a `CompleteExNormedRing` a polynomial's coefficients form an everywhere convergent power series (`polyPowerSeriesConv`), from which evaluation is shown to be a cover map, hence continuous (`polyEvalCover`).

#### Bounding a polynomial's value by its coefficients

- **`lnorm_polyEval-bound`**: If `q` has degree below `m`, then `lnorm (polyEval q z) <= Σ_{k<m} lnorm (polyCoef q k) * (lnorm z)^k`.
- **`lnorm_polyEval-boundD`**: If `q` has degree below `m` and `c` bounds the norms of the first `m` coefficients, then `lnorm (polyEval q z) <= c * powSum (lnorm z) m`.

#### Uniform coefficient bound

- **`coefNorms`**: The array of norms of the first `m` coefficients of `q`.
- **`coefMax`**: The maximum (join, starting from `0`) of `coefNorms q m`, a uniform bound on the first `m` coefficient norms.
- **`coefMax>=0`**: `coefMax q m` is non-negative.
- **`coefMax-cond`**: Each of the first `m` coefficients has `lnorm` at most `coefMax q m`.
- **`coefMax-cond-all`**: If `q` has degree below `m`, then `coefMax q m` bounds the norm of every coefficient of `q`, not only the first `m`.
- **`deflate-coefBound`**: If `p` has degree below `m` and every coefficient has norm at most `c >= 0`, then every coefficient of `deflate p z` has norm at most `c * powSum (lnorm z) m`.

#### Continuity of polynomial evaluation

- **`polyPowerSeriesConv`**: Over a `CompleteExNormedRing`, the coefficients of a polynomial form a power series that converges everywhere (`IsPowerSeriesConv`).
- **`polyEvalCover`**: Over a `CompleteExNormedRing`, `polyEval f` is a `CoverMap X X`, i.e. polynomial evaluation is continuous.
