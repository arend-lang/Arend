# Local changes to the Arend skills

The Arend skills used in this repository come from
[sxhya/arend-skills](https://github.com/sxhya/arend-skills), installed by copying
the skill directories into `~/.claude/skills/`. Those copies have been edited
locally, and they are not under version control. This file records those edits.

- **Upstream base:** commit `03f081b` ("Prefer \meta over \func for constants
  linarith must see through").
- **Exact diff:** [`arend-skills-local-changes.patch`](arend-skills-local-changes.patch).
  Applied with `patch -p1` to a checkout of the base commit, it reproduces the
  five installed `SKILL.md` files byte for byte (verified 12 October 2026).

To re-apply the changes after reinstalling the skills:

```sh
git clone https://github.com/sxhya/arend-skills.git && cd arend-skills
git checkout 03f081b
patch -p1 < /path/to/Arend/docs/arend-skills-local-changes.patch
for d in arend-*/; do cp -R "$d" ~/.claude/skills/; done
```

All added material describes Arend and its tooling in general. It is not tied to
any particular formalization.

## 1. Local paths (`arend-formalize`, `arend-prove`, `arend-quirks`)

The upstream skills hard-code the author's checkout,
`/home/sergey/Documents/Arend/`. It is replaced by this repository,
`/Users/admin/codingspace/arend-lang-new/Arend/`, in:

- the `cd …/arend-lib && arend --serialize <Module>` command;
- the base path for `Module/File.ard:NNN` citations;
- the location of `arend-issues.md`, the tooling-friction log.

Two other `/home/sergey/…` paths are unchanged, because they have no local
counterpart: the `tutorial-code-fresh` reference in `arend-prove` and the
website docs checkout in `arend-quirks`.

## 2. Doc comments (`arend-quirks` §19)

`-- |` is a single-line doc comment. Continuation lines written as `--   …`
after it are ordinary comments and are not part of the documentation. The
parser only recognizes `-- |` for a single line, and the pretty-printer emits
`{- | … -}` whenever a description has more than one line. Multi-line docs must
therefore be `{- | … -}` blocks, and the existing advice about `{-` and `-}`
inside block comments applies to them.

## 3. New `arend-quirks` §22: surface-syntax and elaboration surprises

Twelve entries:

- `{Name}` links in doc text must resolve in scope, to a definition, an import,
  or a parameter of the documented declaration. Otherwise the IDE reports
  `Cannot resolve reference`, which the CLI typecheck never checks. Use
  `` `Name` `` for names not in scope; braces inside `$…$` are LaTeX.
- An infix operator cannot be applied prefix with an implicit argument
  (`GSet.** {X} g x`). Bind the instance and use `g X.** x`.
- A type annotation on a let-bound class instance can give
  `Infinite level is not allowed here`. Drop the annotation.
- `P || x = 0` parses as `(P || x) = 0`.
- A pattern variable cannot start with a digit. `| byRight 1=0 => …` fails with
  `'Nat.suc' is not a constructor of data type Path`.
- A recursive `\func` returning a class instance, or an `Array` of class
  instances, gives `Infinite level is not allowed here`. Use level parameters
  `.{u}` with bounded carriers (`Ring.{u}`), make helpers top-level, and index by
  functions rather than arrays.
- `\type` synonyms are not unfolded by unification. Convert explicitly, for
  example with `Sym.fromEquiv`.
- A dotted top-level name (`\func Rec.helper`) declares a local module that
  shadows the imported record.
- If two imported records share field names, `x.f` can resolve to the wrong
  record. Import only the record names.
- `IsEquiv f` is `TruncP (QEquiv f)`. Use `IsEquiv.isInj e`, and get
  surjectivity by `\case`.
- Truncated elimination inside a lambda needs an annotated binder type.
- In `cases (x arg addPath, …)`, value and path patterns alternate. `cases` only
  abstracts literal occurrences in the goal; to see through a definition, prove
  a helper over explicit values and equations.

## 4. New `arend-prove` failure modes 18–25

- **18.** Composition aliases (`LinearMap.∘`) and `toMatrix`/`toMatrix_*` infer
  the wrong modules. Pass all implicits, and disambiguate identity matrices as
  `Ring.ide {MatrixRing R n}`.
- **19.** A path chain between record values fails with "Candidate is:
  SomeRecord {R}" because of record subtyping. Prove pointwise with `exts`.
- **20.** `exts` on maps out of `ArrayFinModule n` can fail. Compare matrix
  entries with `matrixExt`.
- **21.** `equation` may use the monoid solver for additive abelian groups and
  splits definitionally equal atoms. Prove small identities over variables with
  `equation.abGroup`; `equation.cGroup` is the multiplicative solver.
- **22.** The ring solver keeps group-inverse applications inside an action as
  opaque atoms. Rewrite them to explicit values first.
- **23.** `basis-split` is an `\sfunc`, so coordinates never compute. Use
  computing coordinate functionals for concrete goals.
- **24.** Endpoint and instance inference gaps in `pmap`, `BigSum-ext` and
  `FinSum-ext`, and `rewrite toFin=id` failures. Use `transport` instead.
- **25.** There are no numeral literals for types without a numeral instance,
  such as `Complex`.

## 5. New `arend-formalize` recipes

Added before the CLI quick reference:

- **"`arend -t` says `Failed: N` but prints no errors":** typecheck the test file
  as a throwaway library that depends on `arend-lib`, which shows the messages.
- **"A module's goals are missing from a multi-module check":** a module first
  typechecked as a dependency of an earlier target does not get its `[GOAL]`
  lines printed. Order targets by dependency, or check modules individually.
- **Daemon hygiene:**
  - flags are fixed at daemon start-up, so a fresh verification run needs
    `--no-daemon`;
  - restart the daemon and delete stale `.arc` files after renaming a module;
  - `--json` output goes to stdout and diagnostics to stderr;
  - `-fu --json` gives the enclosing declaration of each usage, which is enough
    for transitive-dependency queries.
- **Pruning imports:** remove one import at a time, re-typecheck, and keep the
  removal if there are no errors and the goal count is unchanged.

## 6. New error-catalogue entries

- **`arend-error-type-mismatch`:**
  - record subtyping, `Cannot infer parameter 'A' … Candidate is: SomeRecord {R}`;
  - an unfolded `\type` synonym, `Cannot solve equation Rec … / E`.
- **`arend-error-extraneous-input`:** a digit-leading pattern variable, which
  surfaces as `'Nat.suc' is not a constructor of data type Path` rather than as
  a parser error.
