### Homotopy.Localization.Separated

Constructs the universe of separated types (those whose identity types are local) from a given local universe, and shows that a reflective localization extends to separated localization.

#### Universe Construction

- **`Separated`**: Given a `Universe U`, builds the universe whose local types `Z` are those for which every identity type `z = z'` is `U`-local. A type is "separated" relative to `U` when its path spaces are local.

#### Reflective Separated Localization

- **`ReflSeparated`**: Promotes a `ReflUniverse U` to a `ReflUniverse` over `Separated U`. The localization of a type `A` is constructed via the image of the Yoneda-style map `F a := YImage.dom-map a` into local path-types, witnessing that each identity type `F a = F a'` is local with localization data inherited from `LType (a = a')`.

#### Helpers (in `\where`)

- **`S`**: The path-localization functor on `A`: `S a a' := LType (a = a')`, the local replacement of the identity type.
- **`F`**: The factoring map `a |-> YImage.dom-map a` whose image is the separated localization of `A`.
- **`pathEquiv`**: Key equivalence `(F a = F a') = LType (a = a')`. Proven by chaining: image-embedding gives `(F a = F a') = (S a = S a')`, then path symmetry, then pointwise univalence to turn `S a' = S a` into `\Pi (x : A) -> Equiv (S a' x) (S a x)`, which factors through the embedding via `localizationFactorEmbedding` using `localYoneda` and `equivLocal`.
- **`separatedLocalization`**: Given a surjection `f : A -> B` such that every `f a = f a'` is local with `(a = a')` localizing into it, produces a `Local` structure on `B` and a `Localization` of `A` along `f` in the `Separated U` universe. The core lemma turning pointwise path-localization data into a separated localization.
