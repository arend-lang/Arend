### Logic.Rewriting.ARS.AbstractReductionSystem

Abstract reduction systems.

- **`AbstractReductionSystem`** (ARS): Class with carrier `A`, reduction `~>`, convertibility `|--|`, and symmetry of `|--|`.
- **`SimpleARS`**: ARS where `|--|` is equality.
- **`~>_0`**, **`~>_1`**, **`~>_=`**, **`~>_+`**, **`~>_*`**: Identity, single-step, reflexive, transitive, and reflexive-transitive closures of `~>`.
