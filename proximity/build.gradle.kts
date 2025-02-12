plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    id("kotlin-parcelize")
    id("maven-publish")
    id("signing")
}

android {
    namespace = "it.pagopa.proximity"
    compileSdk = 35

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

    publishing {
        publications {
            create<MavenPublication>("release") {
                // from(components["release"])  // this line generates an error on syncing as undefined component
                groupId = "it.pagopa.io.wallet"
                artifactId = "proximity"
                version = "1.0.0"

                pom {
                    name.set("PagoPA IO Wallet PROXIMITY Library")
                    description.set("A native implementation of iso18013 (aka proximity)")
                    url.set("https://github.com/pagopa/iso18013-android")

                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }

                    developers {
                        developer {
                            id.set("iowallettech")
                            name.set("PagoPA S.p.A.")
                            email.set("iowallettech@pagopa.it")
                        }
                    }

                    scm {
                        connection.set("scm:git:git://github.com/pagopa/iso18013-android.git")
                        developerConnection.set("scm:git:ssh://github.com/pagopa/iso18013-android.git")
                        url.set("https://github.com/pagopa/iso18013-android")
                    }
                }
            }
        }

        repositories {
            maven {
                name = "ProximityTestRepo"
                url = uri("${rootProject.buildDir}/proximity")
                /* name = "MavenCentral"
                url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
                credentials {
                    username = project.findProperty("mavenCentralUsername") as String? ?: System.getenv("MAVEN_CENTRAL_USERNAME") ?: ""
                    password = project.findProperty("mavenCentralPassword") as String? ?: System.getenv("MAVEN_CENTRAL_PASSWORD") ?: ""
                } */
            }
        }
    }

    /* signing {
        useInMemoryPgpKeys(
            project.findProperty("signing.keyId") as String?,
            project.findProperty("signing.key") as String?,
            project.findProperty("signing.password") as String?
        )
        sign(publishing.publications["release"])
    } */


dependencies {
    implementation(project(":cbor_implementation"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.io.core)
    // to parse device request
    implementation(libs.cbor)
    implementation(libs.google.identity) {
        exclude(group = "org.bouncycastle")
    }
    implementation(libs.google.identity.android) {
        exclude(group = "org.bouncycastle")
    }
    implementation(libs.google.identity.mdoc) {
        exclude(group = "org.bouncycastle")
    }
    implementation(libs.bouncy.castle.prov)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}