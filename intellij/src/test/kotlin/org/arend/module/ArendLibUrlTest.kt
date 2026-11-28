package org.arend.module

import org.arend.util.VersionRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * The URLs the plugin downloads arend-lib from, checked against this repository and the release workflow, without a
 * network. In 1.11.0 the plugin read `master/versions`, which no longer existed once arend-lib moved into this repository,
 * and in 1.7.0 `versions` named a release without its patch component, so that no tag matched it.
 */
class ArendLibUrlTest {
    private val repositoryRoot: Path = generateSequence(Paths.get("").toAbsolutePath()) { it.parent }
        .firstOrNull { Files.isRegularFile(it.resolve("settings.gradle.kts")) && Files.isDirectory(it.resolve(AREND_LIB)) }
        ?: throw IllegalStateException("Cannot find the repository root above ${Paths.get("").toAbsolutePath()}")

    @Test
    fun testVersionsUrlNamesAFileOnMaster() {
        val prefix = "https://raw.githubusercontent.com/$AREND_REPO/master/"
        assertTrue(AREND_LIB_VERSIONS_URL, AREND_LIB_VERSIONS_URL.startsWith(prefix))
        val path = AREND_LIB_VERSIONS_URL.removePrefix(prefix)
        assertTrue("$path is not a file in the repository", Files.isRegularFile(repositoryRoot.resolve(path)))
    }

    @Test
    fun testZipUrlNamesAReleaseAsset() {
        // The tag that release.yml publishes for version 1.12.0, and the asset name that the releaseAssets task gives the zip
        assertEquals("https://github.com/arend-lang/Arend/releases/download/v1.12.0/arend-lib.zip", arendLibZipUrl("1.12.0"))
    }

    @Test
    fun testVersionsNameReleases() {
        val lines = Files.readAllLines(repositoryRoot.resolve("$AREND_LIB/versions")).filter { it.isNotBlank() }
        assertTrue(lines.isNotEmpty())
        for (line in lines) {
            val parts = line.split("->").map { it.trim() }
            assertEquals("'$line' must be '<language versions> -> <release>'", 2, parts.size)
            assertNotNull("'$line': cannot parse '${parts[0]}'", VersionRange.parseVersionRange(parts[0]))
            assertTrue("'$line': a release has three components, like 1.12.0", Regex("""\d+\.\d+\.\d+""").matches(parts[1]))
        }
    }
}
