# 03-cmake — тот же log-stats, но на C++ и CMake

log-stats, `log_analyzer <file.log>
[--top N]`, сводка в консоль + отчёт в `report.json`, — но мир другой:
компилируемый язык, машинный код вместо байткода, и CMake вместо Maven/Gradle.
Вход для демо — `samples/app.log` (реальный кусок лога прод-сервиса
веб-звонков, signaler).

Требуется CMake 3.28+ (проверено на 4.3), компилятор C++17 (проверено на
Apple clang 17), для шага с Ninja — сам Ninja (`brew install ninja`).
Интернет нужен один раз: при первой конфигурации FetchContent скачает
nlohmann/json (~112 КБ, архив json.tar.xz).

## Структура

```
03-cmake/
├── CMakeLists.txt        # корень: стандарт, FetchContent, add_subdirectory
├── samples/app.log       # лог «прод-сервиса платежей» — вход для анализа
├── core/                 # библиотека — аналог модуля logstats-core
│   ├── CMakeLists.txt    # add_library(logstats_core ...) — STATIC или SHARED
│   ├── include/logstats/log_parser.hpp, log_stats.hpp
│   └── src/log_parser.cpp, log_stats.cpp
└── app/                  # приложение — аналог Main + JsonReportWriter
    ├── CMakeLists.txt    # add_executable + target_link_libraries
    └── src/main.cpp
```

Сравните с 01-maven: `core` ≈ модуль `logstats-core` (jar-библиотека: парсер
и статистика, зависимостей ноль), `app` ≈ модуль `logstats-app` (CLI и запись
отчёта). Многомодульность здесь родная, а не достижение.

### Шаг 1. Зачем CMake: он не собирает, он генерирует

Открываем `CMakeLists.txt`. Главная мысль слайда 55: у каждого платформенного
сборщика свой формат (Makefile, `.vcxproj`, `.xcodeproj`) — писать и
поддерживать их все руками невозможно. CMake — метасборщик: один
`CMakeLists.txt` на входе, файлы под конкретный сборщик на выходе.

Заодно первое отличие от Maven/Gradle: CMake-файл — это скрипт на своём
маленьком языке.

### Шаг 2. Конфигурация и сборка

Два шага вместо одной команды `./gradlew build`:

```bash
cmake -S . -B build        # КОНФИГУРИРОВАНИЕ: -S — где исходники, -B — куда класть
cmake --build build        # СБОРКА: запускает сгенерированный Makefile
```

Первая команда — это «понять проект и сгенерировать Makefile»: проверка
компилятора, скачивание зависимостей, разбор CMakeLists. Хвост её вывода:

```
-- Using the multi-header code from .../build/_deps/json-src/include/
-- logstats_core: STATIC_LIBRARY (BUILD_SHARED_LIBS=)
-- Configuring done (4.6s)
-- Generating done (0.0s)
-- Build files have been written to: .../03-cmake/build
```

Строка `-- logstats_core: STATIC_LIBRARY ...` — наша учебная печать из
`core/CMakeLists.txt`. Вторая команда — уже чистая сборка:

```
[ 20%] Building CXX object core/CMakeFiles/logstats_core.dir/src/log_parser.cpp.o
[ 40%] Building CXX object core/CMakeFiles/logstats_core.dir/src/log_stats.cpp.o
[ 60%] Linking CXX static library liblogstats_core.a
[ 80%] Building CXX object app/CMakeFiles/log_analyzer.dir/src/main.cpp.o
[100%] Linking CXX executable log_analyzer
```

Видна главная разница с Java: **отдельные шаги компиляции и линковки**
(`Building` → `Linking`), которых в javac не было. Запускаем:

```bash
./build/app/log_analyzer samples/app.log
# [INFO] Запускаемся: log-stats на CMake
# Файл:    samples/app.log
# Строк:   64 (разобрано 58, пропущено 6)
# Уровни:  ERROR 3   INFO 41   WARN 14
# Топ ошибок (ERROR/WARN):
#   1. QUIC exception from src x13
#   2. Unable to send WebSocket x2
#   3. Invalid access token for x1
#   4. exceptionCaught event was fired x1
# [INFO] Отчёт: report.json
```

