package org.arend.intention

import com.intellij.openapi.command.WriteCommandAction
import org.arend.*
import org.arend.codeInsight.ArendImportOptimizer
import org.arend.codeInsight.removeFindings
import org.arend.naming.reference.LongUnresolvedReference
import org.arend.psi.ArendPsiFactory
import org.arend.server.imports.ImportUsageData
import org.arend.term.group.ConcreteNamespaceCommand
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

    // \using only adds renamings to what the command brings in anyway: with the last one gone the
    // command stays, as a plain \import
    fun `test unused using list`() {
        doSoftTest("""
            -- ! Foo.ard
            \func f => 1
            \func g => 2
            \func k => 3
            -- ! Main.ard
            \import Foo \using (g, k)

            \func h => f
        """, """
            \import Foo

            \func h => f
        """)
    }

    fun `test unused using list with hiding`() {
        doSoftTest("""
            -- ! Foo.ard
            \func f => 1
            \func g => 2
            \func k => 3
            -- ! Main.ard
            \import Foo \using (g) \hiding (k)

            \func h => f
        """, """
            \import Foo \hiding (k)

            \func h => f
        """)
    }

    /** A finding made from [stale], an older parse of the file, for its command number [index]. */
    private fun staleFinding(stale: String, index: Int): ImportUsageData.Part {
        val command = ArendPsiFactory(project).createFromText(stale)!!.statements[index].statCmd!!
        return ImportUsageData.Part(ConcreteNamespaceCommand(command, true, LongUnresolvedReference(null, null, listOf(command.longName!!.text)), true, emptyList(), emptyList()), null)
    }

    private fun doStaleTest(stale: String, index: Int, after: String) {
        myFixture.configureByText("Main.ard", "\\import Foo\n\\import Bar\n")
        val finding = staleFinding(stale, index)
        WriteCommandAction.runWriteCommandAction(project) { removeFindings(myFixture.file as ArendFile, listOf(finding)) }
        myFixture.checkResult(after)
    }

    // the server's findings may come from an older parse: the element at the same range is taken
    // over only if it is still the same text, rather than whatever an edit has moved there
    fun `test stale finding is applied to the same element`() =
        doStaleTest("\\import Foo\n\\import Baz\n", 0, "\\import Bar\n")

    fun `test stale finding is not applied to a different element`() =
        doStaleTest("\\import Bar\n\\import Foo\n", 0, "\\import Foo\n\\import Bar\n")

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
             
             \func p => f Nat.+ g Nat.+ h
        """, """
            \import Aaa
            \import Bbb (g)
            \import Ccc
            
            \func p => f Nat.+ g Nat.+ h
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

    fun `test import using an alias without a renaming`() = checkNoQuickFixesWithMultifile(
        ArendBundle.message("arend.optimize.imports.intention.name"), """
            -- ! Foo.ard
            \func foo \alias fu (a : Nat) => a
            -- ! Main.ard
            \import Foo {-caret-}(fu)

            \func lol => 1 Nat.+ fu 2
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