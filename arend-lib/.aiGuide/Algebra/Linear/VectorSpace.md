### Algebra.Linear.VectorSpace

Vector spaces over discrete fields, the rank of a matrix over a Smith domain, and applications to images, kernels, and linear (in)dependence.

This module specializes `LModule` to the case where the scalar ring is a `DiscreteField`, yielding `VectorSpace` and its finite-dimensional refinement `FinVectorSpace`. The bulk of the file develops a coordinate-free notion of matrix `rank` over a `SmithDomain` by reducing to Smith normal form and counting nonzero diagonal entries up to associates (i.e., in `DivQuotient`); uniqueness of this count follows from uniqueness of Smith form. These rank invariants then feed into structural results about linear maps between finite modules: bases can be simultaneously adjusted so the map becomes diagonal, the image and kernel are themselves finite modules whose dimensions are governed by the rank, and linear dependence becomes decidable.

#### Vector Space Classes

- **`VectorSpace`**: Extends `LModule` with the constraint that the scalar ring `R` is a `DiscreteField`.
- **`FinVectorSpace`**: Extends both `VectorSpace` and `FinModule`; finite-dimensional vector spaces over a discrete field.

#### Matrix Rank

- **`rank`**: For a matrix `A : Matrix R n m` over a `SmithDomain`, the rank is defined as the index of the first zero entry in the Smith-normal diagonal (taken in `DivQuotient R`).
- **`rank.firstNonZero`**: Helper returning the first index in an array of `DivQuotient` values whose entry is zero (or the length if none is zero).
- **`rank.firstNonZero_<=`**: Characterizes `firstNonZero l <= k` as `l k = in~ 0`, given monotonicity of zero-ness along the array.
- **`rank.firstNonZero<=len`**: `firstNonZero l <= l.len`.
- **`rank.rank-array`**: Constructs the canonical diagonal of associate classes, packaged with a witness that some Smith-form matrix `B` equivalent to `A` realizes them; the type is propositional via Smith uniqueness.
- **`rank.rank-array_M~`**: The diagonal `rank-array` is invariant under matrix equivalence `M~`.
- **`rank.rank_M~`**: `rank` itself is invariant under matrix equivalence.
- **`rank.rank-array_smith`**: When `A` is already in Smith form, `rank-array A` is the diagonal of `A` mapped into `DivQuotient`.
- **`rank.rank_smith`**: Computes `rank` directly from the diagonal when `A` is in Smith form.
- **`rank.rank_transpose`**: `rank (transpose A) = rank A`.
- **`rank.rank_<=`**: Characterizes `rank A <= k` via vanishing of the `k`-th entry of `rank-array A`.
- **`rank.rank_smith_<=`**: Specialization of the above when `A` is in Smith form: `rank A <= k` iff `A k k = 0`.

#### Rank Bounds

- **`rank<=rows`**: `rank A <= n`, the number of rows.
- **`rank<=columns`**: `rank A <= m`, the number of columns.

#### Simultaneous Basis Adjustment

- **`smith-bases`**: Given bases `lu` of `U` and `lv` of `V` and a linear map `f : U -> V`, produces new bases `lu'`, `lv'` and scalars `d : Array R` such that `f (lu' j) = d j *c lv' j` (diagonalization of `f`), with `d j = 0` characterized exactly by `rank (toMatrix lu' bv' f) <= j`.
- **`smith-bases.M~_toLinearMap`**: Equivalent matrices `A M~ B` give rise to the same linear map relative to suitably transformed bases.

#### Image and Kernel as Finite Modules

- **`image-fin`**: For `f : LinearMap U V` between finite modules over a Smith domain, the image `ImageLModule f` is itself a `FinModule` instance.
- **`image-fin.basis`**: Constructs an explicit basis of the image of length `rank (toMatrix lu' bv' f)`.
- **`image-fin.dimension_rank`**: `FinModule.dimension (image-fin f) = rank (toMatrix lu bv f)`; the dimension of the image equals the rank of the matrix of `f`.
- **`kernel-fin`**: The kernel `KerLModule f` of a linear map between finite modules over a Smith domain is a `FinModule`.
- **`kernel-fin.filter0`**: Selects the indices at which an array of decidable-zero ring elements is zero, packaged with proofs.
- **`kernel-fin.filter0_BigSum`**: A big sum collapses to the sum over indices where the coefficient is zero, when the summand vanishes off those indices.

#### Decidability of Linear Dependence

- **`dependency-dec`**: For an array `l` in a finite module over a Smith domain, decides whether `l` is linearly dependent or independent, by inspecting the dimension of the kernel of the linear map sending coordinates to linear combinations of `l`.