Что CMake нагенерил в `build/` (аналог `target/` и `build/` из 01/02):

```
_deps/  app/  cmake_install.cmake  CMakeCache.txt  CMakeFiles/  core/  Makefile
```

- `Makefile` — то, ради чего всё затевалось: конфиг для платформенного сборщика;
- `CMakeCache.txt` — «память» конфигурации: выбор генератора, компилятора,
  все `-D`-опции. Смена опции = новая конфигурация, не новая сборка;
- `_deps/` — скачанный FetchContent'ом nlohmann/json (`json-src` — исходники).

### Шаг 3. Другой генератор — тот же CMakeLists

```bash
cmake -S . -B build-ninja -G Ninja
cmake --build build-ninja
```

Вывод сборки — уже не `[25%]`, а счётчик Ninja:

```
[1/5] Building CXX object core/CMakeFiles/logstats_core.dir/src/log_stats.cpp.o
[2/5] Building CXX object core/CMakeFiles/logstats_core.dir/src/log_parser.cpp.o
[3/5] Linking CXX static library core/liblogstats_core.a
[4/5] Building CXX object app/CMakeFiles/log_analyzer.dir/src/main.cpp.o
[5/5] Linking CXX executable app/log_analyzer
```

Исходники одни, `CMakeLists.txt` один, а сборочных каталогов — сколько
хотите, каждый под своим сборщиком. `build/` (Make) и `build-ninja/` живут
рядом и не мешают друг другу. На Windows та же команда с
`-G "Visual Studio 17 2022"` даст solution для студии (см. секцию про
Windows внизу).

у FetchContent есть служебный подпроект в
`build/_deps/json-subbuild`, и он помнит генератор, с которым создавался.
Смешивать генераторы **внутри одного** build-каталога нельзя в принципе
(CMake откажется).

### Шаг 4. Таргеты и переменные

Открываем CMakeLists-файлы и находим словарь слайдов 56–57:

- `add_library(logstats_core ...)` — таргет-библиотека (аналог jar-модуля);
- `add_executable(log_analyzer ...)` — таргет-программа (аналог main-модуля);
- `set(CMAKE_CXX_STANDARD 17)` + `${CMAKE_CURRENT_SOURCE_DIR}` — переменные:
  `set()` кладёт, `${}` читает;
- `add_subdirectory(core)` / `add_subdirectory(app)` — подключение модулей,
  аналог `<modules>` в Maven / `include` в settings.gradle.

Собрать один таргет, не трогая остальные (аналог `./gradlew jar`):

```bash
cmake --build build --target logstats_core
```

### Шаг 5. Связывание: target_link_libraries

В `app/CMakeLists.txt`:

```cmake
target_link_libraries(log_analyzer
    PRIVATE logstats_core                  # наш модуль core
    PRIVATE nlohmann_json::nlohmann_json   # внешняя зависимость
)
```

Это «класспас» C++: что слинковать в бинарь. `PRIVATE` — перекличка с
`implementation` из Gradle (слайд 38): зависимость нужна самому app, его
потребители (если бы он был библиотекой) её видеть не должны. Есть и
`PUBLIC` (аналог `api`) и `INTERFACE` («зависимость только для потребителей»).

Ещё одно отличие от Java: линковка физическая. Код `logstats_core` либо
вклеивается в бинарь (STATIC), либо подгружается из файла при запуске
(SHARED) — в Java выбор «толстый jar или classpath» был вопросом удобства,
здесь он бинарный.

### Шаг 6. Внешняя зависимость: FetchContent

В корневом `CMakeLists.txt`:

```cmake
include(FetchContent)
FetchContent_Declare(
    json
    URL https://github.com/nlohmann/json/releases/download/v3.12.0/json.tar.xz
    URL_HASH SHA256=42f6e95cad6ec532fd372391373363b62a14af6d771056dbfc86160e6dfff7aa
)
FetchContent_MakeAvailable(json)
```

