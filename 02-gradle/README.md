# 02-gradle — тот же log-stats, но на Gradle

Приложение — **log-stats, анализатор логов** (сводка уровней и топ ошибок из
лог-файла, отчёт в `report.json`). Вход для демо — `samples/app.log`
(реальный кусок лога прод-сервиса веб-звонков, signaler).

Требуется JDK 21+ (проверено на 25). **Gradle ставить не нужно** — в проекте
wrapper (`./gradlew`), который при первом запуске сам скачает Gradle 9.2.1
(версия зафиксирована в `gradle/wrapper/gradle-wrapper.properties`).
CLI приложения: `Main <file.log> [--top N]`; `./gradlew run` по умолчанию
анализирует `samples/app.log` (меняется: `./gradlew run --args='...'`).

В проекте Gradle 9.2.1, а не 8.14: 8.14 не умеет запускаться на JDK 25.
Это тоже часть правды о сборщиках: «версия сборщика фиксируется wrapper'ом» —
не красивая абстракция, а работающий механизм.

## Прогон практики (все шаги проверены)

### Шаг 1. Wrapper первым делом

```bash
./gradlew tasks
```

Первая команда проекта — не сборка, а список задач. Первый запуск скачает
дистрибутив Gradle в `~/.gradle/wrapper/dists/` — та же идея, что `./mvnw`
в 01-maven: версия сборщика версионируется вместе с проектом, у всей команды
и CI она одинаковая. Среди задач уже есть знакомое и новое:

```
Application tasks
-----------------
run - Runs this project as a JVM application

Build tasks
-----------
assemble - Assembles the outputs of this project.
build - Assembles and tests this project.
...
clean - Deletes the build directory.
jar - Assembles a jar archive containing the classes of the 'main' feature.
...
```

### Шаг 2. Структура и сборка

Сравниваем деревья каталогов с `01-maven`: `src/main/java`,
`src/main/resources`, `src/test/java` — один в один. Отличия: вместо
`pom.xml` — `build.gradle` + `settings.gradle`, вместо `target/` — `build/`,
версии зависимостей — в `gradle/libs.versions.toml` (шаг 6).

```bash
./gradlew build
```

Полный вывод (сравните с `./mvnw package`: не фазы, а задачи):

```
> Task :compileJava
> Task :processResources
> Task :classes
> Task :jar
> Task :startScripts
> Task :distTar
> Task :distZip
> Task :assemble
> Task :compileTestJava
> Task :processTestResources NO-SOURCE
> Task :testClasses
> Task :test
> Task :check
> Task :build

BUILD SUCCESSFUL in 750ms
8 actionable tasks: 8 executed
```

(Время у вас будет другим — оно зависит от машины и прогретости демона;
при самом первом запуске сюда же добавится скачивание дистрибутива и
зависимостей. Обратите внимание на `startScripts`/`distTar`/`distZip` — их
добавил плагин `application`, и на `processTestResources NO-SOURCE` —
тестовых ресурсов у нас нет, Gradle это честно отмечает.)

Что лежит в `build/` (аналог `target/`):

```
classes/  distributions/  generated/  libs/  reports/  resources/  scripts/  test-results/  tmp/
```

В `libs/` — `logstats-1.0.0.jar`. Вскрываем: внутри наши классы + `logback.xml`,
**Lombok нет, Gson нет** — обычный jar, как у Maven (жирный собирают плагины,
здесь его не делаем). Вскроем и манифест
(`unzip -p build/libs/logstats-1.0.0.jar META-INF/MANIFEST.MF`) — там только
`Manifest-Version: 1.0`, **Main-Class отсутствует**: плагин `application`
знает главный класс для задачи `run` и пишет его в скрипты дистрибутива,
а не в jar. Такой jar — библиотечный; `java -jar` его не запустит (сравните
с shade-jar из 01-maven). В `reports/tests/test/` — HTML-отчёт тестов:
`test` выполнился по-настоящему, 7 тестов (LogParserTest + LogStatsTest).

Аналоги джентльменского набора Maven:

