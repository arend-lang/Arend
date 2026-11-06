### AG.RingedLocale.RingedLocaleCat

The precategory and univalent category of ringed locales, with the forgetful functor to locales.

- **`RingedLocalePrecat`**: Precategory of ringed locales and `RingedLocaleHom`s, with identity `RingedLocaleHom.id` and composition `RingedLocaleHom.∘`; the category laws hold by `idp`.
- **`RingedLocalePrecat.forget`**: Forgetful functor `RingedLocalePrecat -> LocaleCat`.
- **`RingedLocaleCat.fromIso`**: Given a morphism `f` of ringed locales over the identity of `L` whose components `f.f# x` are all isos, gives an iso of sheaves `R2.R -> R1.R` in `VSheafCat CRingCat L`.
- **`RingedLocaleCat`**: The univalent category of ringed locales, with `RingedLocalePrecat` as underlying precategory; univalence is proved by a structure identity principle over `LocaleCat` (an isomorphism of ringed locales is a frame isomorphism together with an isomorphism of the sheaves of rings).
- **`LocallyRingedLocalePrecat`**: Precategory of locally ringed locales and `LocallyRingedLocaleHom`s, with identity `LocallyRingedLocaleHom.id` and composition `LocallyRingedLocaleHom.∘`; `LocallyRingedLocalePrecat.forget` is a faithful functor to `RingedLocalePrecat`.
- **`LocallyRingedLocaleCat`**: The univalent category of locally ringed locales; univalence follows from that of `RingedLocaleCat`, since the forgetful functor is faithful and `LocallyRingedLocaleCat.embedding` is an embedding on objects.
