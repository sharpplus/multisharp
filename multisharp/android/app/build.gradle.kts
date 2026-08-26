plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// The precomputed two-square table has to travel inside the APK, since an
// Android process has no working directory to resolve ../precomp against.
// decomps2.txt is the only file any prover reads; primes.txt, the other survivor
// in precomp/, is an input to SquareDecomp's own regeneration utilities and is
// not needed on a device.
val copyPrecomp by tasks.registering(Copy::class) {
    from(rootProject.file("../precomp")) { include("decomps2.txt") }
    into(layout.buildDirectory.dir("generated/precompAssets/precomp"))
}

android {
    namespace = "org.multisharp.bench"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.multisharp.bench"
        // 31 rather than 26 only because of BigInteger.TWO, which Android gained
        // at 31 and which MultiSharpProver uses. Replacing it with valueOf(2)
        // would take the floor to 26; nothing else in the tree needs more than 11.
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    // The protocol sources are consumed in place rather than copied, so that the
    // handset and the desktop tables are produced by one set of files. Anything
    // that compiles here compiles for the JVM harness too.
    sourceSets["main"].java.srcDirs("../../src")
    sourceSets["main"].assets.srcDir(copyPrecomp.map { it.destinationDir.parentFile })

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    packaging {
        // curve25519-elisabeth is a modular jar; D8 has no use for the descriptor.
        resources.excludes += "META-INF/versions/9/module-info.class"
        resources.excludes += "META-INF/*.kotlin_module"
    }

    buildTypes {
        release {
            // Left off deliberately. R8 would shrink and inline across the
            // protocol code, which is exactly the code under measurement; the
            // desktop figures come from unoptimised javac output and the two
            // should not differ in that respect.
            isMinifyEnabled = false
            isDebuggable = false
        }
        debug {
            // Never benchmark this variant: a debuggable process runs with the
            // JIT held back and reads dramatically slow.
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(files("../../lib/curve25519-elisabeth-0.1.5.jar"))
    implementation(files("../../lib/keccakj-1.1.0.jar"))
}

tasks.named("preBuild") { dependsOn(copyPrecomp) }