```bash
./gradlew clean build   # = clean package
./gradlew build -x test # = package -DskipTests (исключить задачу из графа!)
./gradlew test          # только тесты
```

Обратите внимание на `-x test`: в Maven «пропустить тесты» — флажок плагина,
а в Gradle — исключение узла из графа задач.

### Шаг 3. Граф задач: DAG вместо линейного lifecycle

У Maven lifecycle линеен: compile → test → package, и `mvn package` всегда
ждёт тестов. У Gradle — граф (DAG). Плагин `com.dorongold.task-tree`
(уже подключён в `build.gradle`) печатает его:

```bash
./gradlew build taskTree
```

```
:build
+--- :assemble
|    +--- :distTar
|    |    +--- :jar
|    |    |    +--- :classes
|    |    |    |    +--- :compileJava
|    |    |    |    \--- :processResources
|    |    \--- :compileJava *
|    \--- :startScripts
|         \--- :jar *
|    +--- :distZip
|    |    +--- :jar *
|    |    \--- :startScripts *
|    \--- :jar *
\--- :check
     \--- :test
          +--- :classes *
          +--- :compileJava *
          +--- :compileTestJava
          |    +--- :classes *
          |    \--- :compileJava *
          \--- :testClasses
               +--- :compileTestJava *
               \--- :processTestResources

(*) - subtree omitted (printed previously)
```

(Звёздочка — уже напечатанное поддерево: граф, в отличие от дерева, одну и
ту же задачу может показывать несколько раз.) Ткните пальцем: `:jar` висит
на ветке `:assemble`, `:test` — на `:check`. **Jar не ждёт test** — их
связывает только общий родитель `:build`. Хотите jar без тестов — просто
`./gradlew jar`, никакой магии.

### Шаг 4. Зависимости и ловушка №3

В `build.gradle` с самого начала лежит «зависимость коллеги» —
`converter-gson` «для будущего HTTP-клиента». А наша прямая зависимость от
Gson — нарочно **старая 2.9.0** (инверсия ловушки №2 из 01-maven: там
транзитивная была старее, здесь — новее). Смотрим, что Gradle решил:

```bash
./gradlew dependencies --configuration runtimeClasspath
```

```
+--- com.google.code.gson:gson:2.9.0 -> 2.10.1
+--- org.slf4j:slf4j-api:2.0.9
+--- com.squareup.retrofit2:converter-gson:2.11.0
|    +--- com.squareup.retrofit2:retrofit:2.11.0
|    |    \--- com.squareup.okhttp3:okhttp:3.14.9
|    |         \--- com.squareup.okio:okio:1.17.2
|    \--- com.google.code.gson:gson:2.10.1
\--- ch.qos.logback:logback-classic:1.4.14
     +--- ch.qos.logback:logback-core:1.4.14
     \--- org.slf4j:slf4j-api:2.0.7 -> 2.0.9
```

Стрелка `gson:2.9.0 -> 2.10.1` — это и есть разрешённый конфликт. Мы
написали «2.9.0», а работает 2.10.1: правило Gradle — **берём самую новую**,
против «nearest wins» у Maven (слайды 28 vs 48). Тоже молча: сборка зелёная,
приложение работает (наш `JsonReportWriter` сериализует DTO `Report` — обычный
класс с полями, Gson читает их напрямую на любой версии, — поэтому Gson 2.9.0
и 2.10.1 работают одинаково), о
подмене знает только это дерево. Бонусом второй конфликт:
`slf4j-api:2.0.7 -> 2.0.9` от logback.

**Лекарство** — строгая версия. Меняем строку в `build.gradle`:

```groovy
implementation(libs.gson) {
    version { strictly('2.9.0') }
}
```

И снова смотрим дерево:

```
+--- com.google.code.gson:gson:{strictly 2.9.0} -> 2.9.0
+--- com.squareup.retrofit2:converter-gson:2.11.0
|    \--- com.google.code.gson:gson:2.10.1 -> 2.9.0
```

