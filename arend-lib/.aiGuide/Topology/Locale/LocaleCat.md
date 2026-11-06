### Topology.Locale.LocaleCat

- **`FrameCat`**: Category of locales with frame homomorphisms; identity, composition, and univalence supplied.
- **`FrameCat.equiv_iso`**: Promotes a frame homomorphism that is a `QEquiv` of underlying sets to an iso in `FrameCat`.
- **`FrameReflectiveSubcat`**: Reflective inclusion of `FrameCat` into `PreorderSiteCat`.
- **`LocaleCat`**: Opposite category of `FrameCat`, in which morphisms point in the geometric (continuous-map) direction.
- **`PreorderSiteCat`**: Category of preorder sites with `PreorderSiteHom` as morphisms (univalent).
- **`LocaleCartesianPrecat`**: `LocaleCat` is cartesian: the terminal object is the discrete locale on a point and binary products are `ProductLocale`.
- **`discreteLocaleFunctor`**: Functor `SetCat -> LocaleCat` sending a set to its powerset locale `discreteLocale` and a function to inverse-image.
- **`localeEqualizer`**: Equalizer of two locale homomorphisms `f g : LocaleHom L M`, given by the sublocale of `L` determined by the nucleus that is the join of all nuclei `j` with `j (f x) = j (g x)` for all `x`.
- **`regular_surj`**: Regular monos in `LocaleCat` are surjective frame homomorphisms.
- **`surj_regular`**: Surjective frame maps are regular monos in `LocaleCat`.
- **`surj_equiv`**, **`surj_equiv.map-comm`**: For surjective `f`, the factor through the image is an iso in `FrameCat`; `map-comm` shows it commutes with `image.map`.
- **`dense_closed_ofs`**: Orthogonal factorization system on `LocaleCat`: dense maps vs. closed surjections.
- **`sdense_wclosed_ofs`**: Orthogonal factorization system on `LocaleCat`: strongly dense vs. weakly closed maps.
