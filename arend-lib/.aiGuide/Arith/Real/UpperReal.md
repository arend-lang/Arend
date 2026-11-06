### Arith.Real.UpperReal

Upper Dedekind cuts of the rationals, providing a constructive representation of (possibly infinite) upper reals and ordinary upper reals.

An `ExUpperReal` is presented by a rounded, upward-closed predicate `U : Rat -> \Prop` — the set of rational strict upper bounds of the represented value; allowing `U` to be empty admits `+∞`. The ordinary `UpperReal` extends this with an inhabitedness condition `U-inh`, ensuring the value is finite. Arithmetic and order operations are defined on the level of upper-cut predicates: addition, multiplication, meet, and join are characterized by existence of rational witnesses below the target rational, and `<=` is contravariant predicate inclusion. Multiplication restricts to positive witnesses, so distributivity and identity laws typically require non-negativity hypotheses; boundedness `IsBounded` (witnessed by a rational lower bound, equivalently `∃ U`-style content) is needed for cancellation and for `0 * x = 0`. The structure assembles into a biordered lattice ordered abelian monoid for `+` and a commutative semigroup for `*`, with `fromRat` an additive monoid homomorphism. A separate `IsLocated` predicate captures decidable comparison up to arbitrary rational precision.

#### Core Record

- **`ExUpperReal`**: Record with field `U : Rat -> \Prop` together with `U-closed` (upward closed under `<`) and `U-rounded` (every member has a smaller member in `U`). Represents a (possibly `+∞`) upper real.
- **`ExUpperReal.U_<=`**: Upward closure under non-strict `<=`, derived from `U-closed` + `U-rounded`.
- **`ExUpperReal.IsBounded`**: `∃ U`, i.e. there exists a rational strict upper bound (the upper real is finite).
- **`ExUpperReal.fromRat`**: Coercion `Rat -> ExUpperReal` sending `x` to the predicate `x <`.
- **`ExUpperReal.fromRat-inj`**: Injectivity of `fromRat`.

#### Arithmetic on `ExUpperReal`

- **`ExUpperReal.+`**: Addition, with `U` defined as `∃ (b : x.U) (c : y.U) (b + c < a)`.
- **`ExUpperReal.+_U`**, **`+_U_<=`**: Membership characterizations of `(x + y).U` with strict and non-strict witnesses.
- **`ExUpperReal.+-rat`**: `fromRat x + fromRat y = fromRat (x + y)`.
- **`ExUpperReal.*`**: Multiplication via positive rational witnesses: `∃ (b : x.U) (b > 0) (c : y.U) (c > 0) (b * c < a)`.
- **`ExUpperReal.*_U`**, **`*_U_<=`**, **`*_U_<`**: Three equivalent membership characterizations of `(x * y).U`, including a non-negative-rational-bound form.
- **`ExUpperReal.*-rat`**: `fromRat x * fromRat y = fromRat (x * y)` for non-negative `x, y`.

#### Order and Lattice on `ExUpperReal`

- **`ExUpperReal.<=`**: Defined as `∀ {b : y.U} (x.U b)` — predicate inclusion, contravariant in `U`.
- **`ExUpperReal.<=-rat`**, **`<_<=`**: Compatibility of `<=` with rational order and with rational membership in `U`.
- **`ExUpperReal.<=_+-char`**: Distributing a bound `x <= y + z` over rational witnesses for `y` and `z`.
- **`ExUpperReal.meet`**: Pointwise `||` of upper-cut predicates (the smaller real).
- **`ExUpperReal.join`**: Pointwise `\Sigma` (intersection) of upper-cut predicates (the larger real).
- **`ExUpperReal.meet_U`**, **`join_U`**: Membership characterizations.
- **`ExUpperReal.join-bounded`**: `join` preserves `IsBounded`.
- **`real_meet_U`**: From `x.U a` and `x.U b` deduce `x.U (a ∧ b)`.

#### Algebraic Instances

- **`ExUpperRealPointed`**: `Pointed` instance with `ide = fromRat 1`.
- **`ExUpperRealAbMonoid`**: `BiorderedLatticeAbMonoid` instance: zero `fromRat 0`, addition, lattice operations, and the strict order `<` (defined via `∃ (q : x.U) (q <= y)`).
- **`ExUpperRealAbMonoid.<`**: Strict order: there is a rational upper bound of `x` that is `<= y`.
- **`ExUpperRealAbMonoid.<-rat`**: `x < y ↔ x.U y` for rational `y`.
- **`ExUpperRealAbMonoid.zro<ide`**: `0 < 1`.
- **`ExUpperRealAbMonoid.<_+`**, **`<_+-left`**, **`<_+-right`**: Strict monotonicity of `+`.
- **`ExUpperRealAbMonoid.<=_+-cancel-left`**, **`<=_+-cancel-right`**: Additive cancellation for `<=`, requiring boundedness and a positive rational lower bound on the cancelled side; uses internal `step`/`steps` lemmas to iterate an `eps`-shift argument.
- **`ExUpperRealAbMonoid.*n_U`**, **`*n_finv`**: Membership in the `n`-fold sum `n *n x` characterized via `x.U` and rational scaling/inverse-scaling.
- **`rat-upperReal`**: `AddMonoidHom RatField ExUpperRealAbMonoid` given by `fromRat`.
- **`ExUpperRealSemigroup`**: `CSemigroup` instance with the multiplication above.

