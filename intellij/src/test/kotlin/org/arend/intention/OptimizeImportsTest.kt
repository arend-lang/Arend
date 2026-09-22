package org.arend.intention

import com.intellij.openapi.command.WriteCommandAction
import org.arend.*
import org.arend.codeInsight.ArendImportOptimizer
import org.arend.psi.ArendFile
import org.arend.quickfix.QuickFixTestBase
import org.arend.util.ArendBundle

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

    fun `test prelude`() {
        doSoftTest("""
            -- ! Main.ard
            \func foo : Nat => 1
            """, """
            \func foo : Nat => 1
            """)
    }

    fun `test same-package modularized usage`() {
        doSoftTest("""
            -- ! Main.ard
            \data Bar \where {
              \data R \where {
                \func f : Nat => 1
              }
            }
            
            \func g => Bar.R.f
            """, """
            \data Bar \where {
              \data R \where {
                \func f : Nat => 1
              }
            }
            
            \func g => Bar.R.f
            """,
        )
    }

    fun `test same-package in-module usage`() {
        doSoftTest("""
            -- ! Main.ard
            \data Bar \where {
              \func foo : Nat => 1
              
              \func bar : Nat => foo
            }
            """, """
            \data Bar \where {
              \func foo : Nat => 1
              
              \func bar : Nat => foo
            }
            """,
        )
    }

private val collidedDefinitions = """
        -- ! Foo.ard
        \func foo => () \where \func apply => ()

        \func bar => () \where \func apply => ()
        
    """

    fun `test self-contained datatype`() {
        doSoftTest(
            """
            -- ! Main.ard
            \data D (a : Nat)
              | d (D a)
        """, """
            \data D (a : Nat)
              | d (D a)
        """
        )
    }

    fun `test self-contained function`() {
        doSoftTest(
            """
            -- ! Main.ard
            \func foo => bar
              \where \func bar => 1
        """, """
            \func foo => bar
              \where \func bar => 1
        """
        )
    }

    fun `test definition in where`() {
        doSoftTest(
            """
            -- ! Main.ard
            \func f => gg \where
              \data g | gg
        """, """
            \func f => gg \where
              \data g | gg
        """
        )
    }

    fun `test array`() {
        doSoftTest(
            """
            -- ! Main.ard
            \func f => \new Array { | A => \lam _ => Nat
                                    | len => 1
                                    | at (0) => 1  }
        """, """
            \func f => \new Array { | A => \lam _ => Nat
                                    | len => 1
                                    | at (0) => 1  }
        """
        )
    }

    fun `test dynamic definition`() {
        doSoftTest(
            """
            -- ! Main.ard
            \record R {
              | r : Nat
            
              \func rrr : Fin r => {?}
            }
        """, """
            \record R {
              | r : Nat
            
              \func rrr : Fin r => {?}
            }
        """
        )
    }

    fun `test record field`() {
        doSoftTest(
            """
            -- ! Main.ard
            \record R {
              | rr : Nat
            }
            
            \func f {r : R} => rr
        """, """
            \record R {
              | rr : Nat
            }
            
            \func f {r : R} => rr
        """
        )
    }

    fun `test record parameter`() {
        doSoftTest(
            """
            -- ! Main.ard
            \open R (rr)
            
            \record R (rr : Nat)

            \func f {r : R} => rr
        """, """
            \open R (rr)
            
            \record R (rr : Nat)
            
            \func f {r : R} => rr
        """
        )
    }

    fun `test two exporting classes`() {
        doSoftTest(
            """
            -- ! Main.ard
            \class A {
              | n : Nat
              \func f : Nat => n
            }
            
            \class B {
              | n : Nat
              \func f : Nat => n
            }
            
            \func h {a : A} => a.f
            \func g {b : B} => b.f
        """, """
            \class A {
              | n : Nat
              \func f : Nat => n
            }
            
            \class B {
              | n : Nat
              \func f : Nat => n
            }
            
            \func h {a : A} => a.f
            \func g {b : B} => b.f
        """
        )
    }

    fun `test extension`() {
        doSoftTest(
            """
            -- ! Main.ard
            \class R (rr : Nat)

            \class E \extends R
              | ee : Fin rr
        """, """
            \class R (rr : Nat)

            \class E \extends R
              | ee : Fin rr
        """
        )
    }

    fun `test extension3`() {
        doSoftTest(
            """
            -- ! Main.ard
            \open A (B)

            \class A \where \record B
            
            \class E \extends A {
              | f : B
            }
        """, """
            \open A (B)

            \class A \where \record B
            
            \class E \extends A {
              | f : B
            }
        """
        )
    }

    fun `test shadowed import`() {
        doSoftTest(
            """
            -- ! Main.ard
            \class A (E : \Type) {
                | + : E -> E -> E
            }
        
            \instance a : A Nat
              | + => +
            \where {
              \open Nat (+)
            } 
        """, """
            \class A (E : \Type) {
                | + : E -> E -> E
            }
        
            \instance a : A Nat
              | + => +
            \where {
              \open Nat (+)
            } 
        """
        )
    }

    fun `test can import identifier without opening a class`() {
        doSoftTest(
            """
            -- ! Foo.ard
            \class A {
              | f : Nat
            }
            -- ! Main.ard
            \import Foo (A, f)

            \func g {a : A} => f
        """, """
            \import Foo (A, f)

            \func g {a : A} => f
        """
        )
    }

    fun `test renamed import`() {
        doSoftTest(
            """
                -- ! Foo.ard
                \func f => 1
                -- ! Main.ard
                \import Foo (f \as g)
                
                \func h => g
            """, """
                \import Foo (f \as g)
                
                \func h => g
            """
        )
    }

    fun `test implicit import`() {
        doSoftTest("""
            -- ! Foo.ard
            \func f => 1
            -- ! Main.ard
            \import Foo
            
            \func g => f
        """, """
            \import Foo
            
            \func g => f
        """)
    }

    fun `test implicit import combined with open`() {
        doSoftTest("""
            -- ! Foo.ard
            \func f => 2
            \func g2 => 3
            -- ! Main.ard
            \import Foo \hiding (f)
            \open K
            
            \module K \where {
              \func f => 4
            }
            
            \func h => g2
            \func h' => f
        """, """
            \import Foo \hiding (f)
            \open K
            
            \module K \where {
              \func f => 4
            }
            
            \func h => g2
            \func h' => f
        """)
    }

    fun `test implicit imports with class`() {
        doSoftTest("""
            -- ! Foo.ard
            \class F {
              | ff : Nat
            } \where {
              \func ff' : Nat => 1
            }
            -- ! Main.ard
            \import Foo
            \open F
            
            \func g {f : F} => ff
            \func h => ff'
        """, """
            \import Foo
            \open F
            
            \func g {f : F} => ff
            \func h => ff'
        """)
    }

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