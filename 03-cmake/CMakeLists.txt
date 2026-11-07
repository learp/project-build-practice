# То же приложение log-stats, что в 01-maven и 02-gradle, — но на C++ и CMake.
# Сюжет не меняется, меняется мир: вместо байткода и jar — машинный код,
# вместо репозитория зависимостей — FetchContent, вместо pom.xml/build.gradle —
# этот файл. CMake НЕ собирает проект сам: он ГЕНЕРИРУЕТ файлы для платформенного
# сборщика (Makefile, Ninja, Visual Studio, Xcode) — их потом запускает cmake --build.
#
# Проверка: cmake -S . -B build && cmake --build build && ./build/app/log_analyzer samples/app.log
#
# Комментарии здесь — только решётка: C-style /* */ язык CMake не знает.

cmake_minimum_required(VERSION 3.28)
project(logstats VERSION 1.0.0 LANGUAGES CXX)

# Переменные: set() кладёт значение, ${} — читает (слайд 57).
# C++17 — фиксируем стандарт в одном месте, для всех подкаталогов.
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)   # нет стандарта — ошибка, а не «как получится»
set(CMAKE_CXX_EXTENSIONS OFF)         # -std=c++17, а не -std=gnu++17

# ---------------------------------------------------------------------------
# Внешняя зависимость: nlohmann/json — «Gson мира C++» (header-only).
# FetchContent скачивает исходники при КОНФИГУРИРОВАНИИ и добавляет их в
# дерево сборки, как будто это наш подкаталог: объявили — и в дереве
# появилась зависимость, как Gson в Maven/Gradle. Лежит она в build/_deps/
# (у каждого сборочного каталога — своя копия; подробности в README).
# ---------------------------------------------------------------------------
include(FetchContent)
FetchContent_Declare(
    json
    URL https://github.com/nlohmann/json/releases/download/v3.12.0/json.tar.xz
    URL_HASH SHA256=42f6e95cad6ec532fd372391373363b62a14af6d771056dbfc86160e6dfff7aa
)
FetchContent_MakeAvailable(json)

# Модули проекта: каждый описан своим CMakeLists.txt в своём каталоге.
add_subdirectory(core)
add_subdirectory(app)
