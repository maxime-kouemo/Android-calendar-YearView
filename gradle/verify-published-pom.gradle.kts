// Shared publication guardrail, applied by every library module that depends on :core.
//
// This library shipped for a while with `implementation(project(":core"))`, which put
// `core` at `runtime` scope in the generated POM. Every type in the public signature of
// YearView (and of the Compose API) comes from `core`, so consumers of the published AAR
// could not compile against it at all — and nothing in the build caught it, because the
// AAR itself assembled perfectly. The POM is the only artefact where the mistake is
// observable before release, so it is what we assert on.
//
// Wired into `check`, so `./gradlew check` in CI fails on a regression.

/** Maven coordinates the published POM must declare at `compile` scope. */
val requiredGroupId = "com.mamboa.yearview"
val requiredArtifactId = "core"

val verifyPomDeclaresCore = tasks.register("verifyPomDeclaresCore") {
    group = "verification"
    description =
        "Asserts the published POM declares $requiredGroupId:$requiredArtifactId at compile scope."

    dependsOn("generatePomFileForReleasePublication")

    val pomFile = layout.buildDirectory.file("publications/release/pom-default.xml")
    inputs.file(pomFile)

    doLast {
        val pom = pomFile.get().asFile
        val document = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(pom)

        val dependencies = document.getElementsByTagName("dependency")
        var scope: String? = null

        for (i in 0 until dependencies.length) {
            val children = dependencies.item(i).childNodes
            val fields = mutableMapOf<String, String>()
            for (j in 0 until children.length) {
                val child = children.item(j)
                if (child.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
                    fields[child.nodeName] = child.textContent.trim()
                }
            }
            if (fields["groupId"] == requiredGroupId &&
                fields["artifactId"] == requiredArtifactId
            ) {
                // Maven's default when <scope> is absent.
                scope = fields["scope"] ?: "compile"
                break
            }
        }

        when (scope) {
            null -> throw GradleException(
                "Published POM for ':${project.name}' does not declare " +
                    "$requiredGroupId:$requiredArtifactId.\n" +
                    "The public API is expressed in `core` types, so the AAR is unusable " +
                    "without it. Check that :core is an `api` dependency and that both " +
                    "projects set `group` and `version`.\nPOM: ${pom.absolutePath}"
            )

            "compile" -> Unit

            else -> throw GradleException(
                "Published POM for ':${project.name}' declares " +
                    "$requiredGroupId:$requiredArtifactId at '$scope' scope, expected " +
                    "'compile'. Use `api(project(\":core\"))`, not `implementation`, or " +
                    "consumers cannot compile against the public API.\n" +
                    "POM: ${pom.absolutePath}"
            )
        }
    }
}

tasks.named("check") { dependsOn(verifyPomDeclaresCore) }
