### Topology.RatherBelow

The "rather below" relation on a topological meet-semilattice, an abstract axiomatization of the way-below/well-inside relation used in formal topology and locale theory.

#### Main Class

- **`RatherBelow`**: Structure on a `TopMeetSemilattice` `A` carrying a binary relation `R : A -> A -> \Prop` satisfying:
  - **`<=<-left`**: `R U V -> V <= W -> R U W` (upward closed on the right).
  - **`<=<-right`**: `U <= V -> R V W -> R U W` (downward closed on the left).
  - **`<=<_top`**: `R V top` — every element is rather below the top.
  - **`<=<_meet`**: `R U V -> R U' V' -> R (U ∧ U') (V ∧ V')` — compatibility with binary meets.

#### Lemmas

- **`<=<c_bottom`**: On a `CompleteLattice`, if `bottom` is rather below every element via `R`, then `bottom` is also below every `U` under the closure relation `R.<=<c`.
- **`<=<c_^-1`**: Preimages preserve the closure relation: given a function `f : X -> Y` and rather-below structures on `SetLattice X` and `SetLattice Y` such that `RY U V` implies `RX (f ^-1 U) (f ^-1 V)`, then `U RY.<=<c V` implies `f ^-1 U RX.<=<c f ^-1 V`.
