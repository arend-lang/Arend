### Category.KanExtension

Constructions of left and right Kan extensions of functors along functors between small categories.

Given a functor `p : C -> C'` and `F : C -> D`, the right Kan extension `Ran_p F : C' -> D` is built pointwise as a limit over the comma category `(b ↓ p)` for each `b : C'`, requiring `D` to be complete. The left Kan extension is obtained by dualizing the right Kan extension construction. The module also provides a `DoubleLimit` class establishing the universal/Fubini-style relationship between a limit of the Kan extension along a diagram `G : J -> C'` and the limit of `F` over the combined comma category, giving the canonical iso that justifies "limits commute with right Kan extensions".

#### Kan Extensions

- **`LeftKanExt`**: Left Kan extension `Lan_p F : C' -> D` of `F : C -> D` along `p : C -> C'`, defined by op-dualization of `RightKanExt` (requires `D` cocomplete).
- **`RightKanExt`**: Right Kan extension `Ran_p F : C' -> D` along `p : C -> C'` (requires `D` complete). On objects sends `c'` to the limit of `F` over the comma category `(c' ↓ p)`; on morphisms uses the universal property via `commaFunctor` to reindex cones.

#### Pointwise Limit Helpers

- **`RightKanExt.lim`**: The pointwise value of the right Kan extension at `b : C'`, computed as `D.limit (F ∘ rightForget (Const b) p)` — i.e. the limit of `F` over the comma category `(b ↓ p)`.
- **`RightKanExt.commaFunctor`**: For `f : y -> x` in `C'`, the induced functor between comma categories `(x ↓ p) -> (y ↓ p)` used to define the action of `RightKanExt` on morphisms.

#### Iterated Limits over Kan Extensions

- **`DoubleLimit`**: Class parameterized by `p : C -> C'`, `F : C -> D`, and a diagram `G : J -> C'`, packaging the comparison between `lim_J (Ran_p F ∘ G)` and the limit of `F` over the total comma category `(G ↓ p)`.
  - **`lim'`**: The limit of `F ∘ rightForget G p` — i.e. the "outer" combined limit.
  - **`cone`**: A cone over `F ∘ rightForget G p` with apex `D.limit (Ran_p F ∘ G)`, built by composing limit projections through both forgetful functors.
  - **`cone'`**, **`cone''`**: Cones that exhibit `lim'` as a cone over `Ran_p F ∘ G` (and over each fiber `lim F (G j)`).
  - **`iso`**: The canonical isomorphism `D.limit (Ran_p F ∘ G) ≅ lim'`, expressing that taking the limit over `J` of the pointwise Kan extension agrees with the joint limit.
  - **`map_cone`**: Lifts a cone `c` over `G` to a cone over `F ∘ rightForget G p` with apex `Ran_p F c`.
  - **`map_iso`**: If the induced map `lim'.limMap (map_cone c)` is an iso, then so is `limMap (Cone.map (Ran_p F) c)` — used to transport limit-preservation along Kan extension.
