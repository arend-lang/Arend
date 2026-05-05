### Algebra.Ring.Poly.Euclidean

Establishes that the polynomial ring over a discrete field is a Euclidean domain, with division algorithm based on degree.

#### Euclidean Structure Instances

- **`PolyEuclideanRingData`**: Instance of `EuclideanRingData (Poly R)` for a discrete field `R`. Uses `degree` as the Euclidean map and implements `divMod` via long division: when `degree q = 0`, divides by the leading coefficient (or returns `(0, p)` if `q = 0`); otherwise iterates `divMod_fuel`.
- **`PolyEuclideanDomain`**: Instance of `EuclideanDomain (Poly R)` for a discrete field `R`, extending `PolyDecIntegralDomain` with the Euclidean structure from `PolyEuclideanRingData`.

#### Long Division Algorithm

- **`divMod_fuel`**: Fuel-bounded polynomial long division. Recursively subtracts `monomial (leadCoef p * finv (leadCoef q)) (degree p -' degree q) * q` from `p` until `degree p < degree q`, accumulating the quotient.
- **`divMod_fuel-correct`**: Correctness of `divMod_fuel`: `q * quotient + remainder = p` when fuel exceeds `degree p`.
- **`divMod_fuel-rem-lem`**: The remainder produced by `divMod_fuel` has degree strictly less than `degree q`.
- **`diff-lem`**: If `p` and `q` have equal degrees and equal leading coefficients (with `degree q /= 0`), then `degree (p - q) < degree p`. Key lemma justifying termination of long division.

#### Decidability Lemmas

- **`poly-dec-unit`**: Decidability of invertibility for polynomials over a discrete field: `Dec (Inv p)` (a polynomial is a unit iff it is a nonzero constant).
- **`poly-dec-div`**: Decidability of divisibility for polynomials over a discrete field: `Dec (TruncP (LDiv a b))`.

#### Maximal Ideals

- **`poly-maximal-ideal`**: For a countable discrete field `K` and a non-unit polynomial `p`, constructs a maximal ideal `M` of `PolyAlgebra K` containing `p`. Built via `BezoutRing.maximal-ideal` using the countability of `Poly K` and decidable divisibility.
