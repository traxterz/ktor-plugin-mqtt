plugins {
    kotlin("jvm") version "1.5.31"

    `maven-publish`
    id("org.jlleitschuh.gradle.ktlint") version "10.0.0"
    id("de.jansauer.printcoverage") version "2.0.0"
    jacoco
    id("com.github.dawnwords.jacoco.badge") version "0.2.0"
}

group = "com.github.traxterz"
version = "1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}


repositories {
    mavenCentral()
    jcenter()
}

dependencies {
    implementation(kotlin("stdlib"))
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "1.8"
        languageVersion = "1.5"
        apiVersion = "1.5"

    }
}

val sourcesJar by tasks.registering(Jar::class) {
    classifier = "sources"
    from(sourceSets["main"].allSource)
}

publishing {
    publications {
        register("mavenJava", MavenPublication::class.java) {
            from(components["java"])
            artifact(sourcesJar.get())
        }
    }
}

tasks {
    check {
        dependsOn(test)
        finalizedBy(jacocoTestReport, jacocoTestCoverageVerification, printCoverage, generateJacocoBadge)
    }

    jacocoTestReport {
        reports {
            xml.isEnabled = true
            csv.isEnabled = false
            html.isEnabled = true
        }
    }
}

ktlint {
    version.set("0.22.0")
    ignoreFailures.set(false)
}

printcoverage {
    coverageType.set("LINE")
}
