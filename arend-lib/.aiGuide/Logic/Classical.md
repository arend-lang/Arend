### Logic.Classical

Classical logic principles (assuming LEM/AC).

- **`lem`**: Law of excluded middle: `Dec P` for any `P : \Prop`.
- **`Choice`**: Class extending `BaseSet` with axiom of choice: `(\Pi (x : E) -> TruncP (B x)) -> TruncP (\Pi (x : E) -> B x)`.
- **`choice`**: Axiom of choice for any set `A`.
  - **`lemFromChoice`**: Derives LEM from choice.
