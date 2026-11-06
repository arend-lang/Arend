### Logic.Bar

Inductive bars over arrays: the predicate `Bar` expressing that every infinite sequence eventually reaches a prefix satisfying a given property, with its closure properties.

`Bar P l` is an inductively defined proposition (a truncated data type): either `P` already holds of the array `l` (`bar-stop`), or for every element `a` the array `a :: l` is again barred (`bar-ask`). This is the constructive notion of a bar used in bar induction and in the study of Noetherian and finite sets. The module shows that bounded properties are bars (`bounded-bar`) and that a bar is reached along every stream (`bar-stream`, via `IsInvPrefix`). It then gives the closure properties of bars: transfer along surjections and relations (`bar-surj`, `bar-map`, `bar-rel`), monotonicity of the predicate (`bar-impl`), shifting the base array (`bar-shift-left`, `bar-shift-right`), and, for `IsBarMonotone` predicates, closure under extension and conjunction (`bar-monotone`, `bar-conj`). The fan theorem built on this is in `Logic.Bar.FanTheorem`.

#### The bar predicate

- **`Bar`**: The inductive proposition `Bar P l` for `P : Array A -> \Prop` and `l : Array A`, with constructors `bar-stop` (`P l` holds) and `bar-ask` (`Bar P (a :: l)` for all `a : A`).
- **`bounded-bar`**: If `P` holds for every array of some fixed length `n`, then `Bar P nil`.

#### Streams

- **`IsInvPrefix`**: The relation that an array `l` consists of the first values of a stream `s : Nat -> A`, with the array stored in reverse order (the head is `s` at index `l.len`). Its lemma `IsInvPrefix.toIndex` expresses `l i` as `s (l.len -' suc i)`.
- **`bar-stream`**: If `Bar P nil` then along every stream `s` there merely is an array `r` which is an `IsInvPrefix` of `s` and satisfies `P r`.

#### Transferring bars

- **`bar-surj`**: If `f : A -> B` is surjective and `P (map f as)` is a bar on `A`, then `P` is a bar on `B`.
- **`bar-map`**: A bar for `P` on `map f l` yields a bar for `\lam as => P (map f as)` on `l`.
- **`bar-impl`**: If `P` implies `Q` then every bar for `P` over `l` is a bar for `Q` over `l`.
- **`bar-rel`**: Transfers a bar along a relation `R : A -> B -> \Prop` whose every `b` has some related `a`, provided `P as` and pointwise relatedness imply `Q bs`.
- **`bar-shift-left` / `bar-shift-right`**: Move the base array `l` of a bar into the predicate (`P (l' ++ l)` over `nil`) and back.
- **`bar-++-insert`**: If `P` is stable under inserting an array `m` in the middle, a bar over `l ++ r` gives a bar over `l ++ m ++ r`.

#### Monotone predicates

- **`IsBarMonotone`**: The predicate `P` is preserved by prepending an element: `P l` implies `P (a :: l)`.
- **`barMonotone_++`**: If `P` is monotone, so is `\lam l => P (l ++ r)`.
- **`barMonotone_map`**: If `P` is monotone, so is `\lam l => P (map f l)`.
- **`bar-monotone`**: If `P` is monotone, then `Bar P` is monotone as well.
- **`bar-conj`**: For monotone `P` and `Q`, bars over `l` for `P` and for `Q` give a bar for their conjunction.
