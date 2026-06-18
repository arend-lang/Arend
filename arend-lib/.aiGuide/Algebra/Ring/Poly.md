### Algebra.Ring.Poly

Univariate polynomials over rings, defined as a higher inductive type with structural equations, plus their ring/algebra/module structure.

`Poly R` is built from `pzero` and `padd p e` (cons a coefficient onto a polynomial) with the equation `padd pzero 0 = pzero` ensuring leading-zero normalization. The module establishes the equivalence with the quotient form `QPoly R` to derive set-truncation, then layers on evaluation, coefficient extraction, degree bounds, the polynomial ring/algebra structure, division algorithms, and lifts of ring properties (domain, integral domain, decidable equality) from the base ring. A separate `polyModule` construction interprets a polynomial in `R[X]` as an iterated linear endomorphism, turning any `R`-module with an endomorphism into an `R[X]`-module.

#### Core Type and Conversions

- **`Poly`**: HIT of polynomials over `R : AddPointed` with constructors `pzero`, `padd : Poly R -> R -> Poly R`, and equation `peq : padd pzero 0 = pzero`.
- **`Poly.toQPoly`**: Conversion to the quotient-of-arrays representation `QPoly R`.
- **`Poly.fromArray`**: Builds a polynomial from a coefficient array.
- **`Poly.cfunc`**: `toQPoly (fromArray l) = in~ l`.
- **`Poly.gfunc`**: Inverse direction: any `p` with `toQPoly p = in~ l` equals `fromArray l`.
- **`Poly.toQPoly-equiv`**: `toQPoly` is an equivalence.
- **`Poly.levelSet`**: `Poly R` is a set (transported from the set structure of `QPoly R`).
- **`Poly.poly-countable`**: Countability of `Poly R` from countability of `R`.
- **`Poly.poly-trivial`**: If every element of `R` is zero, every polynomial equals `pzero`.

#### Coefficient Maps and Functoriality

- **`polyMap`**: Functorial action on polynomials along an `AddPointedHom`.
- **`polyMap-comp`**: Compatibility with composition.
- **`polyMap-inj`**: `polyMap f` is injective when `f` is.
- **`polyMap_fromArray`**: `polyMap` commutes with `fromArray`.
- **`polyMapRingHom`**: `polyMap` packaged as a `RingHom (PolyRing f.Dom) (PolyRing f.Cod)`.

#### Evaluation

- **`polyEval`**: Evaluate `p : Poly R` at `a : R` (Horner-style recursion).
- **`polyMapEval`**: Evaluate after mapping coefficients along an `AddPointedHom`.
- **`polyEval_polyMap`**: `polyEval (polyMap f p) (f a) = f (polyEval p a)` for ring homs.
- **`polyEval_fromArray`** / **`polyMapEval_fromArray`**: Evaluation of an array-based polynomial as `Σ l_j · a^j`.
- **`polyEvalRingHom`**: Evaluation at `a : R` packaged as a ring hom `PolyRing R -> R` (for `R : CRing`).
- **`polyMapEvalRingHom`**: Composite ring hom for evaluation through a coefficient map.
- **`polyMapEval-unique`**: Two ring homs out of `PolyRing R` are equal if they agree on constants and on `padd 1 0` (the indeterminate `X`).
- **`polyHom`**: Constants embedding `R -> PolyRing R` as a ring hom.
- **`polyHom_polyMapEval`**: Evaluating the canonical embedding at `X` recovers `p`.
- **`factorHom_polyMapEval`**: Compatibility of evaluation with quotient by an ideal.

#### Coefficients

- **`polyCoef`**: `n`-th coefficient of `p`.
- **`polyCoef=0`**: Coefficients of a constant polynomial vanish at positive indices.
- **`polyCoef_polyMap`**: `polyCoef` commutes with `polyMap`.
- **`polyCoef_fromArray`**: `polyCoef (fromArray l) j = l j`.
- **`polyCoef_+`**, **`polyCoef_negative`**, **`polyCoef_*c`**, **`polyCoef_*-right`**, **`polyCoef_*`**: Coefficients distribute over the ring/scalar operations.
- **`polyCoef_BigSum`**: Coefficient of a finite sum.
- **`polyCoefHom`**: `polyCoef · n` as an `AddGroupHom`.
- **`lastCoef`**: Constant term `polyCoef p 0`.
- **`lastCoef_*`**, **`lastCoef_pow`**: Multiplicativity of the constant term.
- **`polyShift`**: Drop the constant term, shifting coefficients down.

#### Monomials

