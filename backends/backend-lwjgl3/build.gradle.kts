plugins {
    id("java")
    kotlin("jvm")
}

group = "com.github.Anuken"
version = "1.0"

repositories {
    mavenCentral()
}

val lwjglVersion = "3.3.6"
val lwjglNativesList = mutableListOf(
    // FreeBSD
    "natives-freebsd",

    // Linux 系列
    "natives-linux",
    "natives-linux-arm32",
    "natives-linux-arm64",
    "natives-linux-ppc64le",
    "natives-linux-riscv64",

    // macOS 系列
    "natives-macos",
    "natives-macos-arm64",

    // Windows 系列
    "natives-windows",
    "natives-windows-x86",
    "natives-windows-arm64"
)

dependencies {
    implementation(platform("org.lwjgl:lwjgl-bom:$lwjglVersion"))

    implementation("org.lwjgl", "lwjgl")
    implementation("org.lwjgl", "lwjgl-assimp")
    implementation("org.lwjgl", "lwjgl-freetype")
    implementation("org.lwjgl", "lwjgl-glfw")
    implementation("org.lwjgl", "lwjgl-openal")
    implementation("org.lwjgl", "lwjgl-opengl")
    implementation("org.lwjgl", "lwjgl-opengles")
    implementation("org.lwjgl", "lwjgl-shaderc")
    implementation("org.lwjgl", "lwjgl-spvc")
    implementation("org.lwjgl", "lwjgl-stb")
    implementation("org.lwjgl", "lwjgl-vma")
    implementation("org.lwjgl", "lwjgl-vulkan")
    lwjglNativesList.forEach { lwjglNatives ->
        runtimeOnly("org.lwjgl", "lwjgl", classifier = lwjglNatives)
        runtimeOnly("org.lwjgl", "lwjgl-assimp", classifier = lwjglNatives)
        runtimeOnly("org.lwjgl", "lwjgl-freetype", classifier = lwjglNatives)
        runtimeOnly("org.lwjgl", "lwjgl-glfw", classifier = lwjglNatives)
        runtimeOnly("org.lwjgl", "lwjgl-openal", classifier = lwjglNatives)
        runtimeOnly("org.lwjgl", "lwjgl-opengl", classifier = lwjglNatives)
        runtimeOnly("org.lwjgl", "lwjgl-opengles", classifier = lwjglNatives)
        runtimeOnly("org.lwjgl", "lwjgl-shaderc", classifier = lwjglNatives)
        runtimeOnly("org.lwjgl", "lwjgl-spvc", classifier = lwjglNatives)
        runtimeOnly("org.lwjgl", "lwjgl-stb", classifier = lwjglNatives)
        runtimeOnly("org.lwjgl", "lwjgl-vma", classifier = lwjglNatives)
    }
    implementation(kotlin("stdlib-jdk8"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(8)
}