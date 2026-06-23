val emfVersion: String by project

dependencies {
    implementation("org.eclipse.emf:org.eclipse.emf.ecore:$emfVersion")
    implementation("org.eclipse.emf:org.eclipse.emf.common:$emfVersion")
    implementation("org.eclipse.emf:org.eclipse.emf.ecore.xmi:$emfVersion")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
