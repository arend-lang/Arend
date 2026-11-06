### Algebra.Ring.Noetherian.Examples

Closure properties of Noetherian rings: quotients, localizations and finite products.

Noetherianity is the `IsNoetherian` property (a bar-induction formulation of the ascending chain condition on finitely generated ideals). The module shows that it is inherited by surjective images, by localizations and by binary products of commutative rings.

#### Closure under constructions

- **`SurjNoetherian`**: if `R` is Noetherian and `f : RingHom R S` is surjective, then `S` is Noetherian.
- **`LocalizationNoetherian`**: if the base ring `L.B` of a ring localization `L` is Noetherian, then the localization `L.L` is Noetherian.
- **`ProductNoetherian`**: the product `ProductCRing R S` of two Noetherian rings is Noetherian.
