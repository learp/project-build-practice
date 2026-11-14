# Multi-stage образ log-stats: JDK на сборке, JRE на рантайме.
#
# ВАЖНО (грабля с практики Maven): плагин application пишет Main-Class в скрипты
# дистрибутива, а не в манифест, и в jar нет зависимостей. Поэтому собираем
# installDist (bin/logstats + lib/*.jar) и запускаем скрипт — он сам соберёт
# classpath. java -jar от обычного jar упадёт с "no main manifest attribute".

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src

# Сначала конфиг сборки — слой кэшируется, пока не поменяются зависимости.
# Меняем код — пересобирается только последний слой (эксперимент с CACHED на лекции).
COPY gradle/ gradle/
COPY gradlew settings.gradle build.gradle ./
COPY src/ src/
COPY samples/ samples/

# --no-daemon: сборка одноразовая, daemon внутри слоя не переживёт (тема слайда a2)
RUN ./gradlew --no-daemon installDist

FROM eclipse-temurin:21-jre
COPY --from=build /src/build/install/logstats/ /opt/logstats/
COPY --from=build /src/samples/ /opt/logstats/samples/
WORKDIR /opt/logstats

ENTRYPOINT ["/opt/logstats/bin/logstats"]
CMD ["samples/app.log"]
