[![Build status](https://ci.sunnyday.dev/app/rest/builds/buildType:FusionTDD_Test,branch:name:main/statusIcon)](https://ci.sunnyday.dev/buildConfiguration/FusionTDD_Test/lastFinished?branch=%3Cdefault%3E)
[![Coverage status](https://img.shields.io/endpoint?url=https://kvdb.io/PY9VzGdCHe8YPbKvepE4y4/fustion-tdd-plugin.main.coverage&logo=TeamCity)](https://ci.sunnyday.dev/buildConfiguration/FusionTDD_Test/lastFinished?buildTab=tests&branch=%3Cdefault%3E)
![Release status](https://img.shields.io/badge/status-pre--alpha-red)

[![Readme en](https://img.shields.io/badge/readme-en-green)](https://github.com/SunnyDayDev/fusion-tdd-plugin/blob/main/README.md)
[![Readme ru](https://img.shields.io/badge/readme-ru-green)](https://github.com/SunnyDayDev/fusion-tdd-plugin/blob/main/docs/README.ru.md)

### What is it
A plugin designed for generating product code based on tests written for it. It is anticipated to become an indispensable tool when writing code in TDD/BDD styles.

### Project status
The plugin is in the early stages of development. It is not recommended to use it for solving real-world problems yet; instead, try it on simple demo projects and suggest improvements. Currently, it is compatible with **Kotlin projects only**.

### How to use
The easiest way to try the plugin is to clone the project and run `./gradlew runIde` in the project's root folder. Alternatively, you can execute `./gradlew buildPlugin` and install the resulting plugin into an existing version of IntelliJ by going to `Settings -> Pluggins -> Install Plugin from disk...` and specifying the path to the created plugin at `build/distributions/FusionTDDPlugin-*.zip`.

But first of all you need to get an authorization token from [HuggingFace](https://huggingface.co/settings/tokens).

Enter the received token in the plugin settings window. You also need to specify the project package; only the classes of this package will be scanned to collect the generation context.

<img src="docs/resources/minimal_required_settings.png" width="700" alt="Token and project package placed in text fields"/>

Further, create a project in the IDE, create a Kotlin class, a target function with an empty body, write tests for it and start generation.

<img src="docs/resources/fusion_tdd_simple_trailer.gif" alt="Animated example of usage"/>

Enjoy and suggest improvements!
