plugins {
    `maven-publish`
}

val emfVersion: String by project

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/folmate/emf-mermaid")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

dependencies {
    implementation("org.eclipse.emf:org.eclipse.emf.ecore:$emfVersion")
    implementation("org.eclipse.emf:org.eclipse.emf.common:$emfVersion")
    implementation("org.eclipse.emf:org.eclipse.emf.ecore.xmi:$emfVersion")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
