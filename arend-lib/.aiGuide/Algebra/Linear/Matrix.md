### Algebra.Linear.Matrix

Matrices over a ring, their algebraic structure, and the theory of determinants.

This module defines `Matrix R n m` as nested arrays and equips square matrices with the full ring/algebra structure: matrices over an abelian group form an abelian group, matrices over a ring form an `LModule`, square matrices form a `Ring`, and over a commutative ring they form an `AAlgebra`. The determinant is built via the symmetric group `Sym n` and a sign, with an equivalent cofactor-expansion form `determinantN` proven multilinear and alternating; key results include multiplicativity, transpose invariance, the adjugate identities `adj(M) * M = det(M) · 1`, and the equivalence `Inv M ↔ Inv (det M)`. Block matrices, triangular/diagonal predicates, and minors are provided to support inductive arguments and reasoning about decomposed systems.

#### Core Type and Constructors

- **`Matrix`**: `Array (Array R m) n` — a matrix as `n` rows of length `m`.
- **`mkMatrix`**: Builds a matrix from a function `Fin n -> Fin m -> R`.
- **`makeMatrix`**: Coerces a nested array to `Matrix`.
- **`mkColumn`**: A length-`n` array as an `n × 1` column matrix.
- **`mkRow`**: A length-`n` array as a `1 × n` row matrix.
- **`addColumn`**: Prepends a column to a matrix.
- **`addRow`**: Prepends a row to a matrix.
- **`matrixExt`**, **`matrixExt'`**: Extensionality — equality of matrices from pointwise equality.
- **`matrix-map`**: Apply a function `A -> B` to every entry.

#### Block Matrix Constructions

- **`blockMatrix`**: Block-diagonal `[[A,0],[0,B]]` of size `(n+n') × (m+m')`.
- **`blockMatrix.elem00/elem10/elem01/elem11`**: Entry lemmas for the four block regions.
- **`block21Matrix`**: Vertical stacking `[[A],[B]]` (same column count).
- **`block12Matrix`**: Horizontal concatenation `[A | B]` (same row count).
- **`blockMatrix_minor`**: Taking the `(fin-inc i, fin-inc j)` minor commutes with stacking with `B`.
- **`blockMatrix_*`**, **`blockMatrix_*-left`**, **`blockMatrix_*-right`**: Multiplication interacts blockwise with `blockMatrix`/`block12`/`block21`.
- **`block12_21_*`**: `block12 A B · block21 C D = A·C + B·D`.

#### Algebraic Instances

- **`MatrixAbGroup`**: `Matrix A n m` is an `AbGroup` over an `AbGroup` `A` (pointwise `+`, `zro`, `negative`).
- **`MatrixModule`**: `Matrix R n m` is an `LModule` over a ring `R` with pointwise scalar multiplication.
- **`MatrixModule.*c-gen`**: Scalar multiplication generalised to matrices over an arbitrary `LModule`.
- **`MatrixRing`**: Square matrices `Matrix R n n` form a `Ring`; `ide` is the identity diagonal, `*` is `product`.
- **`MatrixRing.product`**: Standard matrix multiplication via `BigSum`.
- **`MatrixRing.product-gen`**: Generalised product `Matrix R n m × Matrix L m k -> Matrix L n k` with values in an `LModule`.
- **`MatrixRing.product_ide-left/right`**, **`product_zro-left/right`**: Identity and zero behaviour of the product.
- **`MatrixRing.product-assoc`**, **`product-gen-assoc`**: Associativity (also for the generalised version).
- **`MatrixRing.product-ldistr/rdistr`**, **`product-gen-ldistr/rdistr`**: Distributivity over `+`.
- **`MatrixRing.product_negative-left/right`**, **`product-gen_negative-*`**: Compatibility with `negative`.
- **`MatrixRing.ide_generates`**: The identity matrix generates the array `LModule`.
- **`MatrixRing.ide'`**, **`ide=ide'`**: Recursive presentation of the identity matrix and its agreement with `ide`.
- **`MatrixAlgebra`**: For a `CRing` `R`, `Matrix R n n` is an `R`-algebra (`AAlgebra`).
- **`MatrixAlgebra.product_*c-comm-left/right`**: Scalar multiplication commutes with matrix multiplication.

#### Diagonal Construction

- **`diagonal`**: Diagonal matrix of size `len(l) × len(l)` from an array `l`.

#### Determinant

