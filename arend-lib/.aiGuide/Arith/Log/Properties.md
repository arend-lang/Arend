### Arith.Log.Properties

The composition log(1 + (exp w - 1)) = w on a Banach algebra.

This is the other direction of the inverse relation between `exp` and `log1+` proved in `Arith.Log`. It is derived by uniqueness of inverses in the group of formal line diffeomorphisms (the formal inverse of `exp - 1` equals log(1 + ·)) and then lifted analytically, with the radius of convergence controlled by `log 2`.

#### Inverse of log1+

- **`log1p-expm1`**: For a central `w` in a real Banach algebra `A` with `norm w < log2`, `log1+ (exp w - 1) _ = w`.
