import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "it.pagopa.cbor_implementation"
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

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.cbor)
    implementation(libs.cose)
    implementation(libs.kotlinx.io.core)
    implementation(libs.kotlinx.io.bytestring)
    implementation(libs.biometric.ktx)
    //Google identity
    implementation(libs.google.identity) {
        exclude(group = "org.bouncycastle")
    }
    // Bouncy castle
    implementation(libs.bouncy.castle.pkix)
    implementation(libs.bouncy.castle.prov)
    implementation(libs.kotlinx.datetime)
    //TESTS
    testImplementation(libs.json)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.bouncy.castle.prov)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

val libGroupId = "it.pagopa"
val libArtifactID = "cbor"
val libVersion = "1.0.0"
val localProperties = Properties()
localProperties.load(FileInputStream(rootProject.file("local.properties")))
val pushingUrl: String? = localProperties["git_hub_pushing_url"] as? String
val gitHubUser: String? = localProperties["git_hub_user"] as? String
val gitHubToken: String? = localProperties["git_hub_token"] as? String
val aarName = "cbor_implementation-release"

val sources by tasks.creating(Jar::class) {
    group = JavaBasePlugin.DOCUMENTATION_GROUP
    description = "Assembles AAR sources"
    archiveClassifier.set("sources")
    from(android.sourceSets.getByName("main").java.srcDirs)
}

configure<PublishingExtension> {
    if (pushingUrl == null) {
        println("no pushing url configured into local properties!!")
        return@configure
    }
    repositories {
        maven(pushingUrl) {
            credentials {
                username = gitHubUser ?: System.getenv("GITHUB_USER")
                password = gitHubToken ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
    publications {
        create<MavenPublication>("cbor") {
            groupId = libGroupId
            artifactId = libArtifactID
            version = libVersion
            val baseDir = rootProject.layout.projectDirectory.dir("cbor_implementation").dir("build")
            val reference = "$baseDir/outputs/aar/$aarName.aar"
            artifact(reference)
            artifact(sources)

            pom.withXml {
                val dependenciesNode = asNode().appendNode("dependencies")
                configurations.api {
                    allDependencies.forEach { dependency ->
                        val dependencyNode = dependenciesNode.appendNode("dependency")
                        dependencyNode.appendNode("groupId", dependency.group)
                        dependencyNode.appendNode("artifactId", dependency.name)
                        dependencyNode.appendNode("version", dependency.version)
                    }
                }
            }
        }
    }
}