#### Multiplicative Lemmas

- **`ExUpperRealSemigroup.ide-left_<=`**, **`ide-left`**, **`ide-right`**: Identity for `*` (the equalities require `0 <= x`).
- **`ExUpperRealSemigroup.<=_*`**, **`<_*`**: Monotonicity and strict monotonicity of `*` (the strict version requires `x, y >= 0`).
- **`ExUpperRealSemigroup.*_join`**: `x * y = (x ∨ 0) * (y ∨ 0)` — multiplication ignores the negative part of upper reals.
- **`ExUpperRealSemigroup.<_*_U-left`**, **`<_*_U-right`**: Membership of products `(x * z).U (y * z)` from `x.U y` and positive rational scalars.
- **`ExUpperRealSemigroup.<_*-left`**, **`<_*-right`**, **`<_*-left'`**, **`<_*-right'`**: Strict monotonicity of multiplication by a positive rational, in primed and unprimed forms differing in `> 0` vs `>= 0` hypotheses.
- **`ExUpperRealSemigroup.*_>=0`**: Products are non-negative.
- **`ExUpperRealSemigroup.ldistr_<=`**, **`rdistr_<=`**: Sub-distributivity (always holds).
- **`ExUpperRealSemigroup.ldistr`**, **`rdistr`**: Full distributivity, given non-negativity of the relevant arguments.
- **`ExUpperRealSemigroup.zro_*-left`**, **`zro_*-right`**: `0 * x = 0` and `x * 0 = 0`, requiring `x.IsBounded`.
- **`ExUpperRealSemigroup.*n_*_<=`**, **`*n_*`**: Relating natural-number scaling `n *n x` and the product `n * x`; equality requires boundedness.
- **`ExUpperRealSemigroup.<_*-positive`**: `0 < x` and `0 < y` implies `0 < x * y`.

#### Division and Inverse Rotation

- **`ExUpperRealSemigroup.div-lb`**: For positive rational `a` and positive `b`, returns a positive `c` with `a * c <= b` (constructed as `finv a * b`).
- **`ExUpperRealSemigroup.div-lb-rat`**: Existence of a rational `c > 0` with `a * c <= b`.
- **`ExUpperRealSemigroup.finv_<=-rotate-right`**, **`finv_<-rotate-right`**, **`finv_<-rotate-left`**: Rotations of inequalities through multiplication by `finv a` / `a`.

#### Powers and Square Bounds

- **`ExUpperRealSemigroup.pow`**: Natural-number powers.
- **`ExUpperRealSemigroup.rat-pow`**: `pow (fromRat x) n = fromRat (x^n)` for `x >= 0`.
- **`ExUpperRealSemigroup.pow_<=`**, **`pow_>=0`**: Monotonicity and non-negativity of `pow`.
- **`ExUpperRealSemigroup.square_<=`**, **`square<=1`**: From `x * x <= q * q` (with `q >= 0`) deduce `x <= q`; specialization to `q = 1`.
- **`ExUpperRealSemigroup.square-bound-div`**: From `x * x <= q * x` deduce `x <= q`, via a halving-step iteration (`step`, `aux`).
- **`ExUpperRealSemigroup.*n-bounded`**: Boundedness propagates through `*n`.

#### `UpperReal` (Inhabited Case)

- **`UpperReal`**: Extends `ExUpperReal` with `U-inh : ∃ U`, i.e. the upper real is finite.
- **`UpperReal.natBounded`**: There is a natural number bounding the real from above.
- **`UpperReal.U-inh_>0`**: There is a positive rational upper bound.
- **`UpperReal.IsLocated`**: For all rationals `a < b`, either `a < c` for some `c : U` or `U b` holds — constructive locatedness.
- **`UpperReal.fromRat`**: Coercion `Rat -> UpperReal`, lifting `ExUpperReal.fromRat`.
- **`UpperReal.ex-ext`**: Extensionality reducing equality of `UpperReal`s to equality of underlying `ExUpperReal`s.
- **`UpperRealSemigroup`**: `CSemigroup` instance on `UpperReal` whose multiplication restricts the `ExUpperReal` product to inhabited cuts.
- **`UpperRealSemigroup.*`**: Multiplication on `UpperReal`, lifting `ExUpperReal.*` and supplying `U-inh`.
- **`UpperRealSemigroup.*_U`**: Membership characterization of the product.
- **`UpperRealSemigroup.*-ex`**: The `UpperReal` product agrees with the `ExUpperReal` product after coercion.
