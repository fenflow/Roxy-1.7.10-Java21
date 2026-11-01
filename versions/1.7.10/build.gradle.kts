import java.util.zip.ZipFile

plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

version = "0.4.1-gtnh-alpha"

// The convention plugin otherwise captures a Git-derived version before this override.
tasks.named<com.gtnewhorizons.retrofuturagradle.mcp.InjectTagsTask>("injectTags") {
    tags.put("VERSION", project.version.toString())
}

val externalVoxy = providers.gradleProperty("roxyVoxyJar")
val voxyCompileStubs = sourceSets.create("voxyCompileStubs")
val voxyBridge = sourceSets.create("voxyBridge")
voxyBridge.compileClasspath += sourceSets.main.get().output
voxyBridge.compileClasspath += voxyCompileStubs.output

tasks.named<JavaCompile>(voxyBridge.compileJavaTaskName) {
    javaCompiler.set(javaToolchains.compilerFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    sourceCompatibility = "21"
    targetCompatibility = "21"
    options.release.set(21)
    doFirst {
        check(externalVoxy.isPresent && file(externalVoxy.get()).isFile) {
            "Supply the original external Voxy JAR with -ProxyVoxyJar=<path>; it is required to compile the bridge."
        }
    }
}

tasks.jar {
    from(voxyBridge.output)
    manifest.attributes("FMLCorePlugin" to "net.rasanovum.roxy.legacy.loader.RoxyCorePlugin", "FMLCorePluginContainsFMLMod" to "true")
}
tasks.named<Jar>("sourcesJar") { from(voxyBridge.allSource) }
sourceSets.test.get().runtimeClasspath += voxyBridge.output

dependencies {
    compileOnly("it.unimi.dsi:fastutil:8.5.9")
    compileOnly("org.ow2.asm:asm:9.9.1")
    compileOnly("org.ow2.asm:asm-tree:9.9.1")
    testImplementation("org.ow2.asm:asm:9.9.1")
    testImplementation("org.ow2.asm:asm-tree:9.9.1")
    testImplementation("org.ow2.asm:asm-analysis:9.9.1")
    testImplementation("junit:junit:4.13.2")
    externalVoxy.orNull?.let { add(voxyBridge.compileOnlyConfigurationName, files(it)) }
    add(voxyBridge.compileOnlyConfigurationName, "it.unimi.dsi:fastutil:8.5.9")
    add(voxyBridge.compileOnlyConfigurationName, "org.lwjgl:lwjgl:3.4.2")
    add(voxyBridge.compileOnlyConfigurationName, "org.lwjgl:lwjgl-opengl:3.4.2")
    add(voxyBridge.compileOnlyConfigurationName, "org.joml:joml:1.10.8")
    testRuntimeOnly("org.joml:joml:1.10.8")
    testRuntimeOnly("it.unimi.dsi:fastutil:8.5.9")
    testRuntimeOnly("org.lwjgl:lwjgl:3.4.2")
    testRuntimeOnly("org.lwjgl:lwjgl:3.4.2:natives-windows")
    testRuntimeOnly("org.lwjgl:lwjgl-zstd:3.4.2")
    testRuntimeOnly("org.lwjgl:lwjgl-zstd:3.4.2:natives-windows")
}

tasks.named<JavaCompile>("compileTestJava") {
    classpath = classpath.filter { !it.name.startsWith("asm-all-") && !it.name.startsWith("asm-debug-all-") }
}

tasks.withType<Test>().configureEach {
    // Forge's historical asm-all precedes ASM 9 in plain unit-test classpaths. GTNH uses RFB's modern ASM.
    classpath = classpath.filter { !it.name.startsWith("asm-all-") && !it.name.startsWith("asm-debug-all-") }
    dependsOn(voxyBridge.classesTaskName)
    val testJava = providers.gradleProperty("roxyTestJava").getOrElse("21").toInt()
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(testJava)) })
    providers.gradleProperty("roxyVoxyJar").orNull?.let { systemProperty("roxy.voxyJar", it) }
    providers.gradleProperty("roxyAngelicaJar").orNull?.let { systemProperty("roxy.angelicaJar", it) }
    testLogging { events("passed", "skipped", "failed") }
}

val verifyNoBundledVoxy by tasks.registering {
    group = "verification"
    description = "Checks that the compatibility artifact does not bundle Voxy classes, assets, or nested JARs."
    dependsOn(tasks.jar)
    doLast {
        ZipFile(tasks.jar.get().archiveFile.get().asFile).use { zip ->
            val forbidden = zip.entries().asSequence().map { it.name }.filter {
                it.startsWith("me/cortex/voxy/") || it.startsWith("assets/voxy/") || it.startsWith("net/minecraft/") || it.endsWith(".jar")
            }.toList()
            check(forbidden.isEmpty()) { "External dependency content found in Roxy artifact: $forbidden" }
        }
    }
}

tasks.check { dependsOn(verifyNoBundledVoxy) }
