### Topology.Locale.LocaleColimits

Colimits of locales: the initial locale and pushouts.

The module constructs two colimit-type objects in the category of locales and locale homomorphisms (`LocaleHom`). `InitialLocale` is the locale on the one-element type with the unique homomorphism into every locale. `PushoutLocale f g` is the locale of compatible pairs of elements, built from a span of locale homomorphisms, equipped with the two injections and the commutativity of the resulting square.

#### Initial locale

- **`InitialLocale`**: The locale on the type `\Sigma` (a single element), in which all elements are equal and the order is trivial.
- **`InitialLocale.initialMap`**: The locale homomorphism from `InitialLocale` to any locale `L`.
- **`InitialLocale.initialMap-unique`**: Any two locale homomorphisms from `InitialLocale` to `L` are equal.

#### Pushouts

- **`PushoutLocale`**: For homomorphisms `f : LocaleHom L M` and `g : LocaleHom L K`, the locale whose elements are triples `(x, y, f x = g y)` with `x : M` and `y : K`, ordered componentwise, with meets and joins computed componentwise.
- **`PushoutLocale.pinl`**: The homomorphism from `M` to `PushoutLocale f g` projecting to the first component.
- **`PushoutLocale.pinr`**: The homomorphism from `K` to `PushoutLocale f g` projecting to the second component.
- **`PushoutLocale.pushoutCoh`**: The square commutes: `pinl ∘ f = pinr ∘ g`.