Объявили URL с зафиксированным хешем — и при конфигурации исходники
скачаются в `build/_deps/`, а таргет `nlohmann_json::nlohmann_json`
появится в дереве, как будто это наш подкаталог. Dependency management
существует и здесь: та же идея «объявил — получил», только тянутся не
скомпилированные артефакты из репозитория, а исходники. (`URL_HASH` —
родственник `strictly`/`dependencyManagement`: что скачалось, то и
проверится побайтово.)

Аналог `--offline`: повторная конфигурация с флагом «не ходить в сеть»:

```bash
cmake -S . -B build -DFETCHCONTENT_FULLY_DISCONNECTED=ON
cmake --build build
```

Работает, потому что `_deps` уже тёплый. А вот в **свежий** каталог без
интернета FetchContent исходники взять не откуда — на практике зависимости
либо кладут в проект (vendoring), либо поднимают зеркало. Это огранижение
честнее признать, чем прятать.

### Шаг 7. STATIC против SHARED

Тип библиотеки задаёт не файл, а переменная `BUILD_SHARED_LIBS` — в
`core/CMakeLists.txt` мы написали `add_library` **без** типа. Переключаем:

```bash
cmake -S . -B build-shared -DBUILD_SHARED_LIBS=ON
cmake --build build-shared
# -- logstats_core: SHARED_LIBRARY (BUILD_SHARED_LIBS=ON)
# [ 60%] Linking CXX shared library liblogstats_core.dylib
```

Появился `build-shared/core/liblogstats_core.dylib` вместо `liblogstats_core.a`.
Бинарь запускается из build-каталога — CMake прописал ему rpath. Смотрим,
откуда динамический загрузчик берёт библиотеку:

```bash
otool -L build-shared/app/log_analyzer   # аналог ldd в Linux
#   @rpath/liblogstats_core.dylib (compatibility version 0.0.0, ...)

otool -l build-shared/app/log_analyzer | grep -A2 LC_RPATH
#   path .../03-cmake/build-shared/core (offset 12)
```

А теперь сломаем — унесём бинарь туда, где библиотеки нет. Просто `cp` не
спасёт (rpath указывает абсолютным путём в build-каталог), поэтому в
демо временно переименуем `.dylib`:

```
$ mv build-shared/core/liblogstats_core.dylib build-shared/core/liblogstats_core.dylib.bak
$ ./build-shared/app/log_analyzer samples/app.log
dyld[89828]: Library not loaded: @rpath/liblogstats_core.dylib
  Referenced from: <...> /Users/.../build-shared/app/log_analyzer
  Reason: tried: '.../build-shared/core/liblogstats_core.dylib' (no such file), ...
```

Вот цена SHARED: бинарь меньше, обновляется отдельно от библиотеки — но и
запускается только вместе с ней. Это и есть «scope/упаковка» Java-мира в
мире машинных кодов. (Не забудьте вернуть `.dylib` на место.)

Размеры в нашем проекте (честно):

|                         | STATIC                              | SHARED                              |
|-------------------------|-------------------------------------|-------------------------------------|
| библиотека              | `liblogstats_core.a`, 873 872 байта | `liblogstats_core.dylib`, 546 008 байт |
| `log_analyzer`          | 946 720 байт                        | 480 656 байт                        |
| работает без библиотеки | да                                  | нет (`otool -L` покажет)            |

Библиотека здесь не крошечная (парсер + статистика на `std::regex`), поэтому
разница уже заметна: SHARED-бинарь вдвое меньше. nlohmann header-only и в
обоих случаях компилируется прямо в main.cpp. Главный факт всё равно не
размер, а строка `@rpath/liblogstats_core.dylib` в `otool -L`: у STATIC-версии
её нет.

### Шаг 8. Инкрементальность

Трогаем один файл и пересобираем:

