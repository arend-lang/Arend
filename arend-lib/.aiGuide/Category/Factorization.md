### Category.Factorization

Weak and orthogonal factorization systems on a precategory.

A factorization system splits every morphism into a "left" map followed by a "right" map, subject to a lifting property between the two classes. `WFS` captures the weak version, where lifts merely exist; `OFS` strengthens this to orthogonal factorization, where the lift is unique and the construction is encoded as an equivalence of hom-sets. The orthogonal version derives `lift` automatically from the equivalence by inverting it on the canonical commuting square.

#### Weak Factorization Systems

- **`WFS`**: Class over a precategory `C` parameterized by predicates `L`, `R` on morphisms. Provides:
  - **`factors`**: Every `h : Hom x z` factors as `g ∘ f = h` with `L f` and `R g`.
  - **`lift`**: Given a commuting square `g ∘ t = s ∘ f` with `L f` and `R g`, produces a diagonal filler `l : Hom b c` satisfying `l ∘ f = t` and `g ∘ l = s`.
- **`WFS.left-epi`**: In a `CartesianPrecat`, if `L f` holds and the diagonal `diagonal z` is in `R`, then `f` is epic: `g ∘ f = h ∘ f` implies `g = h`.

#### Orthogonal Factorization Systems

- **`OFS`**: Extends `WFS` by replacing existence of lifts with a uniqueness equivalence.
  - **`unique-lift`**: For `L f` and `R g`, the map `Hom b c → \Sigma (t : Hom a c) (s : Hom b d) (g ∘ t = s ∘ f)` sending `l` to `(l ∘ f, g ∘ l, inv o-assoc)` is an `Equiv`. This says diagonals are in bijection with commuting squares.
  - **`lift`** (derived): Implements the `WFS` lift by applying the inverse of `unique-lift` to the given square and extracting the section data.

#### Helpers for Orthogonality

- **`OFS.liftFromMono`**: Constructs the `unique-lift` equivalence from weaker data — when `g` is a `Mono`, having a lift `l` with only `g ∘ l = s` (without the `l ∘ f = t` condition) suffices, since monicity of `g` recovers the missing equation and uniqueness.