- **`monomial`**: `c · X^n` as `padd … (padd pzero c) … 0`.
- **`polyCoef_monomial`**, **`polyMap_monomial`**, **`polyEval_monomial`**, **`polyMapEval_monomial`**: Standard identities for monomials.
- **`monomial_zro`**: `monomial 0 n = pzero`.
- **`*c_monomial`**: `a *c monomial c n = monomial (a*c) n`.
- **`poly-monomial-char`**: `monomial 1 n * p` equals `n` iterations of `padd __ 0` on `p`.
- **`poly-factor-monomial`**: Any nonzero polynomial factors uniquely as `X^n · q` with `q` having nonzero constant term.
- **`poly-factor-monomial-unique`**: Uniqueness of that factorization.
- **`monomial_div`**: A divisor of `X^n` (in a strict integral domain) is itself a monomial `c · X^k`.
- **`monomial_div2`**: If `monomial c (suc n)` divides `p`, then `p` has zero constant term.
- **`monomial-factor`**: Factorization lemma when `p * q = padd s 0`.

#### Degree Bounds

- **`degree<`**: Predicate `degree< p n` meaning all coefficients at index `≥ n` vanish.
- **`degree<=`**: Predicate `degree<= p n` meaning all coefficients at index `> n` vanish (recursively defined).
- **`degree<0`**: `degree< p 0 -> p = pzero`.
- **`degree<_padd`**, **`degree<_padd.conv`**: Compatibility with `padd`.
- **`degree<_polyMap`**, **`degree<=_polyMap`**: Preserved by coefficient maps.
- **`degree<=.toCoefs`**, **`degree<=.fromCoefs`**: Equivalence with the coefficient-vanishing characterization.
- **`degree<=.trivialPoly`**: Polynomials that are forced to have every coefficient zero are `pzero`.
- **`degree<=_degree<`**, **`degree<_degree<=`**: Translation between strict and non-strict degree bounds.
- **`degree-exists`**, **`degree<-exists`**: Existence of a degree bound for any polynomial.
- **`degree<=-trans`**: Monotonicity in the bound.
- **`degree-reduce`**: Lower a `degree<= p (suc n)` bound when the leading coefficient is zero.
- **`degree<=0`**: `degree<= p 0` characterizes `p` as a constant.
- **`degree<=_monomial`**: `degree<= (monomial c n) n`.
- **`fromArray_polyCoef`**: A polynomial bounded by `n` equals `fromArray` of its first `n` coefficients.
- **`fromArray_degree<`**: `fromArray l` has degree less than `l.len`.
- **`polyEval_polyCoef`** / **`polyMapEval_polyCoef`**: Evaluation as a finite sum of `coef · a^i` when degree is bounded.
- **`degree<_+`**, **`degree<_negative`**: Closure under sum/negation.
- **`degree<=_+`**, **`degree<=_negative`**, **`degree<=_*c`**, **`degree<=_*`**, **`degree<=_BigSum`**, **`degree<=_FinSum`**, **`degree<=_BigProd`**, **`degree<=_BigProd1`**, **`degree<=_pow`**: Closure of `degree<=` under the algebraic operations.
- **`degree<=0_*-conv`**: In a strict domain, `degree<= (p*q) 0` forces `p` or `q` to be a constant.

#### Leading Coefficients

- **`polyCoef-linear-*`**: Coefficient of `padd q a * p` at successor index.
- **`leadCoef-product`**: `polyCoef (p*q) (n+m) = polyCoef p n * polyCoef q m` under degree bounds.
- **`leadCoef_BigProd`** / **`leadCoef_BigProd1`** / **`leadCoef_pow`**: Leading-coefficient identities for products and powers.
- **`isMonic`**: `p` is monic if there exists `n` with `degree<= p n` and `polyCoef p n = 1`.
- **`polyMap_isMonic`**, **`monomial-isMonic`**: Stability and basic monic example.
- **`monic/=0`**, **`monic-unique`**: Nondegeneracy and uniqueness of the monic-leading index (modulo `0 = 1`).

#### Ring/Algebra/Module Instances

- **`PolyRing`**: `Ring (Poly R)` instance (sum, product via Horner with the helper `*c`, negation, natural-number coefficients).
- **`PolyRing.*c`**: Scalar multiplication of a polynomial by a coefficient.
- **`PolyRing.zro_*c`**, **`PolyRing.ide_*c`**, **`PolyRing.*c-rdistr`**, **`PolyRing.*c-ldistr`**, **`PolyRing.*c-assoc`**, **`PolyRing.*c-comm-left`**, **`PolyRing.*c_*`**, **`PolyRing.*c_negative`**, **`PolyRing.padd0_*-comm`**: Algebraic properties of `*c`.
- **`padd-expand`**: `padd p e = p * X + e` where `X = padd 1 0`.
- **`PolyAlgebra`**: `CAlgebra R` instance over a commutative ring.
- **`PolyRingWith#`**, **`PolyCRingWith#`**: `Ring.With#` / `CRing.With#` instances using a recursive apartness `#0`.
- **`PolyRingWith#.#0_*c-left`**, **`PolyRingWith#.#0_*c-right`**: Apartness behavior of scalar action.
- **`PolyDomain`**, **`PolyIntegralDomain`**: Domain / integral-domain instances over corresponding base rings.
- **`PolyStrictDomain`**, **`PolyStrictIntegralDomain`**: Strict-domain analogues with the `zeroProduct` lemma.
- **`PolyDecRing`**, **`PolyDecCRing`**, **`PolyDecDomain`**, **`PolyDecIntegralDomain`**: Decidable-equality versions.
- **`padd=pzero`**: `padd p a = pzero -> p = pzero ∧ a = 0`.

