[![Build status](https://ci.sunnyday.dev/app/rest/builds/buildType:FusionTDD_Test,branch:name:main/statusIcon)](https://ci.sunnyday.dev/buildConfiguration/FusionTDD_Test/lastFinished?branch=%3Cdefault%3E)
[![Coverage status](https://img.shields.io/endpoint?url=https://kvdb.io/PY9VzGdCHe8YPbKvepE4y4/fustion-tdd-plugin.main.coverage&logo=TeamCity)](https://ci.sunnyday.dev/buildConfiguration/FusionTDD_Test/lastFinished?buildTab=tests&branch=%3Cdefault%3E)
![Release status](https://img.shields.io/badge/status-pre--alpha-red)

### What is it
A plugin for generating product code using tests written in advance for it. It is expected that it will become an indispensable assistant in TDD/BDD styles of coding.

### State
The plugin is in an early stage of development. You shouldn't use it on real problems, just try it on dummy and suggest improvements.
Currently it works **only with Kotlin** projects.

### How to use
The easiest way to try is to pull the project and run `./gradlew runIde`.

But firstly, you need to get a token from the [HuggingFace](https://huggingface.co/settings/tokens).

Enter the received token into the plugin settings. You also need to fill out your project package, only classes with the specified package will be scanned to collect the generation context.

<img src="docs/resources/minimal_required_settings.png" width="700" alt="Token and project package placed in text fields"/>

Further, in the IDE, create a project, create a Kotlin class, a target function with an empty body, write tests for it and start generation.

<img src="docs/resources/fusion_tdd_simple_trailer.gif" alt="Animated example of usage"/>


Enjoy, and suggest improvements!