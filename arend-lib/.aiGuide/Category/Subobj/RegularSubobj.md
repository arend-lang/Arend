### Category.Subobj.RegularSubobj

Regular subobjects (those whose representing mono is a regular mono), and closure of regularity under meets.

- **`IsRegularSubobj`**: Proposition that a subobject `s : Subobj obj` is regular, i.e. its representing mono is a regular mono (`IsRegularMono`); well defined on equivalence classes of monos.
- **`regularSubobj-meet`**: In a finitely complete precategory, the meet (`SubobjectSemilattice.meet`) of two regular subobjects `s1`, `s2` of `obj` is again regular.
- **`regularSubobj-meet.pullback-isRegularMono`**: The composite `P.f ∘ pbProj1` is itself a regular mono when `P.f` and `P.g` are, so the meet is again a regular subobject.
