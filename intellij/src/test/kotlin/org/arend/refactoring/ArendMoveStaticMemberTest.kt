package org.arend.refactoring

class ArendMoveStaticMemberTest: ArendMoveTestBase() {

    fun testSimpleMove1() =
        testMoveRefactoring("""
             --! Main.ard
            \func abc{-caret-} => 1
            \module def \where {}
            """, """
            \open def (abc)

            \module def \where {
              \func abc => 1
            }
            """, "Main", "def")

    fun testSimpleMove2() =
            testMoveRefactoring("""
             --! Main.ard
            \func abc{-caret-} => 1
            \func foo => 2 \where
              \func bar => 3
            """, """
            \open foo (abc)

            \func foo => 2 \where {
              \func bar => 3

              \func abc => 1
            }
            """, "Main", "foo")

    fun testForbiddenRefactoring1() =
            testMoveRefactoring("""
             --! Main.ard
            \func foo{-caret-} => 2 \where
              \func bar => 3
            """, null, "Main", "foo.bar")

    fun testSimpleMove3() =
            testMoveRefactoring("""
             --! Main.ard
            \func abc{-caret-} => 1
            \module Foo
            \func bar => abc
            """, """
            \open Foo (abc)

            \module Foo \where {
              \func abc => 1
            }

            \func bar => abc
            """, "Main", "Foo")

    fun testForbiddenRefactoring2() =
            testMoveRefactoring("""
             --! Main.ard
            \func foo{-caret-} => 0
            \module Foo \where {
              \func foo => 1
            }
            """, null, "Main", "Foo")

    fun testLongName1() =
            testMoveRefactoring("""
             --! Main.ard
            \module Foo \where {
              \func foo{-caret-} => 1

              \func bar => 2
            }

            \func foobar => Foo.foo
            """, """
            \module Foo \where {
              \func bar => 2 \where {
                \func foo => 1
              }
            }

            \func foobar => Foo.bar.foo
            """, "Main", "Foo.bar")

    fun testMoveModule() =
            testMoveRefactoring("""
             --! Main.ard
            \module abc{-caret-} \where {}
            \module def \where {}
            """, """
            \open def (abc)

            \module def \where {
              \module abc \where {}
            }
            """, "Main", "def")

    fun testMovedContent1() =
            testMoveRefactoring("""
                 --! DirB/Main.ard
                \module Foo \where {
                  \func foo => 101
                }

                \func foo => 202
                \func bar{-caret-} => foo
            """, """
                \import DirB.Main

                \module Foo \where {
                  \func foo => 101

                  \func bar => DirB.Main.foo
                }

                \func foo => 202

                \open Foo (bar)

            """, "DirB.Main", "Foo")

    fun testMoveData1() =
            testMoveRefactoring("""
                --! Main.ard
                \module Foo \where {}

                \data MyNat{-caret-}
                  | myZero
                  | myCons (n : MyNat)

                \func foo => myCons myZero
            ""","""
                \module Foo \where {
                  \data MyNat
                    | myZero
                    | myCons (n : MyNat)
                }

                \open Foo (MyNat)

                \func foo => Foo.myCons Foo.myZero
            """, "Main", "Foo")

    fun testForbiddenRefactoring3() =
            testMoveRefactoring("""
             --! Main.ard
            \data MyNat{-caret-}
              | myZero
              | myCons (n : MyNat)
            \module Foo \where {
              \func myZero => 0
            }

            \func bar => Foo.myZero

            \func lol => myZero
            """, """
            \module Foo \where {
              \func myZero => 0

              \data MyNat
                | myZero
                | myCons (n : MyNat)
            }

            \func bar => Foo.myZero

            \func lol => Foo.MyNat.myZero
            """, "Main", "Foo")

    fun testMovedContent2() =
            testMoveRefactoring("""
                 --! Main.ard
                \import Main
                \open Nat

                \module Foo{-caret-} \where {
                  \func foo => bar.foobar + Foo.bar.foobar + Main.Foo.bar.foobar

                  \func bar => 2 \where {
                    \func foobar => foo + bar
                  }
                }

                \module Bar \where {
                  \open Foo

                  \func lol => foo + Foo.foo + bar.foobar + Foo.bar.foobar
                }

                \func goo => 4
            ""","""
                \import Main
                \open Nat

                \module Bar \where {
                  \open goo.Foo

                  \func lol => foo + goo.Foo.foo + bar.foobar + goo.Foo.bar.foobar
                }

                \func goo => 4 \where {
                  \module Foo \where {
                    \func foo => bar.foobar + Foo.bar.foobar + goo.Foo.bar.foobar

                    \func bar => 2 \where {
                      \func foobar => foo + bar
                    }
                  }
                }
            """, "Main", "goo")

    fun testMovedContent3() =
            testMoveRefactoring("""
                 --! Main.ard
                \class C{-caret-} {
                  | foo : Nat

                  \func bar(a : Nat) => foobar a
                } \where {
                  \func foobar (a : Nat) => a
                }

                \module Foo \where { }

                \func lol (L : C) => (C.bar (C.foobar (foo {L})))
            ""","""
                \module Foo \where {
                  \class C {
                    | foo : Nat

                    \func bar(a : Nat) => foobar a
                  } \where {
                    \func foobar (a : Nat) => a
                  }
                }

                \func lol (L : Foo.C) => (Foo.C.bar (Foo.C.foobar (Foo.foo {L})))
            """, "Main", "Foo")

    fun testMoveData2() =
            testMoveRefactoring("""
                --! Main.ard
                \module Foo \where {}

                \data MyNat{-caret-}
                  | myZero
                  | myCons (n : MyNat)

                \func foo (m : MyNat) \elim m
                  | myZero => myZero
                  | myCons x => myCons x
                """, """
                \open Foo (MyNat, myZero, myCons)

                \module Foo \where {
                  \data MyNat
                    | myZero
                    | myCons (n : MyNat)
                }

                \func foo (m : MyNat) \elim m
                  | myZero => myZero
                  | myCons x => myCons x
                """, "Main", "Foo")

    fun testMoveStatCmds() =
            testMoveRefactoring("""
                --! Goo.ard
                \module GooM \where {
                  \func lol => 1
                }
                --! Foo.ard
                \module FooM \where {
                }
                --! Main.ard
                \import Goo
                \open GooM (lol \as lol'){-caret-}

                \func foobar => lol'
            """, """
                \import Foo
                \import Goo
                \open GooM ()
                \open FooM (lol \as lol')

                \func foobar => lol'
            """, "Foo", "FooM", "Goo", "GooM.lol")
 }