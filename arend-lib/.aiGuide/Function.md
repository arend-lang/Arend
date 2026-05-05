### Function (root file)

Basic function combinators and properties.

- **`id`**: Identity function.
- **`-o`**: Pre-composition: `(-o f) g x = g (f x)`.
- **`o-`**: Post-composition: `(o- g) f x = g (f x)`.
- **`o`**: Function composition: `(g o f) x = g (f x)`.
- **`IsInj`**: Injectivity: `f a = f a' -> a = a'` (for sets).
- **`IsSurj`**: Surjectivity: `\Pi (y : B) -> ∃ (x : A) (f x = y)`.
  - **`comp`**: Composition of surjections is surjective.
- **`assuming`**: `assuming f g = f g` — applies a function expecting a callback.
- **`Image`**: `\Sigma (b : B) (∃ (a : A) (f a = b))` — the image of `f`.
