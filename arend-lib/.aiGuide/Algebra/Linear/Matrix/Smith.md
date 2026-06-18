### Algebra.Linear.Matrix.Smith

Smith normal form for matrices over Bezout-like rings: equivalence under invertible row/column operations and existence/uniqueness of a diagonal canonical form whose successive diagonal entries divide one another.

This module formalizes the classical Smith normal form theorem in the constructive setting of strict Bezout rings satisfying Kaplansky's condition. The matrix equivalence relation `M~` quotients by left/right multiplication by invertible matrices, and `IsSmith` captures the canonical shape: diagonal with each entry dividing the next. The main result is `SmithRing.toSmith`, proved via `Smith-char` which gives a TFAE characterization reducing the general case to a 2×2 diagonalization step plus an inductive elimination of the first row and column. Uniqueness up to associates is established for reduced commutative integral domains via the divisor characterization of products of diagonal entries as determinants of submatrices (`diag-sub`, `prod-div`).

#### Matrix Equivalence

- **`M~`**: Equivalence of matrices `A B : Matrix R n m` by `B = C * A * D` for invertible `C : Matrix R n n` and `D : Matrix R m m`.
- **`M~.transposed`**: Equivalence is preserved by transposition (over a commutative ring).
- **`*c_M~`**: Scalar multiplication preserves `M~`.
- **`blockMatrix_M~`**: Block-diagonal construction respects `M~` componentwise.
- **`M~-equiv`**: `Equivalence` instance witnessing reflexivity, symmetry, and transitivity of `M~`.

#### Smith Normal Form Predicate

- **`IsSmith`**: A matrix is in Smith form iff it is diagonal and each diagonal entry `A_{i,i}` left-divides the next `A_{i+1,i+1}`.
- **`IsSmith.transposed`**: Smith form is preserved by transpose.
- **`IsSmith.smith-diag`**: Any entry `A i j` with `i = j` (as `Nat`) and `i ≤ i'` divides `A i' j'` along the diagonal extension.
- **`IsSmith.smith-diag-func`**: Functional/uncurried packaging of `smith-diag` under truncation.
- **`IsSmith.div00`**: The top-left entry `A 0 0` divides every entry `A i j` of a Smith matrix.

#### Smith Rings

- **`SmithRing`**: Class extending `StrictBezoutRing` with Kaplansky's condition `isKaplansky`; provides `toSmith` reducing any matrix to Smith form via `M~`.
- **`SmithRing.toSmith`**: Existence theorem — every matrix over a Smith ring is `M~`-equivalent to one in Smith form.
- **`IsKaplansky`**: Kaplansky's property: for any coprime triple `(a, b, c)`, there exist `t, s` with `t*a` and `t*b + s*c` coprime.
- **`Smith-char`**: TFAE characterization of Smith rings — Bezout+Kaplansky ⇔ 2×2 lower-triangular coprime diagonalization ⇔ diagonalization for `n ≤ m ≤ 2` ⇔ full Smith reduction.
- **`SmithDomain`**: Class extending `BezoutDomain.Dec` and `SmithRing`.

#### Reduction Lemmas (Row/Column Elimination)

- **`elimRow`**: Given `A 0 0` divides each `A (suc i) 0`, produce `B M~ A` with zeros below the pivot column and the top row unchanged, via multiplication by an explicit lower-triangular invertible matrix.
- **`elimColumn`**: Dual of `elimRow` — clears the top row to the right of the pivot using transposition.
- **`elimRowColumn`**: Combined elimination producing a matrix with `B 0 0 = A 0 0` and zeros throughout the rest of the first row and column.
- **`M~_LDiv`**: Divisibility of all entries by a fixed `a` is invariant under `M~`.
- **`Smith-char.diagonalize_normed`**: Reduction of 2×2 diagonalization to the lower-triangular coprime case.
- **`Smith-char.smith-isBezout`**: Diagonalizing every `1 × 2` matrix already implies the strict Bezout property.
- **`ind-step`**: Inductive core of `toSmith`: extends a Smith reduction on `n × m` to `(suc n) × (suc m)` matrices whose top-left entry divides every other entry.

#### Embedding 2×2 Operations

- **`embed22`**: Embeds a `2 × 2` matrix `A` into a `(suc n) × (suc n)` identity-based matrix at coordinates `0` and `k`, used to apply 2×2 row/column operations within larger matrices.
- **`embed22.replace2`**, **`replace2-first`**, **`replace2-second`**: Helpers for double substitution into arrays.
- **`embed22_determinant`**: `determinant (embed22 A k) = determinant A` for `k ≠ 0`, ensuring such embeddings preserve invertibility.
- **`embed22_determinant.embed22_00`**, **`embed22_minor00`**, **`embed22_minor_k0`**, **`embed22_minor_k0_perm`**: Auxiliary minor and permutation computations supporting the determinant identity.
- **`embed22_determinant.diagonal1`**, **`determinant_diagonal1`**: A diagonal matrix with a single non-identity entry, with determinant equal to that entry.

#### Uniqueness of Smith Form

- **`smith-unique`**: Over a strict integral domain, two `M~`-equivalent Smith matrices have associate corresponding entries.
- **`smith-unique.subMatrix`**: Selects a submatrix by index arrays `li` (rows) and `lj` (columns).
- **`smith-unique.subMatrix_perm`**: Permuting the row index array induces a permutation on the submatrix rows.
- **`smith-unique.subMatrix-div`**: Divisibility of all `l × l` minors of `A` by `d` propagates through products `B · A · C` (Cauchy–Binet style).
- **`smith-unique.subMatrix-div.subMatrix-div-left`**: One-sided variant of `subMatrix-div`.
- **`smith-unique.index`**, **`diagProd`**: First `k` diagonal entries (as a finite product) of a matrix.
- **`smith-unique.diagProd_suc`**: Recursive expansion `diagProd_{k+1} = diagProd_k * A_{k,k}`.
- **`smith-unique.diag-sub`**: For diagonal `A`, the leading `k`-diagonal product equals the determinant of the leading `k × k` submatrix.
- **`smith-unique.determinant_subMatrix`**: Determinant of a sorted-index submatrix of a diagonal matrix is the product of selected diagonal entries.
- **`smith-unique.diag-div`**: For Smith `A`, the leading diagonal product `diagProd A p q` divides every `k × k` submatrix determinant.
- **`smith-unique.diag-div.sorted-monotone`**: A sorted injective `Fin`-array satisfies `j ≤ l j` pointwise.
- **`smith-unique.prod-div`**: Leading diagonal products are divisibility-comparable for `M~`-equivalent Smith matrices.
- **`smith-unique.smith-div`**, **`smith-div-equiv`**: Over a reduced commutative ring, corresponding entries of `M~`-equivalent Smith matrices divide each other (so are associate).
