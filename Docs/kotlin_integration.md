# Kotlin SDK


[changelog-link]: https://github.com/cloudinary/cloudinary_kotlin/blob/master/CHANGELOG.md

## Overview

Cloudinary's Kotlin SDK provides simple, yet comprehensive image and video transformation, optimization, and delivery capabilities that you can implement using code that integrates seamlessly with your existing Kotlin or Java application.
> **INFO**: :title=SDK security upgrade, June 2025

We recently released an enhanced security version of this SDK that improves the validation and handling of input parameters. We recommend upgrading to the [latest version][changelog-link] of the SDK to benefit from these security improvements.

> **TIP**: In this guide you'll learn how to get started with the Kotlin SDK, but if you are not familiar with Cloudinary, we advise starting with the [Developer Kickstart](dev_kickstart) for a hands-on, step-by-step introduction to a variety of features.

> **READING**: :no-title

**This guide relates to the latest released version of the [Cloudinary Kotlin](https://github.com/cloudinary/cloudinary_kotlin) library.**

### Key features

* Uses Cloudinary's new SDK action based syntax with enhanced code autocomplete.
* Actions and transformations are immutable, for easier and safer code reuse.
* Makes use of [Type-Safe Builders](https://kotlinlang.org/docs/type-safe-builders.html) to create a Cloudinary DSL layer. The transformation syntax is therefore simpler and more human-readable when compared with the existing Java or Android SDKs.

## Get started

Install and configure the SDK in your project to get started.

### Add Kotlin SDK dependency

Add the SDK to your project as a dependency, we recommend using a build management tool such as Maven or Gradle to do this.

#### Using Gradle

Add the Cloudinary Kotlin SDK to the dependencies section of your `build.gradle` file.

```java
implementation 'com.cloudinary:kotlin-url-gen:1.7.0'
```

#### Using Maven

Add the Cloudinary Kotlin SDK to the list of dependencies in your `pom.xml` file.

```xml
<dependencies>
  ...
  <dependency>
    <groupId>com.cloudinary</groupId>
    <artifactId>kotlin-url-gen</artifactId>
    <version>1.7.0</version>
    <type>pom</type>
  </dependency>
</dependencies>
```

### Add your Cloudinary configuration

The `Cloudinary` class is the main entry point for using the library. Your `cloud_name` is required to create an instance of this class. Your `api_key` and `api_secret` are used to perform secure API calls to Cloudinary (e.g., image and video uploads). Setting the configuration parameters can be done either programmatically using an appropriate constructor of the Cloudinary class or globally using an environment variable. You can find your configuration credentials on the [API Keys](https://console.cloudinary.com/app/settings/api-keys) page of the Cloudinary Console Settings.

In addition to the required configuration parameters, you can define a number of optional [configuration parameters](cloudinary_sdks#configuration_parameters) if relevant.

Here's an example of setting configuration parameters in your Kotlin application:

```kotlin
import com.cloudinary.*; 
...
private val cloudinary = Cloudinary("cloudinary://<your-api-key>:<your-api-secret>@<your-cloud-name>")
```

## Example

Here is a simple example for generating a Cloudinary image URL, including a resize transformation, using the Kotlin SDK:

```kotlin
cloudinary.image {
    publicId("sample")
    resize(Resize.fill {
        width(200)
        height(300)
    })
}
```

![simple example](https://res.cloudinary.com/demo/image/upload/c_fill,h_300,w_200/sample.jpg "with_code: false, with_url: false, thumb: u_docs:iphone_template,h_600")

> **Learn more about transformations**:
>
> * See all possible transformations in the [Transformation URL API reference](transformation_reference).

> * See more examples of [image and video transformations](kotlin_media_transformations) using the Cloudinary Kotlin library.

> **INFO**: :title=Help us improve our SDK

We'd love to hear your thoughts on using our Kotlin SDK. Please take a moment to complete this [short survey](https://forms.gle/5hvdVB1hjc1UCuLN7). Thanks for your time!

## Migration from Java/Android

To use the Kotlin SDK with your existing Java and Android projects, we recommend adding your own Kotlin classes as a bridge between our SDK and your Java code. You can therefore take advantage of the simpler transformation building syntax.

For example, here's a simple Kotlin transformation:

```kotlin
val t = Transformation.transformation {
            Effect.gradientFade() { strength(3) }
            adjust(Adjust.opacity(80))
            border(Border.solid(3, Color.RED))
        }
```

And the corresponding code in Java:

```java
Transformation t = new Transformation()
       .gradientFade(new GradientFade.Builder().strength(3).build())
       .adjust(new Opacity.Builder(80).build())
       .border(new Border.Builder().width(3).color(new ColorValue.Builder().named("red").build()).build());
```

You can add a new Kotlin file to act as a bridge e.g.:

```kotlin
fun constructSpecificUseCaseTransformation() =
    Transformation.transformation {
        Effect.gradientFade() { strength(3) }
        adjust(Adjust.opacity(80))
        border(Border(4, Color.RED))
    }
```

You can then call this specific function from your Java code:

```java
Transformation t = constructSpecificUseCaseTransformation()
```

> **READING**:
>
> * See examples of powerful [image and video](kotlin_media_transformations) transformations using Kotlin code and see our [image transformations](image_transformations) and [video transformations](video_manipulation_and_delivery) docs.

> * Stay tuned for updates by following the [Release Notes](programmable_media_release_notes) and the [Cloudinary Blog](https://cloudinary.com/blog).

> * Take a look at our [Android SDK](android_integration) as an alternative for Android development with Cloudinary.