```bash
touch core/src/log_parser.cpp
cmake --build build
# [ 20%] Building CXX object core/CMakeFiles/logstats_core.dir/src/log_parser.cpp.o
# [ 40%] Linking CXX static library liblogstats_core.a
# [ 80%] Linking CXX executable log_analyzer         <-- main.cpp НЕ перекомпилировался
```

`main.cpp` не пересобрался — make увидел, что его объектник новее заголовков,
которые он включает. Повторная сборка без изменений:

```
[ 60%] Built target logstats_core
[100%] Built target log_analyzer
```

Ничего не делаем — это знакомое по Gradle UP-TO-DATE, только уровень
грубее: у make нет хешей входов, он смотрит на время файлов (пресловутый
`touch` поэтому и «ломает» сборку). Инкрементальность **внутри** шага
компиляции (перекомпилировать только затронутые единицы трансляции) —
заслуга make/Ninja, работающих по графу зависимостей.

## Сборка под Windows (не меняя ни строки)

На машине с Visual Studio:

```bat
cmake -S . -B build -G "Visual Studio 17 2022"
cmake --build build --config Release
build\app\Release\log_analyzer.exe samples\app.log
```

Тот же `CMakeLists.txt`, другой генератор — тот же принцип, что с Ninja на
шаге 3. Два отличия: конфигурация Release выбирается на этапе **сборки**
(`--config`), потому что в solution-генераторе все конфигурации живут в
одном каталоге; и `BUILD_SHARED_LIBS=ON` даст `logstats_core.dll` + импортную
библиотеку `.lib`. (На macOS этот сценарий не проверить — здесь он
приведён по документации CMake, без живого прогона.)

## Чем CMake-мир отличается от Maven/Gradle-мира

|                          | Java (Maven/Gradle)            | C++ (CMake)                        |
|--------------------------|--------------------------------|------------------------------------|
| Что собираем             | байткод в jar                  | машинный код: `.o` → линковка      |
| Роль сборщика            | сам собирает и тянет deps      | генерирует конфиги для make/Ninja/MSVC |
| Зависимость              | артефакт из репозитория        | исходники (FetchContent)           |
| Конфликт версий          | nearest-wins / самая новая     | нет: чей URL объявил, тот и скачан |
| Многомодульность         | reactor / включённые проекты   | `add_subdirectory` — родная        |
| Область видимости        | scope / configuration          | `PRIVATE` / `PUBLIC` / `INTERFACE` |
| Пропустить тесты         | `-DskipTests` / `-x test`      | не собираем target `tests` (здесь их нет) |
| Повторная сборка         | UP-TO-DATE (хеши входов)       | «новее по времени» у make/Ninja    |
| Оффлайн                  | `--offline` из кэша репозитория| `-DFETCHCONTENT_FULLY_DISCONNECTED=ON` при тёплом `_deps` |
| «А если jar/библиотеку унесли?» | `NoClassDefFoundError`  | `dyld: Library not loaded`         |

## Вопросы для самопроверки

1. Почему команд две — `cmake -S . -B build` и `cmake --build build`? Что
   происходит в каждой и что из этого повторяется при обычной разработке?
2. Что лежит в `CMakeCache.txt` и почему нельзя просто поменять генератор
   у существующего build-каталога?
3. Как `app` получает код `core`? Что означает `PRIVATE` и чему он
   соответствует в Gradle?
4. Чем FetchContent отличается от зависимости в pom.xml — что именно
   скачивается и когда? Почему `-DFETCHCONTENT_FULLY_DISCONNECTED=ON`
   работает не в любом каталоге?
5. Что изменится между `-DBUILD_SHARED_LIBS=OFF` и `ON`: файлы в build/,
   вывод `otool -L`, поведение бинаря без библиотеки?
6. Почему после `touch core/src/log_parser.cpp` не пересобирается
   `main.cpp`? Чем это правило отличается от UP-TO-DATE в Gradle?
7. Почему цифры в сводке совпадают с Java-версией до последней строки,
   а порядок уровней в консоли и ключей в report.json — нет?
