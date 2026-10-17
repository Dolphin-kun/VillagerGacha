plugins {
    id("dev.kikugie.loom-back-compat")
}

version = "${property("version")}+${sc.current.version}"
group = property("group") as String
base.archivesName = property("mod_id") as String

val mcVer: String = sc.current.version
val requiredJava: JavaVersion = when (mcVer) {
    "1.20.1" -> JavaVersion.VERSION_17
    "1.21.1" -> JavaVersion.VERSION_21
    else -> JavaVersion.VERSION_25
}
val mcCompat: String = when (mcVer) {
    "1.20.1" -> "~1.20.1"
    "1.21.1" -> "~1.21.1"
    else -> "~26.1"
}
val javaCompat: String = when (requiredJava) {
    JavaVersion.VERSION_17 -> ">=17"
    JavaVersion.VERSION_21 -> ">=21"
    else -> ">=25"
}
val mixinJava = "JAVA_${requiredJava.majorVersion}"
val modVersion: String = project.version.toString()
val loaderVer: String = property("loader_version").toString()

repositories {
    maven("https://maven.fabricmc.net/") { name = "Fabric" }
    maven("https://maven.terraformersmc.com/releases/") { name = "Terraformers" }
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    loomx.applyMojangMappings()

    modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
    modImplementation("com.terraformersmc:modmenu:${property("modmenu_version")}") {
        isTransitive = false
    }
}

tasks.processResources {
    filteringCharset = "UTF-8"
    inputs.property("version", modVersion)
    inputs.property("mc_compat", mcCompat)
    inputs.property("loader_version", loaderVer)
    inputs.property("java_compat", javaCompat)
    inputs.property("mixin_java", mixinJava)

    filesMatching("fabric.mod.json") {
        expand(
            "version" to modVersion,
            "mc_compat" to mcCompat,
            "loader_version" to loaderVer,
            "java_compat" to javaCompat
        )
    }
    filesMatching("*.mixins.json") {
        expand("mixin_java" to mixinJava)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = requiredJava.majorVersion.toInt()
    options.encoding = "UTF-8"
}

java {
    withSourcesJar()
    targetCompatibility = requiredJava
    sourceCompatibility = requiredJava
    toolchain {
        vendor = JvmVendorSpec.ADOPTIUM
        languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
    }
}

tasks.jar {
    from("LICENSE") {
        rename { "LICENSE_villager_gacha" }
    }
}
