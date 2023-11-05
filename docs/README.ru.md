[![Build status](https://ci.sunnyday.dev/app/rest/builds/buildType:FusionTDD_Test,branch:name:main/statusIcon)](https://ci.sunnyday.dev/buildConfiguration/FusionTDD_Test/lastFinished?branch=%3Cdefault%3E)
[![Coverage status](https://img.shields.io/endpoint?url=https://kvdb.io/PY9VzGdCHe8YPbKvepE4y4/fustion-tdd-plugin.main.coverage&logo=TeamCity)](https://ci.sunnyday.dev/buildConfiguration/FusionTDD_Test/lastFinished?buildTab=tests&branch=%3Cdefault%3E)
![Release status](https://img.shields.io/badge/status-pre--alpha-red)

### Что это
Плагин для генерации продуктового кода на основе написанных для него тестов. Ожидается, что он сможет стать незаменимым помошником при написании кода в TDD/BDD стилях.

### Статус проекта
Плагин находится в ранней стадии разработки. Не используйте его на реальных задачах, попробуйте на простых демо проектах и предложите улучшения. В настоящее время он работает **только с Kotlin проектами**.

### Как использовать
Самый простой способ попробовать - это стянуть проект и выполнить `./gradlew runIde` в корневой папке проекта. Также можно выполнить `./gradlew buildPlugin` и установить полученный плагин в существующую версию IntelliJ, `Settings -> Pluggins -> Install Plugin from disk...`, указав путь до созданного плагина `build/distributions/FusionTDDPlugin-*.zip`.

Но сначала вам нужно получить токен авторизации от [HuggingFace](https://huggingface.co/settings/tokens).

Введите полученный токен в окне настроек плагина. Также вам необходимо указать пакет проекта, лишь классы этого пакета будут сканироваться для сбора контекста генерации.

<img src="docs/resources/minimal_required_settings.png" width="700" alt="Token and project package placed in text fields"/>

Далее, в IDE, создайте проект, создайте Kotlin класс, целевую функцию с пустым телом, напишите на нее тесты и запустите генерацию.

<img src="docs/resources/fusion_tdd_simple_trailer.gif" alt="Animated example of usage"/>

Наслаждайтесь и предлагайте улучшения!
