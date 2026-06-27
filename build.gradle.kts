plugins {
    java
    `maven-publish`
    signing
    id("me.champeau.jmh") version "0.7.2"
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withJavadocJar()
    withSourcesJar()
}

tasks.compileJava {
    options.encoding = Charsets.UTF_8.name()
    options.compilerArgs.add("-parameters")
}

repositories {
    mavenCentral()
    maven("https://maven.blamejared.com/") // gg.moonflower:molang-compiler
}

dependencies {
    implementation("org.ow2.asm:asm:9.7")
    compileOnly("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")

    testImplementation("org.ow2.asm:asm-util:9.7")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.4")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.11.4")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Competitor MoLang implementations, compared in src/jmh against this library.
    jmh("gg.moonflower:molang-compiler:3.1.1.19")
    jmh("team.unnamed:mocha:3.0.1")
}

group = "org.cloudburstmc"

// Version follows the git ref: a pushed tag publishes a release named after the tag (e.g. tag
// "0.0.1" -> 0.0.1); every other build (main, PRs, local) is "<base>-SNAPSHOT". GITHUB_REF_TYPE /
// GITHUB_REF_NAME are set by GitHub Actions and propagate into the reusable deploy workflow.
val baseVersion = "0.0.1"
version = if (System.getenv("GITHUB_REF_TYPE") == "tag") {
    System.getenv("GITHUB_REF_NAME").removePrefix("v")
} else {
    "$baseVersion-SNAPSHOT"
}

jmh {
    // Report bytes-allocated-per-op (gc.alloc.rate.norm) — the key signal for the
    // allocation-elimination optimisations measured by the W1–W7 workload matrix.
    profilers.add("gc")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

publishing {
    repositories {
        maven {
            name = "maven-deploy"
            // CI supplies these; the fallbacks let `./gradlew build` work locally without publishing.
            url = uri(
                System.getenv("MAVEN_DEPLOY_URL")
                    ?: "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
            )
            credentials {
                username = System.getenv("MAVEN_DEPLOY_USERNAME") ?: "username"
                password = System.getenv("MAVEN_DEPLOY_PASSWORD") ?: "password"
            }
        }
    }

    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                packaging = "jar"
                name.set("MoJava")
                description.set("A fast MoLang parser, compiler, and runtime for Java")
                url.set("https://github.com/CloudburstMC/MoJava")

                scm {
                    connection.set("scm:git:git://github.com/CloudburstMC/MoJava.git")
                    developerConnection.set("scm:git:ssh://github.com/CloudburstMC/MoJava.git")
                    url.set("https://github.com/CloudburstMC/MoJava")
                }

                licenses {
                    license {
                        name.set("The Apache Software License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }

                developers {
                    developer {
                        name.set("CloudburstMC Team")
                        organization.set("CloudburstMC")
                        organizationUrl.set("https://github.com/CloudburstMC")
                    }
                }
            }
        }
    }
}

signing {
    // Only sign when the CI provides the in-memory PGP key, so local builds don't need a keyring.
    if (System.getenv("PGP_SECRET") != null && System.getenv("PGP_PASSPHRASE") != null) {
        useInMemoryPgpKeys(System.getenv("PGP_SECRET"), System.getenv("PGP_PASSPHRASE"))
        sign(publishing.publications["maven"])
    }
}
