import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    application
    id("com.gradleup.shadow") version "8.3.6"
}

version = "0.0.0"

dependencies {
    implementation(project(":core"))
    implementation("org.jcommander:jcommander:2.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("com.folmate.ecore2mermaid.cli.Main")
}

// Give the thin jar a classifier so it doesn't clash with the fat jar filename.
tasks.jar {
    archiveClassifier.set("thin")
}

tasks.named<ShadowJar>("shadowJar") {
    archiveBaseName.set("ecore2mermaid")
    archiveClassifier.set("")

    mergeServiceFiles()

    // Append the identical about.html files from all three EMF jars rather than
    // silently keeping only the first one Shadow encounters.
    append("about.html")

    // Jar signature files from individual jars would make the fat jar unverifiable.
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.EC")

    manifest {
        attributes(
            "Main-Class" to "com.folmate.ecore2mermaid.cli.Main",
            "Implementation-Title" to "ecore2mermaid",
            "Implementation-Version" to project.version,
        )
    }
}
