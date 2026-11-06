### Algebra.Linear.Matrix.CharPoly

Characteristic polynomials of square matrices over commutative rings.

This module defines the characteristic polynomial `charPoly M = det(X·I − M)` of a square matrix `M` over a commutative ring `R`, viewed as an element of the polynomial ring `Poly R`. The construction is built directly from the determinant of the matrix obtained by lifting `M` to constant polynomials and subtracting it from `X` times the identity, ensuring the resulting polynomial is monic of degree `n`. The module also relates the characteristic polynomial to eigenvalues, showing that any eigenvalue is a root of the characteristic polynomial — the elementary half of the spectral relationship between `charPoly` and the matrix's spectrum.

#### Definition

- **`charPoly`**: The characteristic polynomial of `M : Matrix R n n` over a commutative ring `R`, defined as `det(X·I − M)` in `Poly R` (using `padd 1 0` for `X` and `padd 0` to embed scalars as constant polynomials).

#### Basic Properties

- **`charPoly-degree`**: The characteristic polynomial of an `n × n` matrix has degree at most `n`: `degree<= (charPoly M) n`.
- **`charPoly-monic`**: The characteristic polynomial is monic — its degree-`n` coefficient equals `1`: `polyCoef (charPoly M) n = 1`.
- **`charPoly_map`**: Naturality with respect to ring homomorphisms: applying a ring hom `f : R → S` coefficient-wise commutes with forming the characteristic polynomial, i.e. `polyMap f (charPoly A) = charPoly (matrix-map f A)`.

#### Eigenvalues and Roots

- **`isEigenvalue`**: Predicate stating that `a : R` is an eigenvalue of `A : Matrix R n n`, asserted as the existence of a nonzero column vector `U : Matrix R n 1` (nonzero in the sense that `x *c U = 0` forces `x = 0`) satisfying `a *c U = A · U`.
- **`eigen-root`**: Every eigenvalue is a root of the characteristic polynomial: if `a` is an eigenvalue of `A`, then `polyEval (charPoly A) a = 0`.
