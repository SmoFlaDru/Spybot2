import java.time.Instant

buildscript {
    dependencies {
        // Used by generateChangelog to attribute CHANGELOG.md bullets to master commits at build
        // time; the Docker build image has no git binary, so this must be pure Java.
        classpath("org.eclipse.jgit:org.eclipse.jgit:7.8.0.202609011348-r")
    }
}

plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("gg.jte.gradle") version "3.2.3"
    id("com.gorylenko.gradle-git-properties") version "4.0.1"
}

dependencyManagement {
    imports {
        // The BOM that matches the Spring Boot Gradle plugin version declared in the root build
        // script - the one place to bump Spring Boot.
        mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
    }
}

// Resolves the Sentry OpenTelemetry javaagent jar so it can be copied into the Docker image,
// keeping its version in lockstep with sentryVersion instead of a hardcoded download URL.
val sentryAgent by configurations.creating

dependencies {
    implementation(project(":spybot-core"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.security:spring-security-webauthn")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    // No version on purpose: webauthn4j is used directly only to parse attestation objects, and
    // it must stay on exactly the release spring-security-webauthn is compiled against (a newer
    // patch already broke binary compatibility once). Gradle takes the transitive version.
    implementation("com.webauthn4j:webauthn4j-core")
    // Templates are generated to Kotlin at build time (see the jte block); only the runtime is
    // needed to execute them, and controllers call the generated classes directly.
    implementation("gg.jte:jte-runtime:${providers.gradleProperty("jteVersion").get()}")
    jteGenerate("gg.jte:jte-kotlin:${providers.gradleProperty("jteVersion").get()}")
    implementation(kotlin("reflect"))
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.springframework.boot:spring-boot-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    implementation("io.sentry:sentry-spring-boot-4:${providers.gradleProperty("sentryVersion").get()}")
    implementation("io.sentry:sentry-logback:${providers.gradleProperty("sentryVersion").get()}")
    sentryAgent("io.sentry:sentry-opentelemetry-agent:${providers.gradleProperty("sentryVersion").get()}")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:junit-jupiter:${providers.gradleProperty("testcontainersVersion").get()}")
    testImplementation("org.testcontainers:postgresql:${providers.gradleProperty("testcontainersVersion").get()}")
}

val frontendOutputDir = layout.projectDirectory.dir("../frontend/output")
val generatedFrontendStaticDir = layout.buildDirectory.dir("generated-resources/frontend-static")

val prepareFrontendAssets =
    tasks.register<Sync>("prepareFrontendAssets") {
        onlyIf {
            frontendOutputDir.file("main.js").asFile.exists() && frontendOutputDir.file("main.css").asFile.exists()
        }
        from(frontendOutputDir)
        into(generatedFrontendStaticDir)
    }

// CHANGELOG.md is a flat list of bullets. This task uses git to work out which master commit
// added each bullet (walking first-parent history, so a merged PR's bullets belong to its merge
// commit) and writes the grouped result as JSON for the /changelog page. Tags on a commit come
// along, which is how a release shows up - the file itself never mentions versions or hashes.
val generatedChangelogDir = layout.buildDirectory.dir("generated-resources/changelog")

val generateChangelog =
    tasks.register("generateChangelog") {
        val changelogFile = rootProject.file("CHANGELOG.md")
        val gitDir =
            rootProject.layout.projectDirectory
                .dir(".git")
                .asFile
        val outputFile = generatedChangelogDir.map { it.file("changelog.json") }
        inputs.file(changelogFile)
        outputs.file(outputFile)
        // Git state is an input too but far too fiddly to declare; the task takes milliseconds.
        outputs.upToDateWhen { false }
        doLast {
            val sections = changelogSections(changelogFile, gitDir, logger)
            val json =
                groovy.json.JsonOutput.toJson(
                    sections.map {
                        mapOf("commit" to it.commit, "committedAt" to it.committedAt, "tags" to it.tags, "bullets" to it.bullets)
                    },
                )
            outputFile.get().asFile.apply {
                parentFile.mkdirs()
                writeText(groovy.json.JsonOutput.prettyPrint(json))
            }
            logger.lifecycle("Changelog: {} bullets in {} sections", sections.sumOf { it.bullets.size }, sections.size)
        }
    }

tasks.named<ProcessResources>("processResources") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(prepareFrontendAssets) {
        into("static")
    }
    from(generateChangelog)
}

gitProperties {
    dotGitDirectory.set(rootProject.layout.projectDirectory.dir(".git"))
}

springBoot {
    buildInfo()
}

jte {
    // generate() turns every .kte into a Kotlin source file compiled with the rest of the module,
    // so a controller calls Jte<Page>Generated.render(output, null, param = ...) directly and the
    // compiler checks the template's parameters at the call site. (precompile() compiles the
    // templates as a separate unit, which is what forced the old Model/view-name indirection.)
    generate()
    packageName.set("gg.jte.generated")
}

tasks.named("compileKotlin") {
    dependsOn(tasks.named("generateJte"))
}

tasks.register("verifyFrontendAssets") {
    doLast {
        val requiredAssets =
            listOf(
                frontendOutputDir.file("main.js").asFile,
                frontendOutputDir.file("main.css").asFile,
            )
        val missing = requiredAssets.filterNot { it.exists() }
        if (missing.isNotEmpty()) {
            error(
                buildString {
                    appendLine("Missing frontend build artifacts:")
                    missing.forEach { appendLine(" - ${it.absolutePath}") }
                    append(
                        "Run `npm ci && npm run package` in `frontend/`, or build via Docker which now performs the frontend packaging step.",
                    )
                },
            )
        }
    }
}

tasks.named<Jar>("jar") {
    enabled = false
}

val copySentryAgent =
    tasks.register<Copy>("copySentryAgent") {
        from(sentryAgent)
        into(layout.buildDirectory.dir("sentry-agent"))
        rename { "sentry-opentelemetry-agent.jar" }
    }

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    dependsOn(tasks.named("verifyFrontendAssets"), copySentryAgent)
    archiveFileName.set("app.jar")
}