Теперь решение явное: стрелка развёрнута, 2.10.1 понижена до 2.9.0, и это
видно. Альтернативы того же класса: `dependencies { constraints { ... } }`
и `platform()` (шаг 6). Сборка при этом остаётся зелёной — проверено.
**Верните обычную версию обратно** (на диске в репо ловушка заложена).

### Шаг 5. Конфигурации = scope'ы

Сравниваем словари Maven и Gradle:

| Maven (scope)  | Gradle (configuration) | Что значит                          |
|----------------|------------------------|-------------------------------------|
| `compile`      | `implementation`       | в classpath и компиляции, и запуска |
| `provided`     | `compileOnly`          | только компиляция, в jar не попадёт |
| —              | `annotationProcessor`  | процессор аннотаций (Lombok!)       |
| `runtime`      | `runtimeOnly`          | только запуск, код её не видит      |
| `test`         | `testImplementation`   | только тесты                        |

`annotationProcessor` — не «ещё один scope», а отдельная ось: Lombok
подключён дважды — `compileOnly` (чтобы компилировался код с `@Slf4j`)
и `annotationProcessor` (чтобы javac его запускал). На JDK 23+ процессоры
аннотаций по умолчанию отключены — та же грабля, что
`maven.compiler.proc=full` в 01-maven, но в Gradle лекарство другое:
просто объявить процессор явно. Проверено: без этой строки — `cannot find
symbol: variable log`.

`implementation` против `api` (конфигурация `api` появляется, когда вместо
плагина `java` подключают `java-library`) — тема слайда 38: `implementation`
не вытекает в classpath потребителей нашей библиотеки.

### Шаг 6. Version Catalog

Открываем `gradle/libs.versions.toml`: все версии — в одном месте, в
`build.gradle` — только алиасы (`libs.gson`, `libs.lombok`, ...). Аналог
`<properties>` в pom.xml, но типизированный: IDE даёт автодополнение и
подсвечивает, когда версия не используется.

Сила каталога — мгновенная смена версии. Правим `gson = "2.9.0"` на
`"2.10.1"`, запускаем `./gradlew dependencies --configuration runtimeClasspath`:

```
+--- com.google.code.gson:gson:2.10.1
```

Стрелка исчезла: наша версия и транзитивная совпали. Верните `"2.9.0"` —
стрелка вернётся. Здесь же слайд 46: `platform('группа:артефакт:версия')` —
подключение BOM, аналог `dependencyManagement`/`import scope` в Maven.

### Шаг 7. Кастомизация: задачи — это код

В `build.gradle` внизу — две готовые задачи:

```bash
./gradlew hello
# Привет! Это кастомная задача Gradle.

./gradlew printTopErrors
# ... приложение соберётся, запустится (samples/app.log), напишет report.json ...
# Всего строк в логе: 64
# Топ ошибок (из report.json):
#   1. QUIC exception from src x13
#   2. Unable to send WebSocket x2
#   3. Invalid access token for x1
#   4. exceptionCaught event was fired x1
```

`printTopErrors` объявлен с `dependsOn 'run'` — сначала выполнится `run`
(а он тянет `classes` → `compileJava` → ...), потом наш `doLast`. Задача
встраивается в граф на общих основаниях — `./gradlew build printTopErrors
taskTree` покажет её в дереве. Напишите на глазах третью, любую:

```groovy
tasks.register('погода') {
    doLast { println 'За окном ноябрь, сборка зелёная.' }
}
```

Контраст: в Maven кастомная логика — это плагин (Java-код, отдельный
артефакт) или хак в pom; в Gradle — пара строк в том же скрипте.

### Шаг 8. Запуск и скорость

`application { mainClass = ... }` в `build.gradle` даёт задачу `run` —
полный аналог «запустить то, что собрали», с classpath из конфигураций
(аргументы по умолчанию — `samples/app.log`, см. блок `run` в скрипте):

