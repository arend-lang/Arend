### Category.Topos.Sheaf.Sub

Constructs sheaves as subobjects of existing sheaves via embedding natural transformations satisfying a closure-under-amalgamation property.

#### Sub-Sheaf Construction

- **`subSheaf`**: Given a presheaf `F`, a sheaf `G`, and a pointwise-injective natural transformation `e : F -> G` such that any local section of `G` whose restrictions all lift to `F` over a cover globally lifts to `F`, produces a `Sheaf` structure on `F`. The closure hypothesis `cl` takes a covering sieve `s`, a section `x : G a`, and per-arrow lifts in `F`, and yields a global lift in `F`.
- **`subSheafWithBasis`**: Variant of `subSheaf` for sites equipped with a basis (`SiteWithBasis`), where the closure condition is phrased over basic covers `g : J -> SlicePrecat a` indexed by a set `J` rather than over arbitrary covering sieves. More convenient when working with explicitly generated topologies.

#### Conversion Lemmas

- **`subSheaf.conv`**: Given two sheaves `F`, `G` with an embedding `e : F -> G`, a covering sieve `s` of `a`, a section `x : G a`, and lifts `(y_f, e y_f = G(f) x)` for every `f` in the sieve, constructs the unique amalgamated lift `(y : F a, e a y = x)` using the sheaf condition on `F` and uniqueness on `G`.
- **`subSheafWithBasis.conv`**: Basis-form analogue of `subSheaf.conv`: reduces a basic cover hypothesis to the sieve form by passing through `C.genSieve` and dispatching the embedding-based propositional truncation.
