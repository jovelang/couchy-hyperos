plugins { id("com.android.application") }

android {
    namespace = "com.homekey.standin"
    compileSdk = 34
    defaultConfig {
        // Some Xiaomi firmwares route HOME only to this compatibility identity.
        // This is an independent, unofficial stand-in; see docs/HYPEROS4.md.
        applicationId = "com.ts.launcher"
        minSdk = 26
        targetSdk = 34
        versionCode = 5
        versionName = "0.1.0"
        val target = providers.gradleProperty("launcherApplicationId")
            .getOrElse("io.github.jovelang.couchy")
        require(target.matches(Regex("[a-zA-Z][a-zA-Z0-9_.]+")))
        buildConfigField("String", "TARGET_PACKAGE", "\"$target\"")
        manifestPlaceholders["launcherPackage"] = target
    }
    val ciKeystore = System.getenv("KEYSTORE_FILE")
    signingConfigs {
        if (ciKeystore != null) create("ci") {
            storeFile = file(ciKeystore)
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            signingConfig = when {
                ciKeystore != null -> signingConfigs.getByName("ci")
                project.hasProperty("localSign") -> signingConfigs.getByName("debug")
                else -> null
            }
        }
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    dependenciesInfo { includeInApk = false; includeInBundle = false }
}