#### Division and Roots

- **`poly-root-div`**: If `polyEval p a = 0` then `a` divides `polyCoef p 0` (with explicit witness from `polyShift`).
- **`polyDivision`**: Generalized polynomial division with leading-coefficient powers as multipliers; returns quotient `q`, remainder `r`, and the identity `(polyCoef g n)^k *c f = q*g + r` with `degree< r n`.
- **`monicPolyDivisionContr`** / **`monicPolyDivision-unique`** / **`monicPolyDivision`**: Existence and uniqueness of division by a monic polynomial.
- **`monicPolyDivisionContr.aux`**: Cancellation: if `q*g` has degree less than that of monic `g`, then `q = 0`.
- **`degree-monic-reduce`** / **`degree-monic-reduce.aux`**: Degree reduction when multiplying by a linear monic.
- **`degree1PolyDivision`** / **`degree1PolyDivision-unique`**: Division by `X - a` with remainder `polyEval f a`.
- **`rootDiv`**: Quotient `f / (X - a)` (the witness from `degree1PolyDivision`).
- **`rootDiv_*`**: `f = rootDiv f a * (X - a)` whenever `polyEval f a = 0`.
- **`rootPolyDivision-unique`**: Uniqueness of the linear divisor.
- **`rootPolyDivision_degree`**: Dividing out a root drops the degree by one.
- **`polyInv`** / **`polyInv.coefInv`**: An invertible polynomial in a strict domain is a constant invertible.
- **`polyDiv`**: Mutual divisibility forces the inverses to be inverse constants (or both zero).
- **`polyDiv-linear`**: If `p*q` is a product of linear factors `(X - l_j)`, then either `q` has a root among the `l_j` or `q` is a unit.
- **`field-toMonic`**: Over a discrete field, normalize a nonzero polynomial to a monic by scaling by the inverse of its leading coefficient.

#### Decidable Degree and Lead Coefficient

- **`degree`**: Decidable degree (returns `0` for `pzero` and constants).
- **`degree_degree<`**, **`degree<_degree`**, **`degree_degree<=`**, **`degree<=_degree`**: Translations to/from the predicate-style bounds.
- **`degree_polyMap`**: `degree` is preserved under injective ring-hom coefficient maps.
- **`monic-coef`**: Leading coefficient of a monic polynomial at its `degree` is `1`.
- **`leadCoef`**: Decidable leading coefficient.
- **`leadCoef_polyCoef`**: `leadCoef p = polyCoef p (degree p)`.
- **`leadCoef=0-lem`**: `leadCoef p = 0 -> p = 0`.
- **`leadCoef_monomial`**, **`degree_monomial`**: Behavior on monomials.
- **`degree=0-lem`**: A polynomial of degree `0` equals the constant given by its lead coefficient.
- **`degree_padd`**: `degree (padd p a) = suc (degree p)` for nonzero `p`.
- **`degree_+`**, **`leadCoef_+`**: Behavior of degree and lead coefficient under addition with strictly smaller-degree summand.
- **`degree_*c`** / **`degree_*c.*c-nonZero`**: Degree under scalar multiplication in a decidable domain.
- **`degree_*`** / **`degree_*.degree_<`**: Multiplicativity of degree on a decidable domain.
- **`degree_*_=`**: Degree of a known product.
- **`leadCoef_*c`**, **`leadCoef_*`**: Multiplicativity of the lead coefficient.

#### Polynomial Action on Modules

- **`polyModule`**: Given a linear endomorphism `f : LinearMap U U` of an `R`-module `U`, equips `U` with a `PolyRing R`-module structure where `X` acts as `f`.
- **`polyModule.poly_func`**: Iteratively applies `f` and adds the scaled term, evaluating a polynomial as an endomorphism.
- **`polyModule.poly_func_zro`**: Polynomial action on zero is zero.
- **`polyModule.poly_func_poly-*c`**: Compatibility of the action with scalar multiplication of the polynomial.
- **`polyModule.finitelyGenerated`**: Finite generation transfers from `U` (as `R`-module) to `U` (as `R[X]`-module via `f`).
