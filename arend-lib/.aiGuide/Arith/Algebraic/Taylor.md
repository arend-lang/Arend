### Arith.Algebraic.Taylor

First- and second-order Taylor estimates for a complex polynomial, uniform on a disc.

Using the deflation identities `polyEval_deflate-diff` and `polyTaylor2`, which write p(y)-p(z) and p(y)-p(z)-p'(z)(y-z) as (y-z), respectively (y-z)², times a value of an iterated deflation, the module bounds the deflation coefficientwise. The constants depend only on `p`, its degree bound and the radius, not on the base point. These estimates feed the Newton iteration in `Arith.Algebraic.Newton`.

#### Disc-uniform bounds

- **`polyEval-diff-bound`**: A complex polynomial of degree `< m` with coefficients bounded by `c` is Lipschitz on the disc `|.| <= R`: `|p(y) - p(z)|` is at most `c * powSum R m * powSum R m * |y - z|`.
- **`polyEval-remainder-bound`**: Quadratic Taylor remainder on the disc `|.| <= R`: `|p(y) - p(z) - p'(z)(y - z)|` is at most `c * powSum R m^3 * |y - z|²`, with the same hypotheses.