- **`determinant`**: `det M = Σ_{e ∈ Sym n} sign(e) · ∏_j M(e j, j)`.
- **`determinant.minor`**: Submatrix obtained by removing row `i0` and column `j0`.
- **`determinant.minorExt`**: Two matrices agreeing off row `i`/column `j` have equal `(i,j)`-minors.
- **`determinant.minor'`**, **`minor=minor'`**: Alternative minor (skip rows after columns) and equivalence proof.
- **`determinant.skip_transpose`**: Skipping commutes with transposition.
- **`determinant.minor_transpose`**: `minor (transpose M) i j = transpose (minor M j i)`.
- **`determinant.multilinear`**, **`determinant.alternating`**, **`determinant.alternatingT`**: Multilinearity and alternation in rows (and after transposition).
- **`determinant.minor00`**: Concrete formula for `minor M 0 0`.
- **`determinantN`**: Cofactor (Laplace) expansion along column `k`.
- **`determinantN.minor_insert`**, **`aux/=`**, **`minor_replace`**: Compatibility of `minor` with `insert`/`replace` operations on rows.
- **`determinantN.multilinear`**, **`determinantN.alternating`**: Multilinearity and alternation of `determinantN`; the `alternating` proof builds permutations of rows and bounds their inversions.
- **`determinantN.determinantN_ide`**: `determinantN k ide = 1`.
- **`determinantN.=determinant`**: `determinantN k M = determinant M` — Laplace expansion equals the symmetric-group definition.
- **`determinantN.determinant=determinant0`**: Particular case at `k = 0` for `suc n`.

#### Determinant: Small Cases and Identities

- **`determinant00`**: `det A = 1` for `0 × 0`.
- **`determinant11`**: `det A = A 0 0` for `1 × 1`.
- **`determinant22`**: Standard `ad - bc` formula.
- **`determinant_ide`**: `det(ide) = 1`.
- **`determinant_*`**: Multiplicativity: `det (M · N) = det M · det N`.
- **`determinant_transpose`**: `det (transpose M) = det M`.
- **`determinant_map`**: A ring homomorphism commutes with `determinant`.
- **`determinant_block`**: `det (blockMatrix A B) = det A · det B`.

#### Transpose

- **`transpose`**: Transpose of a matrix.
- **`transpose.isInv`**: Invertibility transports along transposition.
- **`transpose_*`**: `transpose (M · M') = transpose M' · transpose M`; `.product` is the non-square version.

#### Adjugate and Inversion

- **`adjugate`**: Classical adjugate (cofactor) matrix; `adj(M)_{ij} = (-1)^{j+i} · det (minor M j i)`.
- **`adjugate_transpose`**: `adjugate` commutes with `transpose`.
- **`adjugate-left`**, **`adjugate-right`**: Cramer-style identities `adj(M) · M = det M ·c 1` and `M · adj(M) = det M ·c 1`.
- **`adjugate-left.determinant_adjugate_=`**: Diagonal sum of adjugate-times-matrix is `det M`.
- **`adjugate-left.adjugate-lem`**, **`adjugateExt`**, **`determinant_adjugate_/=`**: Auxiliary identities used to prove the Cramer relations.
- **`determinant-inv`**: `Inv M ↔ Inv (det M)` — invertibility iff the determinant is a unit.
- **`matirx-inv`**: A right inverse to `A` is itself invertible (when `A * B = 1`).
- **`determinant1_Inv`**: `det A = 1` implies `A` is invertible.
- **`blockMatrix_Inv`**: `Inv (blockMatrix A B) ↔ Inv A × Inv B`.

#### Triangular and Diagonal Predicates

- **`IsDiagonal`**: Off-diagonal entries are zero.
- **`IsDiagonal.transposed`**, **`=>upperTriangular`**, **`=>lowerTriangular`**: Diagonal implies triangular (both ways) and is preserved under transposition.
- **`IsUpperTriangular`**: Entries below the diagonal vanish.
- **`IsLowerTriangular`**: Entries above the diagonal vanish.
- **`*c_IsDiagonal`**: Scalar multiples preserve diagonality.
- **`diagonal-isDiagonal`**: `diagonal l` is diagonal.
- **`determinant_IsUpperTriangular`**, **`determinant_IsLowerTriangular`**, **`determinant_IsDiagonal`**: Determinant of a triangular/diagonal matrix is the product of diagonal entries.
- **`determinant_diagonal`**: `det (diagonal l) = ∏ l`.

#### Non-Square Products and Rank

- **`determinant-nonSquare`**: For `m < n`, any product `A · B` of an `n × m` and an `m × n` matrix has zero determinant.
- **`determinant-nonSquare_+`**, **`determinant_block12`**: Auxiliary forms for sizes `k + n` and for `block12Matrix 0 A`.
- **`matrix-split-trivial`**: `A · B = ide` with `m < n` forces `0 = 1` in `R`.
- **`matrix-split_<=`**: Over a non-trivial `CRing`, `A · B = ide` forces `n ≤ m` (rank/dimension lower bound).

#### Linear System Solving

- **`equations-determinant`**: If `A · U = 0` (with `U` valued in any `R`-module), then `det A ·c U = 0` — Cramer-style consequence used for solving systems.
