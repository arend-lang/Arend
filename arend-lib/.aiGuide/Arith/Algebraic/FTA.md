### Arith.Algebraic.FTA

The fundamental theorem of algebra for polynomials over discrete subfields of the complex numbers, culminating in algebraic closedness of the algebraic numbers.

The proof goes through a separable-polynomial root lemma over `ComplexField`, then transfers to any discrete field `K` embedded in `ComplexField` (separable factor, monic associate, root with integrality), and specializes to `RatField` and `AlgNumbersRing`.

#### Complex roots

- **`separable-root`**: a separable polynomial `q` over `ComplexField` with `degree< q (suc (suc m))` and invertible coefficient `polyCoef q (suc m)` has a complex root: `∃ (z : Complex) (polyEval q z = 0)`. Proved without choice.

#### Discrete subfields of the complex numbers

- **`FTA-discrete`**: for a discrete field `K` with a ring homomorphism `f : RingHom K ComplexField` and `p : Poly K` with `degree p = suc n`, the image `polyMap f p` has a complex root `z` that is also integral over `K` via `f` (`isIntegral f z`). No monicity or degree bound is assumed.
- **`FTA-rat`**: every rational polynomial of degree `suc n` has a complex root of `polyMap ratToComplex p`, and that root is an algebraic number (`IsAlgNumber z`). The `RatField` case of `FTA-discrete`.

#### Algebraic numbers

- **`FTA-algNumbers`**: every polynomial over `AlgNumbersRing` of degree `suc n` has a root in `AlgNumbersRing`; uses `FTA-discrete` and that the algebraic numbers are integrally closed in `Complex`.
- **`algNumbers-algClosed`**: `AlgNumbersRing` is algebraically closed (`IsAlgebraicallyClosed`), derived from `FTA-algNumbers`.
