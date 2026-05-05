### Algebra.Linear.VectorSpace

Vector spaces over discrete fields, matrix rank theory over Smith domains, and finiteness of images and kernels of linear maps.

#### Vector Space Classes

- **`VectorSpace`**: Extends `LModule` with the scalar ring overridden to a `DiscreteField`.
- **`FinVectorSpace`**: Finitely generated vector space, extending `VectorSpace` and `FinModule`.

#### Matrix Rank

- **`rank`**: Rank of a matrix `A : Matrix R n m` over a Smith domain, defined as the position of the first zero divisibility-class in the Smith normal form's diagonal.
- **`rank.firstNonZero`**: Index of the first element divisible by zero (i.e. `in~ 0`) in an array of `DivQuotient R`; returns the array length if none exists.
- **`rank.firstNonZero_<=`**: Characterizes `firstNonZero l <= k` as equivalent to `l k = in~ 0`, given monotonicity of zeros.
- **`rank.firstNonZero<=len`**: `firstNonZero l` is bounded by the array length.
- **`rank.rank-array`**: Sigma packaging the canonical diagonal divisibility-quotient array of length `n ∧ m` extracted from any Smith form of `A`; uniqueness relies on `smith-unique`.
- **`rank.rank-array_M~`**: The rank-array is invariant under matrix equivalence `M~`.
- **`rank.rank_M~`**: Rank is invariant under matrix equivalence.
- **`rank.rank-array_smith`**: For a matrix already in Smith form, the rank-array equals the diagonal of the matrix lifted to `DivQuotient`.
- **`rank.rank_smith`**: Rank of a Smith-form matrix equals `firstNonZero` applied to its diagonal classes.
- **`rank.rank_transpose`**: Rank is preserved by transposition.
- **`rank.rank_<=`**: `rank A <= k` iff the `k`-th entry of the rank-array is `in~ 0` (for `k < n`, `k < m`).
- **`rank.rank_smith_<=`**: For Smith-form `A`: `rank A <= k` iff `A k k = 0`.

#### Rank Bounds

- **`rank<=rows`**: `rank A <= n`.
- **`rank<=columns`**: `rank A <= m`.

#### Smith Normal Form for Linear Maps

- **`smith-bases`**: Given bases `lu` of `U` and `lv` of `V` and a linear map `f : U → V`, produces new bases `lu'`, `lv'` (of the same lengths) and a diagonal coefficient array `d` such that `f (lu' j) = d j *c lv' j` (padded with zero), and `d j = 0` iff `rank (toMatrix lu' bv' f) <= j`. This is the Smith normal form theorem at the level of linear maps.
- **`smith-bases.M~_toLinearMap`**: For matrix-equivalent `A M~ B`, there exist new bases under which `toLinearMap bu lv A = toLinearMap bu' lv' B`; transports `M~` from matrices to linear maps.

#### Image and Kernel Finiteness

- **`image-fin`**: Instance making the image `ImageLModule f` of a linear map between finite modules over a Smith domain a `FinModule`.
- **`image-fin.basis`**: Constructs an explicit basis for the image whose length equals `rank (toMatrix lu' bv' f)`.
- **`image-fin.dimension_rank`**: The dimension of the image of `f` equals the rank of any matrix representation of `f`.
- **`kernel-fin`**: Instance making the kernel `KerLModule f` of a linear map between finite modules over a Smith domain a `FinModule`; uses `smith-bases` to extract a basis indexed by the zeros of the diagonal `d`.
- **`kernel-fin.filter0`**: Filters an array of ring elements to the sublist of indices where the entry is zero, paired with proofs of the equality.
- **`kernel-fin.filter0_BigSum`**: A `BigSum` over `f` equals the `BigSum` over `f` restricted to the zero indices, provided `f` vanishes on nonzero indices.

#### Linear Dependence Decision

- **`dependency-dec`**: Decides whether an array `l : Array U` in a finite module over a Smith domain is dependent or independent, by computing the dimension of the kernel of the corresponding `arrayLinearMap`.
