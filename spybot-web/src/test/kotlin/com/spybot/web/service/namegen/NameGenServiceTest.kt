package com.spybot.web.service.namegen

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class NameGenServiceTest {
    private val service = NameGenService()
    private val pool = service.buildPool()
    private val names = pool.map { it.displayName }

    @Test
    fun `the specified examples emerge from the phonetics alone`() {
        assertTrue("Carry Potter" in names, "expected Carry Potter in $names")
        assertTrue("Gregor Easy" in names, "expected Gregor Easy in $names")
        assertTrue("Harald Flash" in names, "expected Harald Flash in $names")
    }

    @Test
    fun `none of the specified examples are hardcoded anywhere in main`() {
        val mainSources =
            File("src/main/kotlin")
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .joinToString("\n") { it.readText() }
        for (name in listOf("Carry Potter", "Gregor Easy", "Harald Flash")) {
            assertTrue(name !in mainSources, "'$name' must not appear literally in main sources")
        }
    }

    @Test
    fun `the pool is large enough to feel varied`() {
        assertTrue(pool.size >= 20, "only ${pool.size} names in pool: $names")
    }

    @Test
    fun `generated names are two capitalised words that differ from the source name`() {
        repeat(200) {
            val name = service.generate()
            val words = name.displayName.split(" ")
            assertEquals(2, words.size, name.displayName)
            assertTrue(words.all { it.first().isUpperCase() }, name.displayName)
            assertTrue(name.displayName != name.realName, name.displayName)
        }
    }

    @Test
    fun `every pool entry clears the threshold`() {
        assertTrue(pool.all { it.score >= PhoneticMatcher.THRESHOLD })
    }

    @Test
    fun `find returns the pool entry for a generated name and nothing for anything else`() {
        assertEquals("Harry Potter", service.find("Carry Potter")?.realName)
        assertEquals(null, service.find("Totally Madeup"))
        assertEquals(null, service.find("carry potter"), "lookups are exact - the client sends back what we rendered")
    }

    @Test
    fun `dump pool for review`() {
        // Not an assertion - the quality gate is a human reading this in the test output.
        println("---- generated pool (${pool.size}) ----")
        pool.sortedByDescending { it.score }.forEach {
            println("%.3f  %-28s <- %s × %s".format(it.score, it.displayName, it.realName, it.slang))
        }
        println("---- end pool ----")
    }
}
