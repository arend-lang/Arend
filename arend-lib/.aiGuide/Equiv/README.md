### Equiv

This directory develops the homotopy-theoretic theory of equivalences: characterizations via fibers and half-adjoint coherence, equivalences on path/sigma/pi types, and the univalence principle.

#### Characterizations of Equivalence

- **`Fiber.md`** — Characterizes equivalences as maps with contractible fibers, with conversions in both directions.
- **`HalfAdjoint.md`** — Half-adjoint equivalences (`HAEquiv`) adding a triangle identity to `QEquiv` so that being an equivalence becomes a proposition.

#### Equivalences on Type Constructors

- **`Path.md`** — Lifts retractions, equivalences, embeddings, and sections to equivalences on path spaces via `pmap`.
- **`Sigma.md`** — Equivalences for sigma and pi types: contracting components, transporting along base equivalences, and the total-map characterization of fiberwise equivalences.

#### Univalence

- **`Univalence.md`** — The univalence principle: a `QEquiv` between `A = B` and `Equiv {A} {B}`, built from `transport` and the primitive `iso` constructor.
