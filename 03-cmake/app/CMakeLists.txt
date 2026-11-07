# Приложение — аналог Main + JsonReportWriter из Java-версии.
# target_link_libraries — это «класспас» C++: какие таргеты слинковать в бинарь.
# PRIVATE («только для внутреннего пользования») — перекличка с implementation
# из Gradle (слайд 38): потребители app не должны видеть его зависимости.
# Порядок не важен: CMake строит граф таргетов, а не идёт по списку.

add_executable(log_analyzer
    src/main.cpp
)

target_link_libraries(log_analyzer
    PRIVATE logstats_core                    # наш модуль core (STATIC- или SHARED-)
    PRIVATE nlohmann_json::nlohmann_json    # внешняя зависимость из FetchContent
)