```bash
./gradlew run
# 12:20:02.344 INFO  best.project.ever.Main - Запускаемся: log-stats на Gradle
# 12:20:02.361 INFO  best.project.ever.Main - Файл:    samples/app.log
# Строк:   64 (разобрано 58, пропущено 6)
# Уровни:  ERROR   3   WARN   14   INFO   41
# Топ ошибок (ERROR/WARN):
#   1. QUIC exception from src                  x13
#   2. Unable to send WebSocket                 x2
#   3. Invalid access token for                 x1
#   4. exceptionCaught event was fired          x1
# 12:20:02.400 INFO  best.project.ever.Main - Отчёт: report.json

./gradlew run --args='samples/app.log --top 1'   # свои аргументы
```

Теперь та же сборка второй раз:

```bash
./gradlew build
# > Task :compileJava UP-TO-DATE
# > Task :test UP-TO-DATE
# > Task :build UP-TO-DATE
# BUILD SUCCESSFUL in 285ms
# 8 actionable tasks: 8 up-to-date
```

**UP-TO-DATE** — Gradle хеширует входы и выходы каждой задачи и не делает
работу заново. Три кита скорости Gradle:

1. `UP-TO-DATE` на уровне задач — не изменилось, не делаем;
2. инкрементальность внутри задач — троньте один `.java`-файл и
   пересоберите: `compileJava` перекомпилирует только затронутые классы;
3. демон — живой фоновый процесс, который держит проект в памяти:

```bash
./gradlew build --offline   # работает из кэша ~/.gradle/caches — сеть не нужна
./gradlew --status          # ... PID ... IDLE ... 9.2.1
```

Демон — почему вторая сборка «за 285ms»: JVM уже прогрета и не
перезапускается. (Тайминги у вас будут другими — машина, прогретость демона;
важен порядок величин, а не конкретные миллисекунды.)

## Чем отличается от Maven-версии того же приложения

|                            | 01-maven                     | 02-gradle                          |
|----------------------------|------------------------------|------------------------------------|
| Описание сборки            | `pom.xml` (XML, декларация)  | `build.gradle` (скрипт, код)       |
| Версии зависимостей        | `<properties>` + depMgmt     | `gradle/libs.versions.toml` (catalog) |
| Порядок работы             | линейный lifecycle (фазы)    | граф задач (DAG)                   |
| `package` ждёт тесты?      | да (фаза test раньше)        | нет (`:jar` и `:test` — siblings)  |
| Пропустить тесты           | `-DskipTests`                | `-x test`                          |
| Конфликт версий            | nearest wins (молча)         | самая новая (молча, стрелка `->`)  |
| Явная фиксация версии      | `dependencyManagement`       | `strictly` / constraints / BOM     |
| Кастомная логика           | плагин (Java-артефакт)       | задача в скрипте                   |
| Скучный jar → `java -jar`  | maven-shade-plugin           | плагин (не делали: идея та же)     |
| Повторная сборка           | пересобирает изменённое      | `UP-TO-DATE` на уровне задач       |
| Фоновый процесс            | нет                          | демон (`--status`)                 |
| Wrapper                    | `./mvnw`                     | `./gradlew`                        |

## Вопросы для самопроверки

1. Что скачает и куда положит `./gradlew` при самом первом запуске? Где
   зафиксирована версия Gradle?
2. Почему `./gradlew jar` собирает jar без запуска тестов, а `mvn package`
   тесты запускает? Покажите ответ на выводе `taskTree`.
3. Что означает стрелка в `gson:2.9.0 -> 2.10.1`? По какому правилу Gradle
   выбрал 2.10.1 и чем это правило отличается от Maven?
4. Как заставить Gradle не «помогать» и держать именно 2.9.0? Что изменится
   в выводе `dependencies`?
5. Где в этом проекте «живут» версии зависимостей? Что нужно поправить,
   чтобы поднять Gson до 2.10.1, и как проверить, что получилось?
6. Почему вторая `./gradlew build` заканчивается за доли секунды? Что означает
   UP-TO-DATE и при чём здесь демон?
7. `printTopErrors` читает `report.json`, который написал запуск `run`. Что
   произойдёт с задачей, если запустить её после `clean`? Проверьте.
