val javaRelease: String by project
val buildJdkVersion: String by project

subprojects {
    apply(plugin = "java")

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(buildJdkVersion.toInt()))
        }
    }

    tasks.withType<JavaCompile> {
        options.release.set(javaRelease.toInt())
    }

    repositories {
        mavenCentral()
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
