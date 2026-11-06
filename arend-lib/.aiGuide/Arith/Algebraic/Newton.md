### Arith.Algebraic.Newton

A quantitative Newton method over the complex numbers, producing a root of a polynomial from explicit smallness data.

The iteration `newton` is z_{k+1} = z_k - p(z_k)·d with the inverse derivative `d` frozen at the starting point; freezing is needed constructively because the complex numbers are not a discrete field, so an inverse is data rather than a function. The linear term then no longer cancels exactly, and the resulting defect is controlled by the Lipschitz and quadratic remainder bounds of `Arith.Algebraic.Taylor`, uniform on a disc. The geometric convergence of the iterates yields the main result `newton-root`: a root of `p` exists whenever the stated bounds on the starting point hold.

#### Iteration and existence of a root

- **`newton`**: The Newton iteration `newton p dinv z0 k` for a polynomial `p` over `ComplexField`, with `newton p dinv z0 0 = z0` and each step subtracting `polyEval p z * dinv` from the current iterate.
- **`newton-root`**: Newton's method run from coefficient data. If `p` has degree `< M`, `dinv` is an inverse of `p'(z0)`, `|z0| <= R0`, `|dinv| <= Dd`, `|p(z0)| <= e0`, and the three smallness conditions (`hB`, `hL`, `hC`, expressed through `coefMax` and `RealField.powSum`) hold, then there exists `z` with `polyEval p z = 0`.
