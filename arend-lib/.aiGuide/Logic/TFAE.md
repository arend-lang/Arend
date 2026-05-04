### Logic.TFAE

"The following are equivalent" (TFAE) automation.

- **`TFAE`**: `\Pi (i j : Fin l.len) -> l i -> l j` — all propositions in array `l` are mutually equivalent.
  - **`proof'`** / **`proof`**: Proves TFAE from a list of directed implications, verified by a graph connectivity check (`checkConnected`).
    - **`Graph`**, **`Path`**: Graph type and path relation for connectivity.
    - **`checkConnected`** / **`checkConnected1`**: Boolean connectivity checks on `Nat`-labeled graphs.
    - **`step1`**, **`makeStep`**, **`collect`**: BFS-style graph traversal helpers.
    - **`checkConnected-correct`**: Correctness of `checkConnected`.
  - **`cycle'`** / **`cycle`**: Proves TFAE from a cyclic chain of implications `l 0 -> l 1 -> ... -> l n -> l 0`.
    - **`aux`**, **`aux2`**: Helper lemmas for cyclic proof.
