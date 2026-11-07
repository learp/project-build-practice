# Библиотека core — аналог модуля logstats-core из Java-версии
# (LogParser + LogStats, зависимостей ноль).
# Внимание на тип: его здесь НЕТ. add_library без STATIC/SHARED берёт тип из
# переменной BUILD_SHARED_LIBS (по умолчанию выключена → статическая).
# Переключаем на динамику одной опцией конфигурации:
#     cmake -S . -B build -DBUILD_SHARED_LIBS=ON
# — и ни одна строка CMakeLists не меняется (слайд 57: переменные рулят).

add_library(logstats_core
    src/log_parser.cpp
    src/log_stats.cpp
)

# Где искать заголовки клиентам этой библиотеки (app):
# PUBLIC — путь попадёт и в саму библиотеку, и во всех, кто её слинкует.
target_include_directories(logstats_core PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/include)

# Учебная печать: показать, каким типом библиотека собирается прямо сейчас.
get_target_property(LOGSTATS_CORE_TYPE logstats_core TYPE)
message(STATUS "logstats_core: ${LOGSTATS_CORE_TYPE} (BUILD_SHARED_LIBS=${BUILD_SHARED_LIBS})")
