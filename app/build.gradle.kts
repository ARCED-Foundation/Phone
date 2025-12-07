import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.konan.properties.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.detekt)
    id("jacoco")
    kotlin("kapt")
}

val keystorePropertiesFile: File = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

fun hasSigningVars(): Boolean {
    return providers.environmentVariable("SIGNING_KEY_ALIAS").orNull != null
            && providers.environmentVariable("SIGNING_KEY_PASSWORD").orNull != null
            && providers.environmentVariable("SIGNING_STORE_FILE").orNull != null
            && providers.environmentVariable("SIGNING_STORE_PASSWORD").orNull != null
}

android {
    compileSdk = project.libs.versions.app.build.compileSDKVersion.get().toInt()

    defaultConfig {
        applicationId = project.property("APP_ID").toString()
        minSdk = project.libs.versions.app.build.minimumSDK.get().toInt()
        targetSdk = project.libs.versions.app.build.targetSDK.get().toInt()
        versionName = project.property("VERSION_NAME").toString()
        versionCode = project.property("VERSION_CODE").toString().toInt()
        setProperty("archivesBaseName", "phone-$versionCode")
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            register("release") {
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
            }
        } else if (hasSigningVars()) {
            register("release") {
                keyAlias = providers.environmentVariable("SIGNING_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("SIGNING_KEY_PASSWORD").get()
                storeFile = file(providers.environmentVariable("SIGNING_STORE_FILE").get())
                storePassword = providers.environmentVariable("SIGNING_STORE_PASSWORD").get()
            }
        } else {
            logger.warn("Warning: No signing config found. Build will be unsigned.")
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
            if (keystorePropertiesFile.exists() || hasSigningVars()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    flavorDimensions.add("variants")
    productFlavors {
        register("core")
        register("foss")
        register("gplay")
    }

    sourceSets {
        getByName("main").java.srcDirs("src/main/kotlin")
    }

    compileOptions {
        val currentJavaVersionFromLibs =
            JavaVersion.valueOf(libs.versions.app.build.javaVersion.get())
        sourceCompatibility = currentJavaVersionFromLibs
        targetCompatibility = currentJavaVersionFromLibs
    }

    dependenciesInfo {
        includeInApk = false
    }

    androidResources {
        @Suppress("UnstableApiUsage")
        generateLocaleConfig = true
    }

    tasks.withType<KotlinCompile> {
        compilerOptions.jvmTarget.set(
            JvmTarget.fromTarget(project.libs.versions.app.build.kotlinJVMTarget.get())
        )
    }

    namespace = project.property("APP_ID").toString()

    lint {
        checkReleaseBuilds = false
        abortOnError = true
        warningsAsErrors = false
        baseline = file("lint-baseline.xml")
        lintConfig = rootProject.file("lint.xml")
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    bundle {
        language {
            enableSplit = false
        }
    }
}

detekt {
    baseline = file("detekt-baseline.xml")
    config.setFrom("$rootDir/detekt.yml")
    buildUponDefaultConfig = true
    allRules = false
}

dependencies {
    implementation(libs.fossify.commons)
    implementation(libs.indicator.fast.scroll)
    implementation(libs.autofit.text.view)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.eventbus)
    implementation(libs.libphonenumber)
    implementation(libs.geocoder)

    // Room dependencies
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)

    // WorkManager dependencies
    implementation(libs.work.runtime)
    implementation(libs.work.runtime.ktx)

    // OkHttp dependencies
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    // Gson dependency
    implementation(libs.gson)

    
    // Navigation dependencies
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.material)

    detektPlugins(libs.compose.detekt)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.work.testing)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.androidx.fragment.testing)
}

jacoco {
    toolVersion = "0.8.12"
}

fun coverageClassTree(dir: String) = fileTree(dir) {
    include("**/*.class")
    exclude("**/R.class", "**/R\\$*.class", "**/BuildConfig.*")
}

val coverageClassDirs = files(
    coverageClassTree("$buildDir/tmp/kotlin-classes"),
    coverageClassTree("$buildDir/tmp/java-classes"),
    coverageClassTree("$buildDir/intermediates/javac")
)

val coverageSourceDirs = files("src/main/java", "src/main/kotlin")
val coverageExecutionData = fileTree("$buildDir/jacoco") {
    include("*.exec")
}

val jacocoReport by tasks.registering(JacocoReport::class) {
    dependsOn(tasks.withType<Test>())
    group = "verification"
    description = "Generates Jacoco coverage reports for all unit tests."
    executionData.setFrom(coverageExecutionData)
    sourceDirectories.setFrom(coverageSourceDirs)
    classDirectories.setFrom(coverageClassDirs)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.register<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(jacocoReport)
    group = "verification"
    description = "Enforces Jacoco coverage requirements."
    executionData.setFrom(coverageExecutionData)
    classDirectories.setFrom(coverageClassDirs)
    violationRules {
        rule {
            limit {
                minimum = "0.10".toBigDecimal()
            }
        }
    }
}

tasks.register("validateCallSync") {
    group = "verification"
    description = "Runs lint, detekt, and coverage validations for the call logging feature."
    dependsOn("lint", "detekt", "jacocoTestCoverageVerification")
}
