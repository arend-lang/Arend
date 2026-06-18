### Homotopy.Localization.Separated

Constructs the separated universe associated to a universe of local types, where a type is "separated" if all its identity types are local.

This module implements the standard construction that turns any universe `U` into a new universe `Separated U` whose locality predicate requires all path spaces to be `U`-local. Starting from a reflective universe, it builds the reflection into separated types using the image factorization of the Yoneda-like map `a ↦ LType (a = -)`: surjectivity of the image map combined with locality of each identity type yields the separated localization. The technical core is `pathEquiv`, which identifies the fibers of this map with the localized identity types via a chain of equivalences passing through univalence, function extensionality, and the embedding property of the Yoneda map.

#### Universe Constructions

- **`Separated`**: Given a universe `U`, produces the universe `Separated U` whose local types are those `Z` for which every identity type `z = z'` is `U`-local. The construction is by `\cowith` extending `Universe`.
- **`ReflSeparated`**: Given a reflective universe `U`, produces a reflective universe structure on `Separated U`. The localization of `A` is built as the image of the map `a ↦ LType (a = -)`, with locality of identity types transported across `pathEquiv`.

#### Key Components of the Reflection (in `\where`)

- **`S`**: The family `S a a' := LType (a = a')`, the localized identity type. This is the family whose Yoneda-style image gives the separated localization.
- **`F`**: The map `F a := YImage.dom-map a`, sending each point to its image in the localization of identity types. Its codomain serves as the underlying type of the separated reflection of `A`.
- **`pathEquiv`**: For all `a a' : A`, the equality `(F a = F a') = LType (a = a')`. This is the central technical lemma identifying paths in the image with the localized identity type, established by chaining: embedding-induced equivalence on `cod-map`, symmetry of equality, function extensionality, univalence pointwise, and a `localizationFactorEmbedding` step using `piLocal` and `equivLocal`.
- **`separatedLocalization`**: Given a surjection `f : A ↠ B` and, for every pair `a, a'`, a local type `P` together with a localization of `a = a'` at `P`, produces a `Local` structure on `B` and a `Localization` of `A` along `f`, both in `Separated U`. This is the abstract ingredient assembled by `ReflSeparated` to obtain the reflection.
