### Algebra.Monoid.AbLocalization

Localization of abelian (additively written) monoids at a submonoid, and the Grothendieck group construction.

This module constructs the localization of an `AbMonoid` `M` at a `SubAddMonoid` `S` of "denominators" by quotienting pairs `(x, y)` with `y ∈ S` under the equivalence `(x, y) ~ (x', y') ⟺ x + y' = x' + y`, using `Quotient` to form the underlying type and lifting the monoid operation pointwise on representatives. (The multiplicative version for `CMonoid` is in `Algebra.Monoid.Localization`.) As a key application, localizing an `AbMonoid` at the maximal submonoid (everything) yields the **Grothendieck group**, freely completing an additive monoid into an abelian group by formally adjoining negatives.

#### Additive Localization

- **`LocAbType`**: The localization of an `AbMonoid` `M` at a `SubAddMonoid` `S`, quotienting pairs `(x, y)` by `a₁ + b₂ = b₁ + a₂`.
- **`LocAbType.SType`**: Pairs `(x, y, Sy)` representing fractions `x - y` with denominator `y ∈ S`.
- **`LocAbType.~`**: The cross-addition equivalence on pairs: `(a₁, a₂) ~ (b₁, b₂) ⟺ a₁ + b₂ = b₁ + a₂`.
- **`LocAbType.inl~`**: Coerces a representative pair into the quotient.
- **`LocAbType.~-lequiv`**: Equivalent representatives give equal elements of `LocAbType S`.
- **`LocAbType.~-lequiv-right`**: Variant allowing equality after adding some `c ∈ S`: `a.1 + b.2 + c = b.1 + a.2 + c` gives `inl~ a = inl~ b`.
- **`LocAbMonoid`**: Abelian monoid instance on `LocAbType S` with `0 = (0, 0)` and `(x, y) + (x', y') = (x + x', y + y')`.

#### Grothendieck Group

- **`MaxLocAbType`**: The localization of `M` at the maximal submonoid `SubAddMonoid.max` (i.e., every element is a denominator).
- **`GrothendieckAbGroup`**: Abelian group structure on `MaxLocAbType M`, extending `LocAbMonoid SubAddMonoid.max`. The negation `-(x, y) = (y, x)` swaps numerator and denominator, exhibiting the Grothendieck group as the universal group completion of `M`.
