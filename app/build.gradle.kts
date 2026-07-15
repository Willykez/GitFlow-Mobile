plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "willykez.gitflowmobile"
    compileSdk = 34
    // No ndkVersion, no abiFilters — this app is pure Kotlin + Java (JGit),
    // so there's nothing native to compile or package per CPU architecture.

    defaultConfig {
        applicationId = "willykez.gitflowmobile"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        multiDexEnabled = true // JGit brings in a lot of classes
    }

    // ------------------------------------------------------------------
    // Release signing
    // ------------------------------------------------------------------
    // Reads keystore details from Gradle properties (-PGITFLOWMOBILE_RELEASE_STORE_FILE=...
    // etc, passed by the CI workflow). If those properties aren't set — e.g. you
    // run `gradle assembleRelease` locally without them — this quietly falls back
    // to signing with the normal Android debug keystore instead of failing the
    // build, so a local release build still produces an installable APK.
    val releaseStoreFile = findProperty("GITFLOWMOBILE_RELEASE_STORE_FILE") as String?
    val releaseStorePassword = findProperty("GITFLOWMOBILE_RELEASE_STORE_PASSWORD") as String?
    val releaseKeyAlias = findProperty("GITFLOWMOBILE_RELEASE_KEY_ALIAS") as String?
    val releaseKeyPassword = findProperty("GITFLOWMOBILE_RELEASE_KEY_PASSWORD") as String?
    val hasReleaseSigningProps = !releaseStoreFile.isNullOrBlank() &&
        !releaseStorePassword.isNullOrBlank() &&
        !releaseKeyAlias.isNullOrBlank() &&
        !releaseKeyPassword.isNullOrBlank()

    signingConfigs {
        if (hasReleaseSigningProps) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            // Shrinks + obfuscates code (R8) and strips unused resources —
            // this is the "shrink the app" step. proguard-rules.pro carries
            // the keep-rules the shrinker needs for Room, JGit, and friends.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

            signingConfig = if (hasReleaseSigningProps) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true // JGit uses some java.time / java.nio APIs
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    // JGit's jar ships some META-INF files that collide with other libraries'
    // — this just tells Gradle "ignore the duplicates, don't fail the build."
    packaging {
        resources {
            excludes += listOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/*.kotlin_module",
                "META-INF/INDEX.LIST",
                "META-INF/eclipse.inf",
                "about.html",
                "about_files/**",
                "plugin.properties"
            )
        }
    }
}

dependencies {
    // Needed alongside isCoreLibraryDesugaringEnabled above
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // Compose BOM keeps all Compose library versions aligned
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    // Material2's "pullrefresh" primitives — material3 in this project's pinned compose-bom
    // doesn't expose an equivalent under androidx.compose.material3 directly, and this is a
    // long-stable, unambiguous API rather than another guess at material3's package layout.
    implementation("androidx.compose.material:material")
    // SAF DocumentFile — recursive traversal for the file explorer's "Import Folder" feature
    implementation("androidx.documentfile:documentfile:1.0.1")
    // Background auto-sync (periodic fetch)
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Activity + Navigation
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Lifecycle + ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")

    // Room
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Security — encrypt stored PAT tokens at rest
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // MultiDex (needed with JGit's class count on minSdk 24-20)
    implementation("androidx.multidex:multidex:2.0.1")

    // Core KTX
    implementation("androidx.core:core-ktx:1.13.1")

    // ---- The git engine itself: a normal Maven dependency, nothing native ----
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.7.0.202309050840-r") {
        // Old SSH library — excluded since this app only supports HTTPS + PAT
        // for now. Can be swapped in later (Apache MINA-based) if you want SSH.
        exclude(group = "com.jcraft", module = "jsch")
    }
}
