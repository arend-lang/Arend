### Analysis.CauchyProduct

Cauchy product (convolution) of series, with norm estimates and Mertens-type theorems on the convergence of the product of two absolutely convergent series.

The module defines `conv a b`, the convolution of two series in a ring (multiplication in the formal series ring), and `uconv`, the analogous convolution of non-negative upper-real series used to bound norms. It shows that the convolution of absolutely convergent series is absolutely convergent (`conv-absConv`) and that its sum is the product of the sums (`conv-sum`, `conv-seriesSum`): this is the classical theorem on the Cauchy product of absolutely convergent series (Mertens' theorem in its absolutely convergent form). The proof goes through bounds on partial sums and mid-sums of `uconv`. The last lemma transports the convolution along the embedding of rationals into a real Banach algebra.

#### Convolution

- **`conv`**: The Cauchy product `conv a b n = a 0 * b n + ... + a n * b 0` of two series in a ring `A`.
- **`uconv`**: The Cauchy product of two series in `ExUpperReal`, defined as a finite sum over pairs of indices with total `n`.
- **`conv-norm_<=`**: In an `ExPseudoNormedRing`, `norm (conv a b n) <= uconv (norm ∘ a) (norm ∘ b) n`.
- **`conv_fromRat`**: In a `RealBanachAlgebra`, `fromRat` commutes with `conv` on series of rationals.

#### Bounds for non-negative upper-real series

- **`partialSum-uconv-square`**: For non-negative series `a`, `b`, `partialSum (uconv a b) n <= partialSum a n * partialSum b n`.
- **`midSum-uconv-bound`**: For non-negative bounded series and `2 * M <= N`, bounds `midSum (uconv a b) N n` by `midSum a M n * partialSum b n + partialSum a n * midSum b M n`.
- **`partialSum-from-termBound`**: If every term of a series is bounded above by a rational `B`, then each partial sum has some rational upper bound.
- **`partialSum-uniform-bound`**: A non-negative bounded convergent upper-real series (`IsBoundedUpperSeries`, `IsConvUpperSeries`) has a rational bound `B` valid for all its partial sums.
- **`uconv-conv`**: The convolution of two non-negative bounded convergent upper-real series is convergent.

#### Convergence of the product

- **`conv-absConv`**: In an `ExPseudoNormedRing`, the convolution of two absolutely convergent series is absolutely convergent.
- **`conv-sum`**: If `a` and `b` are absolutely convergent and sum to `sa` and `sb`, then `conv a b` sums to `sa * sb`.
- **`conv-seriesSum`**: In a `CompleteExNormedRing`, the form of `conv-sum` stated with `seriesSum`: `conv a b` sums to the product of `seriesSum a` and `seriesSum b`.
