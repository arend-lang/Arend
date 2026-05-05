### Category.KanExtension

Left and right Kan extensions of functors along a functor between small categories, computed pointwise via (co)limits over comma categories.

#### Kan Extensions

- **`LeftKanExt`**: Given `p : Functor C C'` and `F : Functor C D` with `D` cocomplete, produces the left Kan extension `Functor C' D`. Defined by dualizing `RightKanExt` on opposite categories.
- **`RightKanExt`**: Given `p : Functor C C'` and `F : Functor C D` with `D` complete, produces the right Kan extension `Functor C' D`. The object map sends `c'` to the limit of `F` over the comma category `(c' ↓ p)`; the morphism map is induced by precomposing the cone with the comma-category functor.

#### Pointwise Construction (in `\where`)

- **`lim`**: The pointwise limit defining the Kan extension at `b : C'`, namely `D.limit (Comp F (commaPrecat.rightForget (Const b) p))` — the limit of `F` restricted to the comma category over `b`.
- **`commaFunctor`**: For `f : Hom y x` in `C'`, the induced functor between comma categories `(y ↓ p) → (x ↓ p)` used to transport cones along morphisms in `C'`.

#### Auxiliary Class

- **`DoubleLimit`**: Bundles the data `J, C, C' : SmallPrecat`, `D : CompletePrecat`, a functor `p : C → C'`, a diagram `F : C → D`, and an indexing functor `G : J → C'`, packaging the setup for iterated/double-limit constructions.
