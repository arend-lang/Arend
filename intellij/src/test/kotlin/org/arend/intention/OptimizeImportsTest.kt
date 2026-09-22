package org.arend.intention

import com.intellij.openapi.command.WriteCommandAction
import org.arend.*
import org.arend.codeInsight.ArendImportOptimizer
import org.arend.psi.ArendFile
import org.arend.quickfix.QuickFixTestBase
import org.arend.util.ArendBundle

/**
 * What the optimizer does to the file once the server has said which commands are unused.
 *
 * Deciding that is not tested here -- it is the server's, and org.arend.server.imports
 * .UnusedImportsTest covers it. What is left is the editing: taking a name out of a list without
 * leaving a stray comma, taking a command out and the \where that held nothing else with it, and
 * sorting what remains.
 */
class OptimizeImportsTest : QuickFixTestBase() {

    private fun FileTree.prepareFileSystem(): TestProject {
        val testProject = create(myFixture.project, myFixture.findFileInTempDir("."))
        myFixture.configureFromTempProjectFile("Main.ard")
        return testProject
    }

    private fun doSoftTest(before: String, after: String, beforeTypecheck: () -> Unit = {}, afterTypecheck: () -> Unit = {}) {
        val fileTree = fileTreeFromText(before)
        fileTree.prepareFileSystem()
        beforeTypecheck()
        typecheck(fileTree.fileNames) //
        afterTypecheck()

        val optimizer = ArendImportOptimizer()
        WriteCommandAction.runWriteCommandAction(myFixture.project, optimizer.processFile(myFixture.file))
        myFixture.checkResult(replaceCaretMarker(after.trimIndent()))
    }

private val collidedDefinitions = """
        -- ! Foo.ard
        \func foo => () \where \func apply => ()

        \func bar => () \where \func apply => ()
        
    """

fun `test soft imports with class`() {
        doSoftTest("""
            -- ! Foo.ard
            \class F {
              | ff : Nat
            } \where {
              \func ff' : Nat => 1
            }
            -- ! Main.ard
            \import Foo (F, ff)
            \open F
            
            \func g {f : F} => ff
            \func h => ff'
        """, """
            \import Foo (F, ff)
            \open F
            
            \func g {f : F} => ff
            \func h => ff'
        """)
    }

    fun `test soft imports redundant open`() {
        doSoftTest("""
            -- ! Main.ard
            \module A \where {
              \module X \where {}
            
              \open X
            }
        """, """
           \module A \where {
             \module X \where {}
           }
        """)
    }

    fun `test soft imports`() {
        doSoftTest("""
            -- ! Main.ard
            \module A \where {
              \module B \where {
                \func f => 1
                \where {
                  \func g => {?}
                }
                \open f
            
                \func h => g
              }
            }
        """, """
            \module A \where {
              \module B \where {
                \func f => 1
                \where {
                  \func g => {?}
                }
                \open f
            
                \func h => g
              }
            }
        """)
    }

    fun `test soft imports 2`() {
        doSoftTest("""
            -- ! Main.ard
            \module A \where {
              \module B \where {
                \func f => 1
                \where {
                  \func g => {?}
                }
                \open B.f
            
                \func h => g
              }
            }
        """, """
            \module A \where {
              \module B \where {
                \func f => 1
                \where {
                  \func g => {?}
                }
                \open B.f
            
                \func h => g
              }
            }
        """)
    }

    fun `test soft imports 3`() {
        doSoftTest("""
            -- ! Foo.ard
            \func f => 1
            \func f' => 2
            -- ! Bar.ard
            \func h' => 3
            \func h => 3
            \func h'' => 3
            -- ! Baz.ard
            \func i => 4
            \func i' => 4
            \func i'' => 4
            -- ! Qux.ard
            \func j => 4
            \func j' => 4
            -- ! Main.ard
            \import Foo (f, f')
            \import Bar (h', h, h'')
            \import Baz (i', i, i'')
            \import Qux (j, j')
            \func r => f
            \func r' => h
            \func r'' => i'
            \func r''' => i''
            \func r'''' => j'
        """, """
            \import Bar (h)
            \import Baz (i', i'')
            \import Foo (f)
            \import Qux (j')

            \func r => f
            \func r' => h
            \func r'' => i'
            \func r''' => i''
            \func r'''' => j'
        """)
    }

    fun `test soft imports 4`() {
        doSoftTest("""
            -- ! Aaa.ard
            \func f => 1
            -- ! Bbb.ard
            \func g => 1
            -- ! Ccc.ard
            \func h => 1
            -- ! Main.ard
            \import Ccc
            
            \import Bbb (g)
             
            \import Aaa 
             
             \func p => f + g + h
        """, """
            \import Aaa
            \import Bbb (g)
            \import Ccc
            
            \func p => f + g + h
        """)
    }

    fun `test soft imports 5`() {
        doSoftTest("""
            -- ! Main.ard
            \class R | n : Nat
            
            \func ff {_ : R} => 10
            
            \instance G' : R | n => 2
            
            \module M \where {
              \open Main (G')
            
              \func f : Nat => ff
            }
        """, """
            \class R | n : Nat
            
            \func ff {_ : R} => 10
            
            \instance G' : R | n => 2
            
            \module M \where {
              \open Main (G')
            
              \func f : Nat => ff
            }
        """)
    }

