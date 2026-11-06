### Algebra.Group.Product

Direct product constructions for groups, building componentwise group structures from pairs of groups.

This module extends the monoid product constructions from `Algebra.Monoid.Product` by adding inverse operations componentwise. Each instance layers the appropriate inverse (multiplicative or additive) on top of the corresponding product monoid, and commutative variants combine the group structure with the commutative monoid structure. The pattern mirrors the standard categorical product: operations and inverses act independently on each factor.

#### Multiplicative Group Products

- **`ProductGroup`**: Direct product of two groups `G H : Group`. Extends `ProductMonoid` with componentwise `inverse`, yielding a `Group` instance on pairs.
- **`ProductCGroup`**: Direct product of two commutative groups `G H : CGroup`. Combines `ProductGroup` with `ProductCMonoid` to produce a `CGroup` instance.

#### Additive Group Products

- **`ProductAddGroup`**: Direct product of two additive groups `A B : AddGroup`. Extends `ProductAddMonoid` with componentwise `negative`, yielding an `AddGroup` instance on pairs.
- **`ProductAbGroup`**: Direct product of two abelian groups `A B : AbGroup`. Combines `ProductAddGroup` with `ProductAbMonoid` to produce an `AbGroup` instance.
