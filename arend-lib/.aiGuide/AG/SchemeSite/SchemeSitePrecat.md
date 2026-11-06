### AG.SchemeSite.SchemeSitePrecat

Categories of scheme sites: the precategory of scheme sites with morphisms `SchemeSiteHom`, its subcategory of saturated scheme sites, and the auxiliary precategory with `SchemeSitePrehom`.

The module packages the morphisms of `AG.SchemeSite.SchemeSiteHom` into precategories. `SchemeSitePrehomPrecat` has the unsaturated prehomomorphisms; `SchemeSitePrecat` has the saturated morphisms (`SchemeSiteHom`, with composition followed by saturation `satPrehom`), and its associativity and unit laws are derived from those of `SchemeSitePrehomPrecat` using idempotence of `satPrehom`. `SatSchemeSitePrecat` is the full subprecategory on saturated scheme sites; it is the source of `SchemeSiteFunctor` and the target of `SpecFunctor`.

#### Precategories

- **`SchemeSitePrehomPrecat`**: The precategory of scheme sites and `SchemeSitePrehom`s, with `SchemeSitePrehom.id` and `SchemeSitePrehom.∘`.
- **`SchemeSitePrecat`**: The precategory of scheme sites and `SchemeSiteHom`s, with `SchemeSiteHom.id` and `SchemeSiteHom.∘`.
- **`SatSchemeSitePrecat`**: The full subprecategory of `SchemeSitePrecat` on the saturated scheme sites `SatSchemeSite`.
