### Category.Comma

Construction of comma categories from a pair of functors with common codomain.

#### Comma Precategory

- **`commaPrecat`**: Given functors `F : C -> E` and `G : D -> E`, builds the comma precategory `(F ↓ G)`. Objects are triples `(x : C, y : D, F x -> G y)`; morphisms are pairs `(f, g)` making the obvious naturality square commute. Identities, composition, and the category axioms are derived from those of `C`, `D`, and `E`.

#### Forgetful Functors and Functoriality

- **`commaPrecat.leftForget`**: Projection functor `(F ↓ G) -> C` taking `(x, y, a)` to `x`.
- **`commaPrecat.rightForget`**: Projection functor `(F ↓ G) -> D` taking `(x, y, a)` to `y`.
- **`commaPrecat.functor`**: Functoriality of the comma construction in its functor arguments. Given natural transformations `a : F' => F` and `b : G => G'`, produces a functor `(F ↓ G) -> (F' ↓ G')` by whiskering each object morphism `s : F x -> G y` to `b y ∘ s ∘ a x`.

#### Comma Category (Univalent)

- **`commaCat`**: When `C` and `D` are univalent categories (and `E` is any precategory), the comma precategory `commaPrecat F G` is also a univalent category. Univalence is established via `Cat.makeUnivalence`, transporting an iso in the comma category to a path componentwise using the isotoid maps of `C` and `D` together with `Functor.transport_Hom_iso`.
