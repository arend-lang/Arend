### Program Arguments Supported by `ConsoleMain`

The Arend console app uses Apache Commons CLI for argument parsing. Below is the complete list of supported arguments:

---

#### Named Options

| Short    | Long                            | Has Arg | Description                                                                          |
|----------|---------------------------------|---------|--------------------------------------------------------------------------------------|
| `-h`     | `--help`                        | No | Print help message and exit                                                          |
| `-v`     | `--version`                     | No | Print language version and exit                                                      |
| `-L`     | `--libdir`                      | Yes (`dir`) | Directory containing libraries (can be specified multiple times)                     |
| `-s`     | `--sources`                     | Yes (`dir`) | Project source directory                                                             |
| `-e`     | `--extensions`                  | Yes (`dir`) | Language extensions directory                                                        |
| `-m`     | `--extension-main`              | Yes (`class`) | Main extension class name                                                            |
| `-c`     | `--double-check`                | No | Double-check correctness of the typechecking result                                  |
| `-i`     | `--interactive`                 | Optional (`type`) | Start an interactive REPL; `type` can be `plain` or `jline` (default is `jline`)     |
| `-p`     | `--print`                       | Yes (`target`) | Print a definition or a module; format: `Module.Path` or `Module.Path:DefinitionName` |
| `-ps`    | `--proof-search`                | Yes (`pattern`) | Run proof searcher                                                                   |
| `-r`     | `--recompile`                   | No | Recompile all modules from source, ignoring binary caches (`.arc` files)             |
| `-t`     | `--test`                        | No | Run tests                                                                            |
| *(none)* | `--show-times`                  | No | Show typechecking times per definition                                               |
| *(none)* | `--show-sizes`                  | No | Show sizes (expression node counts) of typechecked definitions, sorted descending    |
| *(none)* | `--show-modules`                | No | Show module dependency cycles                                                        |
| *(none)* | `--show-modules-with-instances` | No | Show module dependency cycles, filtered to modules that contain instances            |

---

#### Positional Arguments (FILES)

Usage: `arend [FILES]`

Each positional argument is interpreted as one of the following:
- **A directory path** — treated as a library root (looks for `arend.yaml` inside)
- **A path ending in `arend.yaml`** — loaded directly as a library config file
- **A `.zip` file** — loaded as a zipped library
- **A library name** (without path) — searched in the library directories specified by `-L` (or the default libraries root)
- **A module path** (e.g., `Category.Functor`) — a specific module to typecheck within the loaded libraries
- **A path to a module and a definition within it, separated by colon, e.g. `Category.Functor:FullyFaithfulFunctor.inverse` - a specific definition to typecheck within the loaded libraries**

---

#### Notes
- If `-L` is not specified, the default libraries root directory is used automatically.
- If no files/modules are specified and no `-s` is given, the tool looks for `arend.yaml` in the current directory.
- `-i` (`--interactive`) launches a REPL and skips typechecking entirely.
- `-h` and `-v` cause the program to exit immediately after printing output.
- After typechecking, compiled definitions are persisted to `.arc` files in the library's binary directory (configured via `binariesDir` in `arend.yaml`). On subsequent runs, these caches are loaded automatically unless `-r` is specified.
- `-r` (`--recompile`) forces all modules to be re-typechecked from source, ignoring any existing `.arc` files. The resulting definitions are still persisted at the end.

