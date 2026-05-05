### Algebra.Monoid.GCD

Greatest common divisors, GCD monoids, the divisibility quotient, and coprimality in commutative monoids.

#### GCD Structure

- **`GCD`**: Class of a GCD `res` for two elements `val1 val2 : M` in a `CMonoid`, with divisibility witnesses `res|val1`, `res|val2` and the universal property `res-univ` that any common divisor divides `res`.
- **`GCD.~-stable`**: Transports a GCD along mutual divisibility `z|z'`/`z'|z` to produce a GCD with replaced `res`.

#### GCD Monoids

- **`GCDMonoid`**: Extends `CMonoid` with `isGCD` (existence of a GCD for any pair, truncated) and `gcd-ldistr` (left-distributivity: multiplying by `c` preserves GCDs).
- **`GCDMonoid.gcd-ldistr_cancel`**: Constructive proof that for a regular element `c`, multiplying a GCD by `c` yields a GCD of the products (used to derive `gcd-ldistr` in cancellative settings).
- **`CancelGCDMonoid`**: Extends `GCDMonoid` and `CancelCMonoid`; `gcd-ldistr` is derived automatically from cancellation.
- **`UnitlessMonoid`**: Extends `CancelCMonoid` with `uniqueUnit`: every invertible element equals `ide`.
- **`UnitlessGCDMonoid`**: Extends `UnitlessMonoid` and `CancelGCDMonoid`.

#### Divisibility Quotient

- **`DivQuotient`**: The quotient `M / ~` of a `CMonoid` by the associates equivalence (mutual divisibility).
- **`DivQuotient.DivPreoder`**: The preorder on `M` with `x <= y` iff `y | x` (divisibility, reversed).
- **`~`**: Associates relation derived from the divisibility preorder.
- **`inD`**: Quotient embedding `M -> DivQuotient M`.
- **`make~`**: Reflects equality in `DivQuotient` back to the `~` relation.
- **`regular_equivalent-associates`**: For a regular `x`, mutual divisibility with `y` produces an explicit `associates` (invertible witness).
- **`equivalent-associates`**: In a `CancelCMonoid`, `x ~ y` implies `associates x y`.
- **`DivQuotientMonoid`**: Instance making `DivQuotient M` an `OrderedCMonoid` with multiplication `*'` lifted from `M`.
- **`inDHom`**: The monoid homomorphism `M -> DivQuotientMonoid M`.
- **`inv~ide`**: Every invertible element is associate to `ide`.
- **`div-to~`**: Lifts an `LDiv x y` in `M` to an `LDiv (inD x) (inD y)`.
- **`div-from~'`**: Reflects an `LDiv` in the quotient back to a truncated `LDiv` in `M`.
- **`div-from~`**: Reflects an `LDiv` in the quotient back to an `LDiv` in `M` (for cancellative `M`).
- **`DivQuotientCancelMonoid`**: For `CancelCMonoid M`, the quotient is a `UnitlessMonoid`.
- **`map`**: Functorial action of a `MonoidHom` on divisibility quotients.
- **`DivQuotientGCDMonoid`**: For `CancelGCDMonoid M`, the quotient is a `UnitlessGCDMonoid`.
- **`gcd-to~`**: Lifts a GCD in `M` to a GCD in `DivQuotient M`.
- **`gcd-from~`**: Reflects a GCD in `DivQuotient M` back to a GCD in `M`.
- **`elemDivChain`**: Transfers the divisibility-chain (DCC) property from the quotient back to `M`.

#### GCD Lemmas

- **`div_gcd`**: When `a | b`, `a` itself is a GCD of `a` and `b`.
- **`gcd_reduced=1`**: Dividing a GCD's two values by the GCD yields coprime quotients (GCD equal to `1`).
- **`gcd-isUnique`**: Any two GCDs of the same pair are associates (in a `CancelCMonoid`).

#### Coprimality

- **`IsCoprime`**: `x` and `y` are coprime when every common divisor is invertible.
- **`IsCoprime.=>gcd`**: Coprimality implies `ide` is a GCD of `x` and `y`.
- **`IsCoprime.<=gcdInv`**: If a GCD of `x`, `y` is invertible, then `x` and `y` are coprime.
- **`IsCoprime.<=gcd`**: If `ide` is a GCD of `x` and `y`, they are coprime.
- **`IsCoprime.factor-left`**: Coprimality with `z` is preserved when replacing `y` with a divisor `x | y`.
- **`IsCoprime.factor-right`**: Coprimality with `x` is preserved when replacing `z` with a divisor `y | z`.
- **`IsCoprime.swap`**: Symmetry of coprimality.
- **`IsCoprime.IsCoprime_ide-left`**, **`IsCoprime.IsCoprime_ide-right`**: `1` is coprime to every element.
- **`IsCoprimeArray`**: Generalization of coprimality to a finite array of elements: every common divisor of all entries is invertible.
