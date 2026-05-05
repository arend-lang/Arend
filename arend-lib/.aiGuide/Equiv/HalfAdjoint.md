### Equiv.HalfAdjoint

Half-adjoint equivalences.

- **`HAEquiv`**: Class extending `QEquiv` with coherence condition `f_ret_f=f_sec_f : pmap f (ret_f x) = f_sec (f x)`.
  - **`coh_f_sec`**: Constructs a coherent section from a section and retraction.
  - **`coh_f_ret_f=f_sec_f`**: Proves the coherence condition for the constructed section.
  - **`fromQEquiv`**: Coercion from `QEquiv` to `HAEquiv`.
  - **`levelProp`**: `HAEquiv f` is a proposition.
