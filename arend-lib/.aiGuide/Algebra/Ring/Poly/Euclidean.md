### Algebra.Ring.Poly.Euclidean

Polynomial rings over a discrete field as Euclidean domains via polynomial long division.

This module establishes that `Poly R` is a Euclidean domain whenever `R` is a discrete field, using the polynomial degree as the Euclidean function. The division algorithm is implemented via fuel-based recursion (`divMod_fuel`) that subtracts leading-coefficient-aligned monomial multiples of the divisor from the dividend until the remainder has lower degree than the divisor. Special cases handle division by constants (using inversion of the leading coefficient) and by zero. The module also provides decidability of unit and divisibility predicates, and uses the Bézout structure to construct maximal ideals containing a given non-unit polynomial when the base field is countable.

#### Euclidean Structure

- **`PolyEuclideanRingData`**: Instance of `EuclideanRingData (Poly R)` for a discrete field `R`. Sets the Euclidean map to `degree` and implements `divMod` by case-splitting on whether `degree q = 0`: constants are handled directly via `finv` of the leading coefficient, while non-constant divisors use `divMod_fuel`.
- **`PolyEuclideanDomain`**: Instance of `EuclideanDomain (Poly R)` over a discrete field, combining the Euclidean ring data with the decidable integral domain structure on `Poly R`.

#### Division Algorithm

- **`divMod_fuel`**: Fuel-recursive polynomial long division. Given `p`, `q`, and a fuel `n`, returns the quotient/remainder pair by repeatedly subtracting `monomial (leadCoef p * finv (leadCoef q)) (degree p -' degree q) * q` from `p`. Terminates when `degree p < degree q`.
- **`divMod_fuel-correct`**: Correctness lemma: `q * d + r = p` for the `(d, r)` produced by `divMod_fuel`, given sufficient fuel `degree p < n` and a non-constant divisor.
- **`divMod_fuel-rem-lem`**: Bound on the remainder: `degree (divMod_fuel p q n).2 < degree q` whenever `degree q /= 0`.
- **`diff-lem`**: Key degree-decrease lemma: if two polynomials share the same (non-zero) degree and leading coefficient, their difference has strictly smaller degree. Justifies termination of the long-division step.

#### Decidability

- **`poly-dec-unit`**: Decidability of being a unit in `Poly R`: `Dec (Inv p)`. Reduces to checking whether `p` is a non-zero constant.
- **`poly-dec-div`**: Decidability of divisibility: `Dec (TruncP (LDiv a b))`. Uses the division algorithm to test whether the remainder is zero.

#### Maximal Ideals

- **`poly-maximal-ideal`**: For a countable discrete field `K` and a non-unit `p : Poly K`, constructs a maximal ideal `M` of `PolyAlgebra K` containing `p`. Built by applying `BezoutRing.maximal-ideal` to the singleton list `p :: nil`, leveraging the Bézout structure on polynomial rings over a field together with countability of `Poly K`.
