### Algebra.Linear.Matrix

Matrices over rings: construction, block operations, the matrix ring/algebra structure, determinants, adjugates, and triangular/diagonal classifications.

#### Core Type and Constructors

- **`Matrix`**: Type of `n × m` matrices over `R`, defined as `Array (Array R m) n`.
- **`mkMatrix`**: Build a matrix from a function `Fin n -> Fin m -> R`.
- **`makeMatrix`**: Coerce a nested array into a `Matrix`.
- **`mkColumn`**: Turn an array into a column matrix (`l.len × 1`).
- **`mkRow`**: Turn an array into a row matrix (`1 × l.len`).
- **`addColumn`**: Prepend a column vector to a matrix.
- **`addRow`**: Prepend a row vector to a matrix.
- **`matrix-map`**: Apply `f : A -> B` entrywise.
- **`transpose`**: The transpose of a matrix; `transpose.isInv` shows transpose preserves invertibility.

#### Extensionality

- **`matrixExt`**: Pointwise equality of entries gives matrix equality.
- **`matrixExt'`**: Equality at the underlying nested-array level lifts to matrix equality.

#### Block Matrices

- **`blockMatrix`**: Diagonal block `[[A,0],[0,B]]` of size `(n+n') × (m+m')`; submodule `elem00`/`elem01`/`elem10`/`elem11` characterize its entries.
- **`blockMatrix_minor`**: Minor of a block matrix at indices in the `A`-block reduces to block of the minor.
- **`blockMatrix_*`**: Multiplication of block-diagonal matrices is blockwise.
- **`block21Matrix`**: Vertical stacking `[A; B]` (sizes `(n+m) × k`).
- **`block12Matrix`**: Horizontal stacking `[A | B]` (sizes `k × (n+m)`).
- **`blockMatrix_*-left`**, **`blockMatrix_*-right`**: Mixed products of `blockMatrix` with vertical/horizontal blocks.
- **`block12_21_*`**: `[A|B] · [C;D] = A·C + B·D`.

#### Algebraic Structures

- **`MatrixAbGroup`**: `AbGroup` instance on `Matrix A n m` with entrywise `+`, `zro`, `negative`.
- **`MatrixModule`**: `LModule R` instance on `Matrix R n m` via entrywise scalar multiplication; `*c-gen` is the same operation parametrized by an arbitrary `LModule`.
- **`MatrixRing`**: `Ring` instance on `Matrix R n n`. Contains the multiplication operator `product`, the generalized `product-gen` (right-acting on a module), identity/zero/distributivity/associativity/negation lemmas (`product_ide-left`, `product_ide-right`, `product_zro-left`, `product_zro-right`, `product-assoc`, `product-ldistr`, `product-rdistr`, `product_negative-left`, `product_negative-right`, …), the alternative identity matrix definition `ide'` with `ide=ide'`, and `ide_generates`.
- **`MatrixAlgebra`**: `AAlgebra` instance over a commutative ring `R` combining `MatrixRing` and `MatrixModule`; lemmas `product_*c-comm-left`/`product_*c-comm-right` show scalar action commutes with multiplication.

#### Diagonal Matrices

- **`diagonal`**: Diagonal matrix from an array `l`.
- **`diagonal-isDiagonal`**: `diagonal l` satisfies `IsDiagonal`.

#### Determinants

- **`determinant`**: Permutation-sum definition `Σ_{e ∈ Sym n} sign(e) · ∏_j M (e j) j`.
- **`determinant.minor`**: `(n) × (m)` minor obtained by removing row `i0` and column `j0` from a `(suc n) × (suc m)` matrix.
- **`determinant.minorExt`**: Minors agree if entries off the removed row/column agree.
- **`determinant.minor'`**, **`determinant.minor=minor'`**: Alternative minor definition (skip rows after column-skip) and proof of equivalence.
- **`determinant.skip_transpose`**, **`determinant.minor_transpose`**: Compatibility of `skip`/`minor` with transpose.
- **`determinant.multilinear`**, **`determinant.alternating`**, **`determinant.alternatingT`**: Multilinearity and alternating properties (in rows, and in rows of the transpose).
- **`determinant.minor00`**: Explicit form of the `(0,0)`-minor.
- **`determinantN`**: Cofactor expansion along column `k`: `Σ_i M i k · (-1)^{i+k} · det (minor M i k)`. The `\where` block contains key lemmas (`minor_insert`, `minor_replace`, `multilinear`, `alternating`) and `=determinant` showing it agrees with `determinant`.
- **`determinant=determinant0`**: Defining-formula determinant equals expansion along column 0.
- **`determinant00`**, **`determinant11`**, **`determinant22`**: Closed forms for `0×0`, `1×1`, `2×2` cases.
- **`determinant_ide`**: `det 1 = 1`.
- **`determinant_*`**: `det (M · N) = det M · det N`.
- **`determinant_transpose`**: `det Mᵀ = det M`.
- **`determinant_map`**: Ring homomorphisms commute with `determinant`.
- **`determinant_block`**: `det (blockMatrix A B) = det A · det B`.
- **`equations-determinant`**: If `A · U = 0`, then `det(A) · U = 0` (Cramer-style consequence).

#### Adjugate and Invertibility

- **`adjugate`**: Adjugate (classical adjoint) matrix; entry `(i,j)` is `(-1)^{j+i} · det(minor M j i)`.
- **`adjugate_transpose`**: `adj(Mᵀ) = (adj M)ᵀ`.
- **`adjugate-left`** / **`adjugate-right`**: `adj(M)·M = det(M)·1` and `M·adj(M) = det(M)·1`; auxiliary lemmas `determinant_adjugate_=`, `determinant_adjugate_/=`, `adjugate-lem`, `adjugateExt`.
- **`transpose_*`**: `(M·M')ᵀ = M'ᵀ · Mᵀ`; `transpose_*.product` is the non-square version.
- **`determinant-inv`**: `M` invertible `iff` `det(M)` invertible.
- **`matirx-inv`**: One-sided inverse `A·B = 1` makes `B` invertible (matrix monoid).
- **`determinant1_Inv`**: `det A = 1` implies `A` is invertible.
- **`blockMatrix_Inv`**: `blockMatrix A B` is invertible `iff` both `A` and `B` are.

#### Triangular and Diagonal Predicates

- **`IsDiagonal`**: Predicate: off-diagonal entries are zero. Submodule includes `transposed`, `=>upperTriangular`, `=>lowerTriangular`.
- **`IsUpperTriangular`**: Below-diagonal entries (`j < i`) are zero.
- **`IsLowerTriangular`**: Above-diagonal entries (`i < j`) are zero.
- **`*c_IsDiagonal`**: Scalar multiplication preserves diagonality.
- **`determinant_IsUpperTriangular`**, **`determinant_IsLowerTriangular`**, **`determinant_IsDiagonal`**: Determinant of a triangular/diagonal matrix is the product of diagonal entries.
- **`determinant_diagonal`**: `det (diagonal l) = ∏ l`.

#### Rank-Style Lemmas

- **`determinant-nonSquare`**: For `m < n`, the determinant of `A · B` (with `A : n×m`, `B : m×n`) is zero; helpers `determinant-nonSquare_+` and `determinant_block12`.
- **`matrix-split-trivial`**: If a "wide·tall" product equals identity with `m < n`, then `0 = 1` in `R`.
- **`matrix-split_<=`**: Over a non-zero commutative ring, `A · B = 1` forces `n ≤ m`.
