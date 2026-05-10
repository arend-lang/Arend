### Algebra.Monoid.GCD

Greatest common divisors, coprimality, and the divisibility quotient for commutative monoids.

This module develops GCDs as a structure (witness plus universal property) rather than as an operation, then layers progressively stronger algebraic settings on top: `GCDMonoid` requires existence of GCDs and left-distributivity over multiplication; `CancelGCDMonoid` adds cancellativity (which makes distributivity automatic); `UnitlessMonoid` collapses the unit group, making GCDs literally unique; and `UnitlessGCDMonoid` combines both so that `gcd` becomes a genuine binary function and divisibility is a join-semilattice. The `DivQuotient` construction quotients a `CMonoid` by the associate relation (mutual divisibility), producing a canonical unitless monoid where each equivalence class of associates becomes a single element — this transports GCD theory between an arbitrary `CancelGCDMonoid` and its unitless quotient. Coprimality is defined directly via the universal property (any common divisor is invertible) and shown equivalent to having `1` as a GCD.

#### GCD Record

- **`GCD`**: A record on `{M : CMonoid}` carrying values `val1`, `val2` and a witness `res` together with `LDiv` proofs that `res` divides both, plus `res-univ` expressing the universal property. Coerces to its `res` field.
- **`GCD.swap`**: Symmetry: a `GCD val1 val2` gives a `GCD val2 val1`.
- **`GCD.reduce`**: Given regularity of `res`, the cofactors `val1/res` and `val2/res` are coprime (have GCD `1`).
- **`GCD.~-stable`**: GCDs are stable under replacing `res` with any associate (mutually dividing element).

#### GCDMonoid Class

