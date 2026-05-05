### Homotopy.Fibration

Defines total spaces of type families and homotopy fibers of maps, with characterization of their path types.

#### Total Spaces

- **`Total`**: Total space `\Sigma (b : B) (F b)` of a type family `F : B -> \Type`.
- **`Total.proj`**: First projection `Total F -> B` extracting the base point.

#### Homotopy Fibers

- **`Fib`**: Homotopy fiber `\Sigma (a : A) (f a = base)` of a map `f : A -> B` over a point `base : B`.
- **`Fib.make`**: Constructor for fiber elements from a point `a : A` and a path `f a = base`.

#### Path Characterization in Fibers

- **`Fib.ext`**: Extensionality for fibers — produces `x = x'` in `Fib f b0` from a path `p : x.1 = x'.1` between underlying points and a coherence `pmap f p *> x'.2 = x.2`.
- **`Fib.ext.retraction`**: Helper providing the retraction data witnessing that `ext` is a section, used to establish the equivalence below.
- **`Fib.equiv`**: Quasi-equivalence `(x = x') ≃ \Sigma (p : x.1 = x'.1) (pmap f p *> x'.2 = x.2)`, characterizing identity types of homotopy fibers via underlying paths plus coherence.
