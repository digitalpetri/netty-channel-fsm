import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `java-library`
    kotlin("jvm") version "1.4.30"
    `maven-publish`
    signing
}

group = "com.digitalpetri.netty"
version = "0.7"

repositories {
    mavenCentral()
}

dependencies {
    api("com.digitalpetri.fsm:strict-machine:0.5")

    // BYO SLF4J
    compileOnly("org.slf4j:slf4j-api:1.7.+")

    // BYO Netty
    compileOnly("io.netty:netty-handler:4.0+")

    testImplementation(kotlin("stdlib", "1.4.30"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.0.1")
    testImplementation("io.netty:netty-handler:4.0+")
    testImplementation("io.netty:netty-transport:4.0+")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.3.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.3.1")
    testRuntimeOnly("org.slf4j:slf4j-simple:1.7.25")
}

repositories {
    mavenLocal()
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "1.8"
        targetCompatibility = "1.8"
    }

    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
    }

    withType<Test> {
        useJUnitPlatform()

        testLogging {
            events("PASSED", "FAILED", "SKIPPED")
        }
    }
}

task<Jar>("sourcesJar") {
    from(sourceSets.main.get().allJava)
    archiveClassifier.set("sources")
}

task<Jar>("javadocJar") {
    from(tasks.javadoc)
    archiveClassifier.set("javadoc")
}

tasks.withType<Jar> {
    manifest {
        attributes("Automatic-Module-Name" to "com.digitalpetri.netty.fsm")
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "netty-channel-fsm"

            from(components["java"])

            artifact(tasks["sourcesJar"])
            artifact(tasks["javadocJar"])

            pom {
                name.set("Netty Channel FSM")
                description.set("An FSM that manages async non-blocking access to a Netty Channel")
                url.set("https://github.com/digitalpetri/netty-channel-fsm")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }

                developers {
                    developer {
                        id.set("kevinherron")
                        name.set("Kevin Herron")
                        email.set("kevinherron@gmail.com")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com.com/digitalpetri/netty-channel-fsm.git")
                    developerConnection.set("scm:git:ssh://github.com.com/digitalpetri/netty-channel-fsm.git")
                    url.set("https://github.com/digitalpetri/netty-channel-fsm")
                }
            }
        }
    }

    repositories {
        maven {
            credentials {
                username = project.findProperty("ossrhUsername") as String?
                password = project.findProperty("ossrhPassword") as String?
            }

            // change URLs to point to your repos, e.g. http://my.org/repo
            val releasesRepoUrl = uri("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
            val snapshotsRepoUrl = uri("https://oss.sonatype.org/content/repositories/snapshots/")
            url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
        }

        mavenLocal()
    }
}

signing {
    useGpgCmd()
    sign(publishing.publications["mavenJava"])
}

tasks.javadoc {
    if (JavaVersion.current().isJava9Compatible) {
        (options as StandardJavadocDocletOptions).addBooleanOption("html5", true)
    }
}