- **`GCDMonoid`**: Extends `CMonoid` with `isGCD` (truncated existence of a GCD for any pair) and `gcd-ldistr` (left-distributivity of GCDs over multiplication, up to existence).
- **`gcd-rdistr`**: Right version of distributivity, derived from `gcd-ldistr` and commutativity.
- **`gcd_*-comm`**: `gcd a (gcd a b * c) = gcd a (b * c)` up to truncated existence.
- **`gcd_*_div`**: If `a | b*c` and `gcd a b` exists, then `a | gcd(a,b) * c`.
- **`coprime_*_div`**: If `a | b*c` and `a, b` are coprime, then `a | c` (Euclid's lemma).
- **`gcd_pow_div`**: If `a | b^n` and `a, b` are coprime, then `a | b`.
- **`IsCoprime_*-right` / `IsCoprime_*-left`**: Coprimality is preserved under products.
- **`IsCoprime_pow-left` / `IsCoprime_pow-right`**: Coprimality is preserved under powers.
- **`IsCoprime_BigProd-right` / `IsCoprime_BigProd-left`**: Coprimality with a finite product reduces to coprimality with each factor.
- **`coprime_*_div-left`**: If `d, e` are coprime and both divide `a`, then `d * e | a`.
- **`coprime_BigProd_div-left`**: Pairwise-coprime divisors of `a` jointly multiply to a divisor of `a`.
- **`split-equiv`**: Any divisor of a product `b*c` factors (up to associates) as `a1 * a2` with `a1 | b`, `a2 | c`.
- **`split-regular`**: For a regular `a | b*c`, the splitting is an actual equality `a = a1 * a2`.
- **`gcd-ldistr_cancel`**: Helper showing that if `c` is regular, `c * z` is a GCD of `c*x, c*y` whenever `z` is a GCD of `x, y`.

#### CancelGCDMonoid Class

- **`CancelGCDMonoid`**: Extends `GCDMonoid` and `CancelCMonoid`; `gcd-ldistr` is automatic via `gcd-ldistr_cancel`.
- **`gcd_*_div`**, **`coprime_*_div`**, **`gcd_pow_div`**: Cancellative versions returning honest `LDiv` rather than `TruncP`.
- **`div_unit`**: Decidability of divisibility is equivalent to decidability of invertibility.

#### UnitlessMonoid Class

- **`UnitlessMonoid`**: Extends `CancelCMonoid` with `uniqueUnit`: every invertible element equals `ide`.
- **`div-eq`**: Mutual divisibility implies equality (associates collapse).
- **`GCD-isProp`**: `GCD x y` is a proposition.
- **`gcd-isUnique`**: Any two GCDs of the same pair have equal `res`.

#### UnitlessGCDMonoid Class

- **`UnitlessGCDMonoid`**: Extends `UnitlessMonoid` and `CancelGCDMonoid`; here `gcd` becomes a function.
- **`gcdC`**: Untruncated `GCD x y` (using its propositionality).
- **`gcd`**: The GCD as a binary operation `E -> E -> E`.
- **`gcd_~`**: Any GCD witness equals the canonical `gcd x y`.
- **`gcd_*-left` / `gcd_*-right`**: `c * gcd a b = gcd (c*a) (c*b)` and the right-multiplied version.
- **`DivLattice`**: The divisibility join-semilattice on `E` with `x <= y := LDiv y x` and join `gcd`.
- **`gcd_*-comm`**: `gcd a (b * c) = gcd a (gcd a b * c)`.

#### DivQuotient

- **`DivQuotient`**: The set quotient of a `CMonoid` by the associate relation, defined as `PreorderC` of the divisibility preorder.
- **`DivPreoder`**: The preorder where `x <= y` means `y | x` (truncated).
- **`~`**: The associate relation (mutual divisibility).
- **`inD`**: Embedding `M -> DivQuotient M`.
- **`make~`**: Equality `inD x = inD y` implies `x ~ y`.
- **`regular_equivalent-associates`**: For regular `x`, mutual divisibility upgrades to genuine associate (invertible factor).
- **`equivalent-associates`**: In a `CancelCMonoid`, `~` coincides with being associates.
- **`DivQuotientMonoid`**: The quotient is an `OrderedCMonoid` with multiplication lifted from `M`.
- **`*'`**: The lifted multiplication on `DivQuotient M`.
- **`inDHom`**: `inD : M -> DivQuotientMonoid M` as a `MonoidHom`.
- **`inv~ide`**: Every invertible element is associate to `ide`.
- **`div-to~` / `div-from~'` / `div-from~`**: Divisibility transfers between `M` and its quotient (the cancellative version is untruncated).
- **`DivQuotientCancelMonoid`**: For cancellative `M`, the quotient is a `UnitlessMonoid`.
- **`map`**: A `MonoidHom M -> N` lifts to a hom of quotients.
- **`DivQuotientGCDMonoid`**: For a `CancelGCDMonoid`, the quotient is a `UnitlessGCDMonoid`.
- **`gcd-to~` / `gcd-from~`**: GCDs transfer between `M` and its quotient.
- **`elemDivChain`**: Divisibility chains in the quotient lift to divisibility chains in `M`.

#### Standalone Lemmas

- **`div_gcd`**: If `a | b`, then `a` is itself the GCD of `a` and `b`.
- **`gcd_reduced=1`**: In a `CancelCMonoid`, the cofactors of any GCD are coprime.
- **`gcd-isUnique`**: In a `CancelCMonoid`, any two GCDs of the same pair are associates.

#### Coprimality

- **`IsCoprime`**: `IsCoprime x y` means every common divisor of `x` and `y` is invertible.
- **`IsCoprime.=>gcd`**: Coprimality implies `1` is a GCD.
- **`IsCoprime.<=gcdInv`**: An invertible GCD witnesses coprimality.
- **`IsCoprime.<=gcd`**: Having `1` as GCD implies coprimality.
- **`IsCoprime.factor-left` / `factor-right`**: Coprimality descends along divisibility on either side.
- **`IsCoprime.swap`**: Symmetry of coprimality.
- **`IsCoprime.IsCoprime_ide-left` / `IsCoprime_ide-right`**: `1` is coprime to everything.
- **`IsCoprimeArray`**: Pairwise-style coprimality of an element with a whole array, via the universal property over `DArray` of divisors.
