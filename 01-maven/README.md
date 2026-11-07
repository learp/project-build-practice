# 01-maven — тот же log-stats, но с Maven

Приложение — **log-stats, анализатор логов**: читает лог-файл, считает
уровни и топ ошибок, пишет `report.json`. Вход для демо — `samples/app.log`
(реальный кусок лога прод-сервиса веб-звонков, signaler).

Требуется JDK 21+ (проверено на 25). **Maven ставить не нужно** — в проекте
wrapper (`./mvnw`), который при первом запуске сам скачает Maven 3.9.9.
CLI приложения: `Main <file.log> [--top N]`.

## Lifecycles maven
https://maven.apache.org/guides/introduction/introduction-to-the-lifecycle.html#Lifecycle_Reference
./mvnw help:describe -Dcmd=package        # покажет цепочку фаз до package


## Два состояния проекта

| Директория | Что это | Шаги демо |
|---|---|---|
| `step-single/` | одномодульный проект `logstats` | 1–6 |
| корень (`pom.xml` + `logstats-core/` + `logstats-app/`) | итог рефакторинга в модули | 7 |

Шаги 1–6 выполняем в `step-single/`; на шаге 7 открываем корень — там уже
разрезанное на модули приложение. Оба состояния собираются зелёными.

---

## Прогон практики (в `step-single/`, все шаги проверены)

### Шаг 1. Сравнение с 00-manual

Открываем рядом деревья каталогов. Классы те же, но легли **по конвенции**:
`src/main/java` (код), `src/main/resources` (logback.xml), `src/test/java`
(тесты). Maven не надо объяснять, где что лежит, — а `javac` в 00-manual
объясняли мы. В `pom.xml` читаем координаты проекта (GAV):

```
best.project : logstats : 1.0.0      <- groupId : artifactId : version
```

По этим координатам наш jar ляжет в `~/.m2/repository/best/project/logstats/1.0.0/`
после `install` (шаг 7). Зависимости в pom объявляются теми же координатами.

**Wrapper.** Запускаем всё через `./mvnw`, а не `mvn`: версия сборщика
версионируется вместе с проектом, и у всей команды (и у CI) она одинаковая.
Первый запуск скачает дистрибутив Maven в `~/.m2/wrapper/` — это нормально.

### Шаг 2. Первая сборка и джентльменский набор

```bash
./mvnw package
```

Ожидаемый хвост вывода:

```
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.038 s -- in best.project.ever.LogParserTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.006 s -- in best.project.ever.LogStatsTest
[INFO] Building jar: .../step-single/target/logstats-1.0.0.jar
[INFO] BUILD SUCCESS
[INFO] Total time:  1.066 s
```

Смотрим, что появилось в `target/` (аналог `out/` из 00-manual, только с
отчётами и метаданными):

```
classes/  test-classes/  surefire-reports/  maven-status/  maven-archiver/
generated-sources/  generated-test-sources/  logstats-1.0.0.jar
```

(`generated-sources/` появился не просто так: это след annotation processing —
Lombok работает на этапе компиляции.)

Компиляция, копирование ресурсов, тесты, упаковка — всё произошло само, по
фазам lifecycle. Теперь типовой набор команд и разница между ними:

```bash
./mvnw clean package        # удалить target/, собрать заново с тестами
./mvnw clean install        # то же + положить наш jar в ~/.m2/repository/
./mvnw package -DskipTests  # собрать БЕЗ тестов (быстро, для локальных проб)
```

`clean` стирает `target/` целиком — без него Maven пересобирает только
изменившееся (помните `rm -rf out` руками в run.sh?).

### Шаг 3. Вскрытие jar

```bash
unzip -l target/logstats-1.0.0.jar
```

Внутри: наши классы, `logback.xml` (ресурсы Maven скопировал сам!), и всё.
**Lombok нет. Gson нет.** Обычный jar содержит только наш код — как и
`logstats.jar` из 00-manual. Запустить можно, но classpath придётся собирать сам:

```bash
M2=~/.m2/repository
java -cp "target/logstats-1.0.0.jar:$M2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar:$M2/org/slf4j/slf4j-api/2.0.9/slf4j-api-2.0.9.jar:$M2/ch/qos/logback/logback-classic/1.4.14/logback-classic-1.4.14.jar:$M2/ch/qos/logback/logback-core/1.4.14/logback-core-1.4.14.jar" best.project.ever.Main samples/app.log
```

Работает — все зависимости уже лежат в `~/.m2` (их скачал Maven при первой
сборке). Но руками перечислять пути неприятно. Хочется `java -jar` → шаг 5.

### Шаг 4. Scope-игра: чем отличаются scope'ы на практике

Смотрим в pom: у зависимостей есть `scope`. Меняем и наблюдаем.

