### Arith.Complex.FTA.Classical

The fundamental theorem of algebra in its classical form, a root of a monic polynomial, proved using countable choice.

`FTA` states that every monic polynomial of degree $m+1$ (so degree at least one) over the complex numbers has a root, as a truncated existence statement. It is obtained from the approximate factorisations of `Arith.Complex.FTA.Approximation` and the matching estimates of `Arith.Complex.FTA.Moduli`: a sequence of near factorisations of accuracy $2^{-k}$ is chosen (`NearSeq`), consecutive ones are aligned by permutations (`Threading`), and the thread of the first entries converges to a root. Extracting a single root from the root multiset costs `acchoice`. The choice-free statement, the whole root multiset as a point of a completion, is in `Arith.Complex.FTA`.

#### Choice sequences

- **`NearSeq`**: record of a choice, for each `k`, of a near factorisation of `p` of accuracy $2^{-k}$ together with a threshold beyond which all such factorisations match to within $2^{-k}$.
- **`Threading`**: a `NearSeq` extended with relabelling permutations (injective maps on `Fin`) aligning consecutive chosen factorisations to within $2^{-k}$.

#### The theorem

- **`FTA`**: the fundamental theorem of algebra: for monic `p` with `degree< p (suc (suc m))` and `polyCoef p (suc m) = 1`, there exists `z : Complex` with `polyEval p z = 0`.
