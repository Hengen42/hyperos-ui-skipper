plugins {
    id("com.android.application")
}

android {
    namespace = "dev.hengen.uiskipper"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.hengen.uiskipper"
        minSdk = 26
        targetSdk = 34
        versionCode = 101
        versionName = "1.0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/java", "stubs")
        }
    }
}

dependencies {
    // Compile-time stubs are provided in xposed-module/stubs
}
