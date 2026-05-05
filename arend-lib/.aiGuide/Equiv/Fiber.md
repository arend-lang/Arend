### Equiv.Fiber

Contractible fibers characterization of equivalences.

- **`hasContrFibers`**: `\Pi (b : B) -> Contr (Fib f b)` — every fiber of `f` is contractible.
  - **`levelProp`**: `hasContrFibers f` is a proposition.
- **`contrFibers=>Equiv`**: Contractible fibers imply `QEquiv`.
- **`Equiv=>contrFibers`**: An `Equiv` has contractible fibers.
  - **`fromSection`**: Constructs contractible fibers from a section with a retraction.