data class ChangelogSection(
    val commit: String?,
    val committedAt: String?,
    val tags: List<String>,
    val bullets: List<String>,
)

fun changelogBullets(text: String): List<String> =
    text
        // The header documents the format; never mistake an example there for an entry.
        .replace(Regex("(?s)```.*?```"), "")
        .lines()
        .filter { it.startsWith("- ") }
        .map { it.removePrefix("- ").trim() }

/**
 * Bullets in [child] that are not in [parent], in [child]'s order. Compared as a multiset so an
 * exact duplicate bullet (rare, but possible) is still attributed to whichever commit added it.
 */
fun addedBullets(
    child: List<String>,
    parent: List<String>,
): List<String> {
    val remaining = parent.groupingBy { it }.eachCount().toMutableMap()
    return child.filter { bullet ->
        val left = remaining[bullet] ?: 0
        if (left > 0) {
            remaining[bullet] = left - 1
            false
        } else {
            true
        }
    }
}

fun changelogSections(
    changelogFile: File,
    gitDir: File,
    logger: org.gradle.api.logging.Logger,
): List<ChangelogSection> {
    val workingTreeBullets = changelogBullets(changelogFile.readText())
    if (!gitDir.exists()) {
        logger.warn("No git repository at {}; the changelog page will show one unattributed section", gitDir)
        return listOf(ChangelogSection(null, null, emptyList(), workingTreeBullets))
    }
    val path = changelogFile.relativeTo(gitDir.parentFile).invariantSeparatorsPath

    org.eclipse.jgit.storage.file
        .FileRepositoryBuilder()
        .setWorkTree(gitDir.parentFile)
        .findGitDir(gitDir.parentFile)
        .build()
        .use { repo ->
            val tagsByCommit = mutableMapOf<org.eclipse.jgit.lib.ObjectId, MutableList<String>>()
            org.eclipse.jgit.revwalk.RevWalk(repo).use { walk ->
                for (ref in repo.refDatabase.getRefsByPrefix(org.eclipse.jgit.lib.Constants.R_TAGS)) {
                    val target = walk.peel(walk.parseAny(ref.objectId)).id
                    tagsByCommit.getOrPut(target) { mutableListOf() } += ref.name.removePrefix(org.eclipse.jgit.lib.Constants.R_TAGS)
                }
            }

            fun fileAt(commit: org.eclipse.jgit.revwalk.RevCommit): org.eclipse.jgit.treewalk.TreeWalk? =
                org.eclipse.jgit.treewalk.TreeWalk
                    .forPath(repo, path, commit.tree)

            fun fileExistsAt(commit: org.eclipse.jgit.revwalk.RevCommit): Boolean = fileAt(commit) != null

            fun bulletsAt(commit: org.eclipse.jgit.revwalk.RevCommit): List<String>? {
                val tree = fileAt(commit) ?: return null
                return changelogBullets(String(repo.open(tree.getObjectId(0)).bytes, Charsets.UTF_8))
            }

            val sections = mutableListOf<ChangelogSection>()
            org.eclipse.jgit.revwalk.RevWalk(repo).use { walk ->
                val headId = repo.resolve(org.eclipse.jgit.lib.Constants.HEAD)
                if (headId == null) {
                    logger.warn("Git repository has no HEAD; the changelog page will show one unattributed section")
                    return listOf(ChangelogSection(null, null, emptyList(), workingTreeBullets))
                }
                var commit: org.eclipse.jgit.revwalk.RevCommit? = walk.parseCommit(headId)
                var childBullets = bulletsAt(commit!!)

                // Bullets in the working tree that HEAD doesn't have yet: only ever seen locally.
                val uncommitted = addedBullets(workingTreeBullets, childBullets ?: emptyList())
                if (uncommitted.isNotEmpty()) sections += ChangelogSection(null, null, emptyList(), uncommitted)

                val shallowCommits = repo.objectDatabase.shallowCommits
                while (commit != null && childBullets != null) {
                    if (commit.id in shallowCommits) {
                        // JGit reports a shallow-clone boundary commit as having no parents, so the walk
                        // would stop silently and hand it every remaining bullet. Say so.
                        logger.warn(
                            "Shallow git history: attributing remaining changelog bullets to {}; use a full clone (fetch-depth: 0) for correct grouping",
                            commit.name.take(7),
                        )
                    }
                    val parent =
                        try {
                            commit.parents.firstOrNull()?.let { walk.parseCommit(it) }
                        } catch (e: org.eclipse.jgit.errors.MissingObjectException) {
                            // Shallow clone: history ends here. Everything still unattributed goes to this
                            // commit rather than failing the build - but the result is only right with a
                            // full clone (fetch-depth: 0 in CI).
                            logger.warn("Shallow git history: attributing remaining changelog bullets to {}", commit.name.take(7))
                            null
                        }
                    val parentBullets = parent?.let { bulletsAt(it) } ?: emptyList()
                    val added = addedBullets(childBullets, parentBullets)
                    if (added.isNotEmpty()) {
                        val committedAt = Instant.ofEpochSecond(commit.commitTime.toLong())
                        val tags = tagsByCommit[commit.id].orEmpty().sorted()
                        sections += ChangelogSection(commit.name.take(7), committedAt.toString(), tags, added)
                    }
                    commit = parent
                    childBullets = if (parent != null && fileExistsAt(parent)) parentBullets else null
                }
            }
            return sections
        }
}
