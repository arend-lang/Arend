### Algebra.Ring.Poly.LinearFactors

Products of monic linear factors `(X - r_0) ... (X - r_{n-1})` over a commutative ring.

`prodLin r` is the polynomial with prescribed roots given by a finite array `r`, and `linProd r z` is its value at `z`. The module shows that it is monic of degree `n`, vanishes at every `r i`, can have any single linear factor pulled out, and is invariant under injective reindexing of the roots. A deflation identity supports greedy comparison of two such products.

#### Definition and basic properties

- **`prodLin`**: the product $\prod_{i<n}(X - r_i)$ for `r : Array R n`.
- **`prodLin-degree`**: `prodLin r` has degree below `suc n`.
- **`prodLin-monic`**: the coefficient of `prodLin r` in position `n` is `1`.

#### Evaluation

- **`linProd`**: the product $\prod_{i<n}(z - r_i)$ as a ring element.
- **`polyEval_prodLin`**: evaluating `prodLin r` at `z` gives `linProd r z`.
- **`linProd-root`**: `linProd r (r i) = 0` for every index `i`.
- **`prodLin-root`**: every `r i` is a root of `prodLin r`.

#### Factorization and reordering

- **`prodLin-skip`**: pulling out an arbitrary factor, `prodLin r = (X - r k) * prodLin (skip r k)`.
- **`prodLin-deflate-id`**: for arrays `r s` of length `suc m`, an identity relating `prodLin (skip r 0)`, `prodLin (skip s j)`, `prodLin r` and `prodLin s`, used to remove `r 0` and `s j` simultaneously.
- **`prodLin-perm`**: `prodLin` is unchanged by reindexing the roots along an injective map `Fin n -> Fin n`.
