### Topology.RatherBelow

Abstract "rather below" relations on topological meet-semilattices, capturing the idea of one open being well-inside another.

A `RatherBelow` is a binary relation `R` on a `TopMeetSemilattice` that is monotone on both sides, has `top` as a universal upper bound, and is preserved by binary meets. From any such relation, two derived strengthenings are constructed: `<=<o` (Omega), the largest sub-relation that interpolates a single step, and `<=<c` (Interpolative), the largest sub-relation that interpolates within itself — yielding a dense/way-below-style refinement. These derived relations again form `RatherBelow` instances, so the construction iterates and `<=<c` provides the interpolative core used in pointfree topology and frame-theoretic constructions.

#### Class Definition

- **`RatherBelow`**: Class over `{A : TopMeetSemilattice}` with a relation `R : A -> A -> \Prop` satisfying:
  - **`<=<-left`**: Right-monotonicity in the codomain: `R U V -> V <= W -> R U W`.
  - **`<=<-right`**: Left-monotonicity in the domain: `U <= V -> R V W -> R U W`.
  - **`<=<_top`**: Everything is rather-below `top`.
  - **`<=<_meet`**: Compatibility with binary meets: `R U V -> R U' V' -> R (U ∧ U') (V ∧ V')`.

#### Derived Lemmas

- **`<=<_meet-same`**: From `R U V` and `R U V'` derive `R U (V ∧ V')` (single-domain meet).

#### Omega: One-Step Interpolative Refinement

- **`<=<o`**: `V <=<o U` holds iff there exists a sub-relation `R' ⊆ R` containing `(V, U)` such that any `R'`-pair admits a one-step `R`-interpolant. The largest "interpolative once" subrelation of `R`.
- **`<=<o_<=<`**: `<=<o` implies `R`.
- **`<=<o-inter`**: One-step interpolation: `V <=<o U` yields some `W` with `V <=<o W` and `R W U`.
- **`Omega`**: The `RatherBelow` instance built from `<=<o`.

#### Interpolative: Fully Interpolative Refinement

- **`<=<c`**: `V <=<c U` holds iff there exists a sub-relation `R' ⊆ R` containing `(V, U)` that interpolates within itself. The largest "fully interpolative" subrelation of `R`.
- **`<=<c_<=<o`**: `<=<c` implies `<=<o`.
- **`<=<c_<=<`**: `<=<c` implies `R`.
- **`<=<c-inter`**: Self-interpolation: `V <=<c U` yields some `W` with `V <=<c W` and `W <=<c U`.
- **`<=<c-func`**: Functoriality: a relation morphism `R -> R2` lifts to `<=<c -> R2.<=<c`.
- **`Interpolative`**: The `RatherBelow` instance built from `<=<c`.

#### Auxiliary Lemmas

- **`<=<c_bottom`**: In a `CompleteLattice`, if `R bottom U` holds for all `U`, then `bottom R.<=<c U`.
- **`<=<c_^-1`**: Preimage compatibility: a function preserving `R` between set-lattices also preserves `<=<c`, i.e. `U RY.<=<c V` implies `f ^-1 U RX.<=<c f ^-1 V`.
