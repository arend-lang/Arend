### Algebra.Group.Product

Product group structures on pairs, lifting group operations componentwise from the factors.

#### Group Instances

- **`ProductGroup`**: Direct product of two groups `G H : Group`, extending `ProductMonoid` with componentwise `inverse` and componentwise inverse laws.
- **`ProductAddGroup`**: Direct product of two additive groups `A B : AddGroup`, extending `ProductAddMonoid` with componentwise `negative` and its laws.
- **`ProductCGroup`**: Direct product of two commutative groups `G H : CGroup`, combining `ProductGroup` with `ProductCMonoid`.
- **`ProductAbGroup`**: Direct product of two abelian groups `A B : AbGroup`, combining `ProductAddGroup` with `ProductAbMonoid`.
