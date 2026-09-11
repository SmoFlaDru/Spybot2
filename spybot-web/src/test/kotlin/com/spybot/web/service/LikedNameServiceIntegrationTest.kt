package com.spybot.web.service

import com.spybot.core.model.Liker
import com.spybot.core.service.LikedNameService
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * One like per person per name is enforced by unique constraints and the count is an aggregate;
 * that only means something against a real Postgres with the Flyway schema applied, so this runs
 * on a Testcontainers database like [com.spybot.web.SpybotWebApplicationContextTest].
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class LikedNameServiceIntegrationTest {
    @Autowired
    lateinit var service: LikedNameService

    @Autowired
    lateinit var dsl: DSLContext

    private val alice = Liker.Visitor("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
    private val bob = Liker.Visitor("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")

    private fun newUser(name: String): Long =
        dsl.fetchOne("insert into spybot_mergeduser (password, name) values ('', ?) returning id", name)!!.get(0, Long::class.java)

    @Test
    fun `a person can like a name once and take the like back`() {
        val name = "Carry Potter"

        val first = service.like(name, "Harry Potter", "carry", alice)
        val again = service.like(name, "Harry Potter", "carry", alice)
        val other = service.like(name, "Harry Potter", "carry", bob)
        val undone = service.unlike(name, alice)

        assertEquals(1, first.likes)
        assertTrue(first.likedByMe)
        assertEquals(1, again.likes, "liking twice must not count twice")
        assertEquals(2, other.likes)
        assertEquals(1, undone.likes)
        assertFalse(undone.likedByMe)
        assertTrue(service.status(name, bob).likedByMe)
        assertFalse(service.status("Never Liked", bob).likedByMe)
    }

    @Test
    fun `logged-in users are tracked separately from visitors`() {
        val userId = newUser("liker")
        val name = "Gregor Easy"

        service.like(name, "Gregor Gysi", "easy", Liker.User(userId))
        val status = service.like(name, "Gregor Gysi", "easy", Liker.User(userId))

        assertEquals(1, status.likes)
        assertFalse(service.status(name, alice).likedByMe)
    }

    @Test
    fun `top list counts people, orders by likes, flags the viewer's own, and drops unliked names`() {
        repeat(2) { service.like("Nuke Skywalker", "Luke Skywalker", "nuke", alice) }
        service.like("Nuke Skywalker", "Luke Skywalker", "nuke", bob)
        service.like("Spawn Connery", "Sean Connery", "spawn", alice)
        service.like("Harald Flash", "Harald Lesch", "flash", bob)
        service.unlike("Harald Flash", bob)

        val top = service.top(30, alice)

        assertEquals("Nuke Skywalker", top.first().displayName)
        assertEquals(2, top.first().likes)
        assertTrue(top.first().likedByMe)
        assertTrue(top.none { it.displayName == "Harald Flash" })
        assertFalse(service.top(30, bob).first { it.displayName == "Spawn Connery" }.likedByMe)
        assertEquals(1, service.top(1, alice).size)
    }

    @Test
    fun `merging users moves their likes and keeps at most one per name`() {
        val target = newUser("target")
        val sourceA = newUser("source-a")
        val sourceB = newUser("source-b")
        val name = "Darth Baiter"
        service.like(name, "Darth Vader", "baiter", Liker.User(target))
        service.like(name, "Darth Vader", "baiter", Liker.User(sourceA))
        service.like(name, "Darth Vader", "baiter", Liker.User(sourceB))
        service.like("Bomb Hanks", "Tom Hanks", "bomb", Liker.User(sourceA))
        service.like("Bomb Hanks", "Tom Hanks", "bomb", Liker.User(sourceB))

        val moved = service.reassignLikes(listOf(sourceA, sourceB), target)

        assertEquals(1, moved, "only the one non-duplicate like should move")
        assertEquals(1, service.status(name, Liker.User(target)).likes)
        assertEquals(1, service.status("Bomb Hanks", Liker.User(target)).likes)
        assertTrue(service.status("Bomb Hanks", Liker.User(target)).likedByMe)
        assertFalse(service.status(name, Liker.User(sourceA)).likedByMe)
    }

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17.2")

        @DynamicPropertySource
        @JvmStatic
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }
}
