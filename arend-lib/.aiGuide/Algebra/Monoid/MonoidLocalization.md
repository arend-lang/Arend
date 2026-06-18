### Algebra.Monoid.MonoidLocalization

Localization of commutative monoids at a submonoid, with an additive variant and the Grothendieck group construction.

This module constructs the localization `S⁻¹M` of a commutative monoid `M` at a submonoid `S` of "denominators" by quotienting pairs `(x, y)` with `y ∈ S` under the equivalence `(x, y) ~ (x', y') ⟺ x · y' = x' · y`. The construction is given twice — once multiplicatively for `CMonoid` and once additively for `AbMonoid` — using `Quotient` to form the underlying type and lifting the monoid operation pointwise on representatives. As a key application, localizing an `AbMonoid` at the maximal submonoid (everything) yields the **Grothendieck group**, freely completing an additive monoid into an abelian group by formally adjoining negatives.

#### Multiplicative Localization

- **`LocType`**: The carrier type of the localization `S⁻¹M`, defined as the quotient of `SType S` by the equivalence relation `~`.
- **`LocType.SType`**: Pairs `(x, y, Sy)` representing fractions `x/y` with denominator `y ∈ S`.
- **`LocType.~`**: The cross-multiplication equivalence on fractions: `(a₁, a₂) ~ (b₁, b₂) ⟺ a₁ · b₂ = b₁ · a₂`.
- **`LocType.inl~`**: Coerces a representative pair into the quotient.
- **`LocType.~-lequiv`**: Equivalent representatives give equal elements of `LocType S`.
- **`LocType.~-lequiv-right`**: Variant allowing equality after multiplying by some `c ∈ S`, useful when the submonoid contains zero divisors.
- **`LocMonoid`**: Commutative monoid instance on `LocType S` with `1 = 1/1` and `(x/y) · (x'/y') = (x·x')/(y·y')`.

#### Additive Localization

- **`LocAbType`**: Additive analogue of `LocType` for an `AbMonoid` and a `SubAddMonoid`, quotienting pairs `(x, y)` by `a₁ + b₂ = b₁ + a₂`.
- **`LocAbType.SType`**, **`LocAbType.~`**, **`LocAbType.inl~`**: Additive counterparts of the multiplicative versions.
- **`LocAbType.~-lequiv`**, **`LocAbType.~-lequiv-right`**: Additive equivalence-respecting lemmas.
- **`LocAbMonoid`**: Abelian monoid instance on `LocAbType S` with `0 = (0, 0)` and `(x, y) + (x', y') = (x + x', y + y')`.

#### Grothendieck Group

- **`MaxLocAbType`**: The localization of `M` at the maximal submonoid `SubAddMonoid.max` (i.e., every element is a denominator).
- **`GrothendieckAbGroup`**: Abelian group structure on `MaxLocAbType M`, extending `LocAbMonoid SubAddMonoid.max`. The negation `-(x, y) = (y, x)` swaps numerator and denominator, exhibiting the Grothendieck group as the universal group completion of `M`.
