### Homotopy.Sphere.Circle

The circle `S¹` as a higher inductive type and the computation of its loop space `Ω(S¹) = ℤ`.

This module presents the circle directly as a HIT `Sphere1` with one point `base1` and one loop, and proves it equivalent to the suspension-based `Sphere 1`. The fundamental group calculation follows the standard HoTT encode-decode method: a universal cover `code : Sphere1 -> Set` defined by transporting along the integer successor equivalence, with `encode`/`decode` providing mutually inverse maps between paths `base1 = x` and elements of `code x`. The end result `Loop_S1 : OmegaS1 = Int` exhibits `Ω(S¹)` as the integers.

#### Circle as a Higher Inductive Type

- **`Sphere1`**: The circle as a HIT with constructors `base1` and `loop : base1 = base1`.
- **`Sphere1.ploop`**: The loop packaged as a path: `path loop`.
- **`OmegaS1`**: The loop space `base1 = base1`.

#### Equivalence with the Suspension Sphere

- **`Sphere1-equiv`**: A `QEquiv` between `Sphere1` and `Sphere 1` (the suspension-based 1-sphere).
- **`Sphere1-equiv.CircleSusp`**: Maps `Sphere1 -> Sphere 1`, sending `base1` to `north` and `loop` to `pmerid north *> inv (pmerid south)`.
- **`Sphere1-equiv.SuspCircle`**: Maps `Sphere 1 -> Sphere1`, collapsing the suspension by sending both poles to `base1` and using `loop` for one meridian.
- **`Sphere1-equiv.CircleSuspCircle`**: The retraction `SuspCircle ∘ CircleSusp ~ id` on `Sphere1`, established via path algebra on the loop case.
- **`Sphere1-equiv.SuspCircleSusp`**: The section `CircleSusp ∘ SuspCircle ~ id` on `Sphere 1`, using `Cube2.map` to fill the required 2-cell.

#### The Universal Cover

- **`code`**: The fibration `Sphere1 -> Set0` with `code base1 = Int` and the loop acting by the integer successor isomorphism `iso isuc ipred ...`.
- **`encode`**: For `p : base1 = x`, returns `transport code p 0` — the winding number of a path.
- **`wind`**: Builds a path `OmegaS1` from an integer by iterating `path loop` (or its inverse for negatives).

#### Decoding and Inverse Properties

- **`decode`**: Section `(x : Sphere1) -> code x -> base1 = x`; equals `wind` on `base1`, with the loop case handled via a dependent path.
- **`decode.wind_loop`**: Key lemma `wind n *> path loop = wind (isuc n)` — appending the loop is the successor.
- **`decode.decode_loop`**: Coherence showing `decode` is well-defined across the loop, proved using `simp_coe` and `wind_loop`.
- **`encode_decode`**: `decode x (encode x p) = p` for any path `p`, by path induction.
- **`encode_wind`**: `encode base1 (wind x) = x`, by induction on the integer using `transport_*>` and `transport_inv_func`.
- **`decode_encode`**: `encode x (decode x c) = c`, reduced to `encode_wind` at `base1`.

#### Main Theorem

- **`Loop_S1`**: The equality `OmegaS1 = Int`, exhibited via the iso `(encode base1, wind, encode_decode, encode_wind)`.