    fun `test soft imports 6`() {
        doSoftTest("""
            -- ! Main.ard
            \module M \where \func f => 1
            
            \func g => 1
            \where { \open M }
        """, """
            \module M \where \func f => 1
            
            \func g => 1
            
        """)
    }

    fun `test soft imports 7`() {
        doSoftTest("""
            -- ! Foo.ard
            \class D { k : Nat }
            -- ! Bar.ard
            \import Foo
           
            \instance x : D \cowith
              | k => 2
            -- ! Main.ard
            \import Foo
            \import Bar
            
            \func e {d : D} => d.k

            \func hh => 1 \where {
              \func h : Nat => e
            }
        """, """
            \import Bar
            \import Foo
            
            \func e {d : D} => d.k

            \func hh => 1 \where {
              \func h : Nat => e
            }
        """)
    }

    fun `test class in module`() {
        doSoftTest("""
            -- ! Main.ard
            \module M \where {
              \record R
                | field : Nat
            }
            
            \func asdzxc => field
              \where
                \open M.R(field)
        """, """
            \module M \where {
              \record R
                | field : Nat
            }
            
            \func asdzxc => field
              \where
                \open M.R(field)
        """)
    }

    fun `test local shadowing`() {
        doSoftTest("""
            -- ! Main.ard
            \open B(foo)

            \module A \where {
              \func foo => 1
            }
            
            \module B \where {
              \func foo => 2
            }
            
            \func r : foo = 2 => idp
            
            \func q : foo = 1 => idp \where \open A
        """, """
            \open B(foo)

            \module A \where {
              \func foo => 1
            }
            
            \module B \where {
              \func foo => 2
            }
            
            \func r : foo = 2 => idp
            
            \func q : foo = 1 => idp \where \open A
        """)
    }

    fun `test class field reimport`() {
        doSoftTest("""
            -- ! Main.ard
            \data Unit | unit
            
            \class Op {
              | f : Unit
            }
            
            \lemma foo {o : Op} : Unit => f'
            \where {
              \open Op(f \as f')
            }
        """, """
            \data Unit | unit
            
            \class Op {
              | f : Unit
            }
            
            \lemma foo {o : Op} : Unit => f'
            \where {
              \open Op(f \as f')
            }
        """)
    }

    fun testOptimizeImports() = checkNoQuickFixes(
        ArendBundle.message("arend.optimize.imports.intention.name"), """
       \module M \where {
         \func foo \alias fu (a : Nat) => a
       }

       \module M1 \where {
         \open {-caret-}M (fu \as foobar)

         \func lol => 1 Nat.+ foobar 2
       }
    """)

    // `\using (<alias>)` without an `\as` is legal and exports the alias, so the item is in use
    fun `test open using an alias without a renaming`() = checkNoQuickFixes(
        ArendBundle.message("arend.optimize.imports.intention.name"), """
       \module M \where {
         \func foo \alias fu (a : Nat) => a
       }

       \module M1 \where {
         \open {-caret-}M (fu)

         \func lol => 1 Nat.+ fu 2
       }
    """)

    fun `test import using an alias without a renaming`() = checkNoQuickFixesWithMultifile(
        ArendBundle.message("arend.optimize.imports.intention.name"), """
            -- ! Foo.ard
            \func foo \alias fu (a : Nat) => a
            -- ! Main.ard
            \import Foo {-caret-}(fu)

            \func lol => 1 Nat.+ fu 2
    """)

    fun testOptimizeImports2() = checkNoQuickFixes(
        ArendBundle.message("arend.optimize.imports.intention.name"), """
       \module N \where \data Bool | true | false

       \module N2 \where {
         \open N (Bool, true{-caret-}, false)

          \func mcas (b : Bool) : Nat => \case b \with {
            | true => 1
            | false => 0
          }
       } 
    """)

    fun testOptimizeImports3() = checkNoQuickFixes(ArendBundle.message("arend.optimize.imports.intention.name"), """
       \module M \where {
         \func foo \alias fu (a : Nat) => a \where
           \func lol => 101
       }

       \module M1 \where {
         \open {-caret-}M (fu \as foobar)

         \func lol => 1 Nat.+ foobar.lol
       }
    """)

    fun testOptimizeImports4() = doSoftTest("""
       \module A \where {
         \func foo => 101

         \func bar => 42
       }

       \func foo => 102

       \module B \where {
         \open A (foo, bar{-caret-})

         \func fubar => foo + bar
       }
    """, """
       \module A \where {
         \func foo => 101

         \func bar => 42
       }

       \func foo => 102

       \module B \where {
         \open A (foo, bar)

         \func fubar => foo + bar
       }
    """)

    fun testOptimizeImports5() = doSoftTest("""
       \open Outer{-caret-} (Inner)

       \module Bar \where {
         \open Inner

         \func bar => foobar
       }

       \module Outer \where {
         \module Inner \where {
           \func foobar => 100
         }
       }
    """, """
       \open Outer (Inner)

       \module Bar \where {
         \open Inner

         \func bar => foobar
       }

       \module Outer \where {
         \module Inner \where {
           \func foobar => 100
         }
       }
    """)

    fun testOptimizeImports6() = checkNoQuickFixesWithMultifile(ArendBundle.message("arend.optimize.imports.intention.name"), """
        -- ! A.ard
        \module M \where {
          \instance I => {?}
        }
        
        -- ! Main.ard
        \import A(M{-caret-})
        \open M
        
        \instance f => M.I
    """)
}