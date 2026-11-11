plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.luccoj.wakebridge"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.luccoj.wakebridge"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "0.5.1"
    }

    // Only the app's own languages, so library texts match the rest of the app
    androidResources {
        localeFilters += listOf("en", "it")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            // Robolectric downloads Android framework jars from Maven Central by itself; this
            // optional Gradle property points it at a mirror (e.g. when Central rate-limits)
            all { test ->
                providers.gradleProperty("robolectricRepoUrl").orNull?.let {
                    test.systemProperty("robolectric.dependency.repo.url", it)
                }
                // -Pscreenshots=DIR renders the panes to PNGs (ScreenshotTest)
                providers.gradleProperty("screenshots").orNull?.let { test.systemProperty("screenshots", it) }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("com.google.android.material:material:1.14.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
}
