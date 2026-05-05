### Algebra.Linear.Matrix.Smith

Smith normal form for matrices over commutative rings: matrix equivalence under invertible row/column operations, the Smith property (diagonal with successive divisibility), Smith rings (Kaplansky/Bezout-style rings admitting Smith reduction), and uniqueness up to associates.

#### Matrix Equivalence

- **`M~`**: Matrix equivalence relation: `A M~ B` iff `B = C * A * D` for some invertible `C : Matrix R n n` and `D : Matrix R m m`.
- **`M~.transposed`**: Equivalence is preserved under transposition (over a commutative ring).
- **`*c_M~`**: Scaling preserves equivalence: `A M~ B → a *c A M~ a *c B`.
- **`blockMatrix_M~`**: Block diagonal construction respects equivalence.
- **`M~-equiv`**: `M~` is an equivalence relation (reflexive, symmetric, transitive) on matrices.

#### Smith Normal Form Predicate

- **`IsSmith`**: Predicate stating a matrix is diagonal and each diagonal entry divides the next: combines `IsDiagonal A` with successive divisibility `A_{i,i} | A_{i+1,i+1}`.
- **`IsSmith.transposed`**: `IsSmith` is preserved by transposition.
- **`IsSmith.smith-diag`**: For a Smith matrix, `A i j` divides `A i' j'` whenever `i = j` (on the diagonal) and `i ≤ i'`.
- **`IsSmith.smith-diag.aux`**: Auxiliary divisibility along the diagonal extended by an offset `k`.
- **`IsSmith.smith-diag-func`**: Truncation-merged version: a single (truncated) function witnessing diagonal-to-anywhere divisibility.
- **`IsSmith.div00`**: The top-left entry `A 0 0` divides every entry of a Smith matrix.

#### Smith Rings

- **`SmithRing`**: A ring class extending `StrictBezoutRing` with the Kaplansky condition `isKaplansky`, characterizing rings over which every matrix admits a Smith normal form.
- **`SmithRing.IsKaplansky`**: Kaplansky's coprimality condition: for any coprime triple `(a, b, c)` there exist `t, s` with `t*a` and `t*b + s*c` coprime.
- **`SmithRing.Smith-char`**: TFAE characterization of Smith rings — strict Bezout + Kaplansky ⟺ 2×2 diagonalization of coprime matrices ⟺ diagonalization for `n ≤ m ≤ 2` ⟺ full Smith normal form for all matrices.
- **`SmithRing.Smith-char.transposed`**: Reduces existence of a Smith form for `A` to one for `transpose A`.
- **`SmithRing.Smith-char.smith-isBezout`**: Diagonalization of `1×2` matrices implies the strict Bezout property.
- **`SmithRing.Smith-char.diagonalize_normed`**: Diagonalization for arbitrary `2×2` matrices with `A 1 0 = 0`, derived from the coprime case.
- **`SmithRing.Smith-char.aux`**: Strong induction step lifting `2×2`/small-case diagonalization to full Smith form.
- **`SmithRing.elimRow`**: Given `A 0 0` divides each `A (suc i) 0`, produces an equivalent matrix with zeros below the pivot in column 0 while preserving the first row.
- **`SmithRing.elimColumn`**: Dual of `elimRow`: zeros out the first row past the pivot while preserving column 0 (via transposition).
- **`SmithRing.elimRowColumn`**: Combines `elimRow` and `elimColumn` to clear both the first row and first column past the `(0,0)` pivot.
- **`SmithRing.M~_LDiv`**: If `a` divides every entry of `A` and `A M~ B`, then `a` divides every entry of `B`.
- **`SmithRing.ind-step`**: Inductive step for Smith reduction: assuming Smith form exists for `n × m` matrices and `A 0 0` divides every entry of `(suc n) × (suc m)` matrix `A`, produces a Smith form equivalent to `A`.

#### 2×2 Embedding into Larger Matrices

- **`embed22`**: Embeds a `2×2` matrix `A` into an `(n+1) × (n+1)` matrix at position `(0, k)`, used to lift `2×2` row/column operations into larger matrix transformations.
- **`embed22.replace2`**: Replaces two entries `i, j` in an array with values `a, b`.
- **`embed22.replace2-first`**, **`embed22.replace2-second`**: Lookup lemmas for `replace2` at indices `i` and `j`.
- **`embed22_determinant`**: The embedding preserves determinant: `determinant (embed22 A k) = determinant A` (when `k ≠ 0`).
- **`embed22_determinant.embed22_00`**: `(embed22 A k) 0 0 = A 0 0`.
- **`embed22_determinant.embed22_minor00`**: The `(0,0)`-minor of `embed22 A (suc k)` equals `diagonal1 k (A 1 1)`.
- **`embed22_determinant.diagonal1`**: Identity matrix with one diagonal entry replaced by `a` at position `k`.
- **`embed22_determinant.determinant_diagonal1`**: `determinant (diagonal1 k a) = a`.
- **`embed22_determinant.embed22_minor_k0`**: Explicit description of the `(suc k, 0)`-minor of `embed22 A (suc k)`.
- **`embed22_determinant.embed22_minor_k0_perm`**: Permutation relating `diagonal1 k (A 1 0)` to the `(suc k, 0)`-minor, with inversion count `k`.

#### Smith Domains

- **`SmithDomain`**: A class extending `BezoutDomain.Dec` and `SmithRing` — Smith rings that are also decidable Bezout domains.

#### Uniqueness up to Associates

- **`smith-unique`**: Over a strict integral domain, the entries of two equivalent Smith matrices are associates: `A M~ B ∧ IsSmith A ∧ IsSmith B → A i j ~ B i j`.
- **`smith-unique.subMatrix`**: Builds the submatrix indexed by row list `li` and column list `lj`.
- **`smith-unique.subMatrix_perm`**: Permuting the row index list induces a permutation of the submatrix rows.
- **`smith-unique.subMatrix-div`**: If `d` divides every `l × l` minor of `A`, it divides every `l × l` minor of `B * A * C`.
- **`smith-unique.subMatrix-div.subMatrix-div-left`**: One-sided version: divisibility of minors is preserved under left multiplication.
- **`smith-unique.index`**: Coerces `i : Fin k` into `Fin n` given `k ≤ n`.
- **`smith-unique.diagProd`**: Product of the first `k` diagonal entries of `A` (using `index p`, `index q`).
- **`smith-unique.diagProd_suc`**: Recursive expansion: `diagProd` for `suc k` equals `diagProd` for `k` times the next diagonal entry.
- **`smith-unique.diag-sub`**: For diagonal `A`, the diagonal product equals the determinant of the leading principal `k × k` submatrix.
- **`smith-unique.determinant_subMatrix`**: Determinant of a submatrix of a diagonal matrix indexed by sorted, injective row/column lists factors as a product of diagonal entries.
- **`smith-unique.diag-div`**: For Smith `A`, `diagProd A p q` divides every `k × k` minor `det(subMatrix A li lj)`.
- **`smith-unique.diag-div.sorted-monotone`**: Sorted injective `Fin n`-arrays are pointwise-monotone: `j ≤ l j`.
- **`smith-unique.diag-div.sorted-monotone.aux`**: Generalization with offset `k` for the monotonicity bound.
- **`smith-unique.prod-div`**: Diagonal product of `A` divides diagonal product of `B` whenever both are Smith and `A M~ B`.
- **`smith-unique.smith-div`**: Over reduced commutative rings: equivalent Smith matrices satisfy `A i j | B i j` entrywise.
- **`smith-unique.smith-div-equiv`**: Two-sided version: divisibility holds in both directions, yielding the associate relation.
