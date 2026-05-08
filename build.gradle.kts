plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.intellij.platform") version "2.2.1" apply false
    application
}

group = "dev.neel.j2keval"
version = "1.0.0"

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("j2keval.MainKt")
}

tasks.test {
    useJUnitPlatform()
}

dependencies {
    testImplementation(kotlin("test"))
}
