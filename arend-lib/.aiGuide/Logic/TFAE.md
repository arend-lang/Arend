### Logic.TFAE

"The following are equivalent" — a propositional predicate stating that all elements of an array of propositions are mutually equivalent, plus tools for proving it.

The module formalizes the classic mathematical idiom "TFAE" (the following are equivalent) as `TFAE l = ∀ i j, l i → l j` over an array of propositions. Rather than requiring the user to supply the full quadratic implication matrix, it provides two practical strategies: a **cycle** strategy (`cycle'` / `cycle`) where one supplies the chain `l 0 → l 1 → … → l n → l 0`, and a **graph-connectivity** strategy (`proof'` / `proof`) where the user supplies an arbitrary list of implications that is checked at type-checking time to be strongly connected via a `Bool`-valued reachability algorithm. The internal graph machinery (`Path`, `collect`, `step1`, etc.) implements a small reflective decision procedure: a primed variant computes the transitive closure abstractly, an unprimed variant memoizes via an accumulator for efficiency, and correctness lemmas bridge the two so that `So (checkConnected …)` discharges the proof obligation.

#### Main Definition

- **`TFAE`**: The proposition `\Pi (i j : Fin l.len) -> l i -> l j` asserting all entries of an array of propositions imply each other.

#### Proof Strategies (User-Facing)

- **`proof'`**: Proves `TFAE l` from an arbitrary list of pairwise implications `(i,j, l i → l j)` whose underlying directed graph passes the connectivity check `checkConnected`. The check is supplied as a `So` hypothesis solved by reflection.
- **`proof`** (`\meta`): Convenience meta wrapping `proof'` with a `later` to defer typechecking of the conclusion.
- **`cycle'`**: Proves `TFAE l` from a cyclic chain of implications `l i → l (i+1 mod n+1)` given as a dependent array.
- **`cycle`** (`\meta`): Convenience meta wrapping `cycle'`.

#### Graph Reachability Infrastructure (inside `proof'`)

- **`Graph`**: Type alias `Array (\Sigma A A)` for a directed graph as an array of edges.
- **`Path`**: Inductive reachability predicate on a graph with constructors `trivial` (reflexivity) and `step` (one edge followed by a `Path`).
- **`path-proof`**: Transports a witness `l i` along a `Path` of implications to obtain `l j`.

#### Reflective Connectivity Check

- **`checkConnected`**: Decides whether every vertex `< t` reaches every other vertex in graph `G : Graph Nat`, returning `Bool`.
- **`checkConnected1`**: Decides whether vertex `i` reaches every vertex `< t`.
- **`step1`** / **`makeStep`** / **`collect`**: Memoized BFS that grows a reachable set, skipping vertices already in the accumulator `ex` for efficiency.
- **`step1'`** / **`makeStep'`** / **`collect'`**: Non-memoized counterparts used as a clean reasoning bridge for correctness proofs.

#### Correctness Lemmas

- **`checkConnected-correct`**: From `checkConnected G n = true` extracts `Path G i j` for all `i j : Fin n`.
- **`checkConnected1-correct`**: Per-source variant: from `checkConnected1 G i n = true` extracts `Path G i j`.
- **`Path-Nat_Fin`**: Lifts a `Path` over `Nat` to a `Path` over `Fin n` when all endpoints fit.
- **`step1'-correct`**, **`makeStep'-correct`**, **`collect'-correct`**: Show that membership in the primed reachable sets witnesses an edge or a `Path` in `G`.
- **`step1->step1'`**, **`makeStep->makeStep'`**, **`collect->collect'`**: Reduce membership in the memoized variants to the primed variants (modulo the accumulator).
- **`step1'_makeStep'`**, **`makeStep'-monotone`**, **`collect'-monotone`**: Closure and monotonicity of the primed BFS under list inclusion, used to chain reachability through `collect'`.

#### Cycle Strategy Helpers (inside `cycle'`)

- **`aux`**: Iterates implications `l i → l (i+1)` along a `Nat` offset `k` with `i + k = j` to obtain `l i → l j`.
- **`aux2`**: Combines a linear chain `l i → l (i+1)` with a closing implication `l n → l 0` into a full `TFAE l`.