**(а) `logback-classic`: `runtime` → `compile`.** Пересобираем, снова
`unzip -l target/logstats-1.0.0.jar` — **ничего не изменилось**. Scope — не про
содержимое jar (зависимости в обычный jar и так не попадают), а про то, в
каком classpath зависимость участвует: компиляции, тестов, запуска.

**(б) `gson`: `compile` → `provided`.** Компилируется (provided доступен на
этапе компиляции), собирается, а при запуске Gson в classpath уже нет:

```bash
./mvnw compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java -Dexec.mainClass=best.project.ever.Main -Dexec.args="samples/app.log"
# 17:29:22.353 INFO  best.project.ever.Main - Запускаемся: log-stats на Maven
# 17:29:22.355 INFO  best.project.ever.Main - Файл:    samples/app.log
# ... (вся сводка напечатается — и только потом крах)
# [ERROR] ... An exception occurred while executing the Java class.
#         com/google/gson/GsonBuilder: com.google.gson.GsonBuilder
```

`exec:java` собирает classpath запуска ИЗ pom — и Gson там не оказалось
(он нужен `JsonReportWriter`'у, без него отчёт не соберётся).
`NoClassDefFoundError`, знакомый по 00-manual. Именно так «ведёт себя»
provided: контейнером обещали предоставить (как servlet-api в Tomcat), а тут
контейнера не было. **Верните `compile` обратно.**

Итог по scope'ам: `compile` (везде), `provided` (компиляция, но не запуск),
`runtime` (запуск, но не компиляция — наш код её не видит), `test` (только
тесты). Плюс экзотика: `system` (jar по локальному пути — почти всегда
антипаттерн) и `import` (в `<dependencyManagement>`, для BOM).

### Шаг 5. Жирный jar: maven-shade-plugin

Добавьте в `<build><plugins>` (полный работающий сниппет):

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-shade-plugin</artifactId>
    <version>3.6.2</version>
    <executions>
        <execution>
            <phase>package</phase>
            <goals>
                <goal>shade</goal>
            </goals>
            <configuration>
                <shadedArtifactAttached>true</shadedArtifactAttached>
                <shadedClassifierName>jar-with-dependencies</shadedClassifierName>
                <transformers>
                    <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                        <mainClass>best.project.ever.Main</mainClass>
                    </transformer>
                </transformers>
            </configuration>
        </execution>
    </executions>
</plugin>
```

(`shadedArtifactAttached` оставляет оба jar — «тонкий» и «жирный» — чтобы
сравнить. `ManifestResourceTransformer` пишет `Main-Class` в манифест —
то самое, что в 00-manual мы печатали в manifest.txt руками.)

```bash
./mvnw clean package
ls -la target/*.jar
# -rw-r--r--  ...  18489  target/logstats-1.0.0.jar                        <- тонкий: только наш код
# -rw-r--r--  ...  1897879  target/logstats-1.0.0-jar-with-dependencies.jar  <- жирный: 1.9 MB

unzip -l target/logstats-1.0.0-jar-with-dependencies.jar | grep -cE 'com/google/gson|ch/qos/logback'
# 936   <- Gson и logback теперь ВНУТРИ
unzip -l target/logstats-1.0.0-jar-with-dependencies.jar | grep -ci lombok
# 0     <- Lombok по-прежнему нет: scope provided

java -jar target/logstats-1.0.0-jar-with-dependencies.jar samples/app.log
# 12:20:12.253 INFO  best.project.ever.Main - Запускаемся: log-stats на Maven
# 12:20:12.270 INFO  best.project.ever.Main - Файл:    samples/app.log
# Строк:   64 (разобрано 58, пропущено 6)
# Уровни:  ERROR   3   WARN   14   INFO   41
# Топ ошибок (ERROR/WARN):
#   1. QUIC exception from src                  x13
#   2. Unable to send WebSocket                 x2
#   3. Invalid access token for                 x1
#   4. exceptionCaught event was fired          x1
# 12:20:12.282 INFO  best.project.ever.Main - Отчёт: report.json
```

В `step-single/` на диске pom хранится **без** shade — это стартовое состояние
демо; в многомодульном `logstats-app` (шаг 7) плагин уже прописан.

### Шаг 6. Дерево зависимостей и ловушка №2

В pom с самого начала лежит «зависимость коллеги» — `converter-gson:2.9.0`
«для будущего HTTP-клиента». Ничего не падает. Смотрим, что на самом деле
произошло:

```bash
./mvnw dependency:tree -Dverbose
```

Ключевой фрагмент (сравните с тем, как выглядела та же история в 00-manual
с банками в lib/):

```
[INFO] +- com.google.code.gson:gson:jar:2.10.1:compile
[INFO] +- org.slf4j:slf4j-api:jar:2.0.9:compile
[INFO] +- ch.qos.logback:logback-classic:jar:1.4.14:runtime
[INFO] |  +- ch.qos.logback:logback-core:jar:1.4.14:runtime
[INFO] |  \- (org.slf4j:slf4j-api:jar:2.0.7:runtime - omitted for conflict with 2.0.9)
[INFO] +- com.squareup.retrofit2:converter-gson:jar:2.9.0:compile
[INFO] |  +- com.squareup.retrofit2:retrofit:jar:2.9.0:compile
[INFO] |  |  \- com.squareup.okhttp3:okhttp:jar:3.14.9:compile
[INFO] |  |     \- com.squareup.okio:okio:jar:1.17.2:compile
[INFO] |  \- (com.google.code.gson:gson:jar:2.8.5:compile - omitted for conflict with 2.10.1)
```

Находим ловушку: `(gson:jar:2.8.5 - omitted for conflict with 2.10.1)`.
Maven **уже решил** конфликт по принципу **nearest wins**: наша прямая
зависимость в дереве ближе, чем транзитивная, — победила 2.10.1. Заметьте:
одна строчка «на будущее» приволокла ещё retrofit → okhttp → okio, и
внезапно конфликтует ещё и slf4j (2.0.7 против 2.0.9) — его тоже кто-то
уже «решил».

Чем опасно «молча»: конфликт не виден ни в сборке, ни в запуске. Про версию
Gson, с которой реально работает приложение, знает только дерево.
Лекарство — сделать решение явным и централизованным: `dependencyManagement`
в родительском pom (шаг 7). Заодно сравните с Gradle (проект 02): там конфликт
решается по другому правилу — и молча тоже.

### Шаг 7. Многомодульность (переходим в корень `01-maven/`)

Приложение разрезано на модули:

```
01-maven/
├── pom.xml          <- parent: packaging pom, <modules>, dependencyManagement
├── logstats-core/   <- БИБЛИОТЕКА: LogParser, LogStats (зависимостей нет вообще)
└── logstats-app/    <- ПРИЛОЖЕНИЕ: Main, JsonReportWriter, Report (DTO); зависит от logstats-core
```

Собираем из корня — **реактор** сам выстроит порядок:

```bash
./mvnw clean verify
# [INFO] Building logstats-parent 1.0.0                                   [1/3]
# [INFO] Building logstats-core 1.0.0                                     [2/3]
# [INFO] Building logstats-app 1.0.0                                      [3/3]
```

Почему core раньше app? Maven видит ребро зависимости, топологически
сортирует граф модулей. (Не «фазы по порядку», как в lifecycle внутри
модуля, — граф на уровне модулей.)

Публикация библиотеки в локальный репозиторий:

```bash
./mvnw clean install
ls ~/.m2/repository/best/project/logstats-core/1.0.0/
# logstats-core-1.0.0.jar  logstats-core-1.0.0.pom  ...
```

В `logstats-app/pom.xml` зависимость от своего модуля объявлена **без версии** —
версию держит `dependencyManagement` родителя. Там же — то самое лекарство
от ловушки №2, и оно видно в дереве:

```bash
./mvnw dependency:tree -Dverbose
# [INFO] |  \- (com.google.code.gson:gson:jar:2.10.1:compile - version managed from 2.8.5; omitted for duplicate)
```

Сравните со step-single: было «omitted for conflict» (nearest wins, молча) —
стало «**version managed from 2.8.5**» (версию назначил родитель, явно).
Одна и та же ситуация, два уровня контроля.

Жирный jar из модуля: `java -jar logstats-app/target/logstats-app-1.0.0-jar-with-dependencies.jar samples/app.log`.

### Шаг 8. XSD: документация всех тегов pom

В шапке `pom.xml` есть `xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
http://maven.apache.org/xsd/maven-4.0.0.xsd"` — откройте вторую ссылку в
браузере. Это схема, описывающая ВСЕ допустимые теги pom.xml. IDE по ней
подсказывает и валидирует.

## Вопросы для самопроверки

1. Чем `./mvnw` отличается от `mvn`? Что скачается при самом первом запуске и куда?
2. Что появится в `target/` после `clean package`, и что дополнительно сделает `install`?
3. Почему Lombok со scope `provided` не ломает приложение в рантайме, а Gson со scope `provided` — ломает?
4. Что означает запись `(com.google.code.gson:gson:jar:2.8.5 - omitted for conflict with 2.10.1)`? Кто и по какому правилу принял это решение?
5. Чем `dependencyManagement` отличается от `dependencies`? Что выигрывает версия, объявленная в модуле, у конкурирующей с версией из management?
6. Почему реактор собирает `logstats-core` раньше `logstats-app`, хотя в `<modules>` они перечислены в другом порядке? (Проверьте: поменяйте порядок и соберите.)
7. Почему `LogParser` и `LogStats` попали в модуль-библиотеку, а `JsonReportWriter` и `Report` — в приложение? Что у них общего с 00-manual, где парсер компилировался «голым» javac?
