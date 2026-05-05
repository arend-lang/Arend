### Homotopy.Sphere.Circle

Defines the circle as a higher inductive type and proves its loop space is the integers.

#### Circle Definition

- **`Sphere1`**: HIT for the circle with point constructor `base1` and path constructor `loop : base1 = base1`.
- **`Sphere1.ploop`**: `loop` packaged as a path: `path loop`.
- **`OmegaS1`**: The loop space of the circle, `base1 = base1`.

#### Equivalence with Suspension Definition

- **`Sphere1-equiv`**: `QEquiv` between `Sphere1` and `Sphere 1` (the suspension-based sphere).
- **`Sphere1-equiv.CircleSusp`**: Maps `Sphere1` to `Sphere 1`, sending `loop` to `pmerid north *> inv (pmerid south)`.
- **`Sphere1-equiv.SuspCircle`**: Inverse map from `Sphere 1` to `Sphere1`.
- **`Sphere1-equiv.CircleSuspCircle`**: Round-trip identity `SuspCircle ∘ CircleSusp ~ id`.
- **`Sphere1-equiv.SuspCircleSusp`**: Round-trip identity `CircleSusp ∘ SuspCircle ~ id`, using `Cube2.map` to fill the necessary square.

#### Universal Cover

- **`code`**: The universal cover `Sphere1 -> \Set0`, defined as `Int` at `base1` with the loop acting by the `isuc`/`ipred` isomorphism.
- **`encode`**: `(p : base1 = x) -> code x`, transporting `0 : Int` along `p`.
- **`wind`**: `Int -> OmegaS1`, sending `n` to the `n`-fold concatenation of `loop` (with inverses for negatives).
- **`decode`**: `(x : Sphere1) -> code x -> base1 = x`, extending `wind` over the loop via a dependent path.
- **`decode.wind_loop`**: `wind n *> path loop = wind (isuc n)`.
- **`decode.decode_loop`**: Naturality used to define `decode` over the loop constructor.

#### Loop Space Theorem

- **`encode_decode`**: `decode x (encode x p) = p` for any `p : base1 = x`.
- **`encode_wind`**: `encode base1 (wind x) = x`, by induction on the integer.
- **`decode_encode`**: `encode x (decode x c) = c`, the other half of the equivalence.
- **`Loop_S1`**: The fundamental theorem `OmegaS1 = Int`, witnessing that the loop space of the circle is the integers.
