### AG.SchemeSite.SchemeEquiv

The functor from saturated scheme sites to locally ringed locales, shown to be fully faithful, and the comparison of a scheme with its maximal scheme site.

`SchemeSiteFunctor` sends a saturated scheme site `S` to the scheme `S.toScheme` and a morphism of sites to the induced morphism of locally ringed locales. It is a functor `SatSchemeSitePrecat -> LocallyRingedLocaleCat`, fully faithful because morphisms of sites `T -> S` correspond to morphisms of locally ringed locales `T.toScheme -> S.toScheme` (through `LRLSchemeSiteHom`). `LRL->SchemeSiteHom` shows that every scheme `S` is recovered from its maximal scheme site `maxSchemeSite S`, whose locale is isomorphic to `S` (`LRL->SchemeSiteHom.isIso`).

#### Schemes and scheme sites

- **`SchemeSiteFunctor`**: The fully faithful functor `SatSchemeSitePrecat -> LocallyRingedLocaleCat` sending a saturated scheme site `S` to the scheme `S.toScheme`.
- **`LRL->SchemeSiteHom`**: For a scheme `S`, the `LRLSchemeSiteHom` from `S` to its maximal scheme site `maxSchemeSite S`, with `f*` the embedding of the affine opens; its associated morphism `toLRLHom` is an isomorphism (`LRL->SchemeSiteHom.isIso`), so `S` is reconstructed from `maxSchemeSite S`.
