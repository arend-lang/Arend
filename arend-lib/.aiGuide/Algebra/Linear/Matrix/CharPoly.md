### Algebra.Linear.Matrix.CharPoly

Characteristic polynomial of a square matrix and its connection to eigenvalues.

#### Characteristic Polynomial

- **`charPoly`**: Characteristic polynomial of a matrix `M : Matrix R n n` over a commutative ring, defined as `det(X·I - M)` where `X = padd 1 0` is the polynomial indeterminate.

#### Properties

- **`charPoly-degree`**: The characteristic polynomial of an `n × n` matrix has degree at most `n`.
- **`charPoly-monic`**: The leading coefficient (at degree `n`) of `charPoly M` equals `1`, i.e. the characteristic polynomial is monic.
- **`charPoly_map`**: Naturality with respect to ring homomorphisms: `polyMap f (charPoly A) = charPoly (matrix-map f A)`.

#### Eigenvalues

- **`isEigenvalue`**: Predicate stating that `a : R` is an eigenvalue of `A`, witnessed by a nonzero column vector `U : Matrix R n 1` (nonzero in the sense that `x *c U = 0` implies `x = 0`) satisfying `a *c U = A · U`.
- **`eigen-root`**: Every eigenvalue is a root of the characteristic polynomial: if `a` is an eigenvalue of `A`, then `polyEval (charPoly A) a = 0`.
