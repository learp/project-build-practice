#!/usr/bin/env bash

set -euo pipefail
cd "$(dirname "$0")"

# --- 0. Наши «жизненные циклы»: clean делаем сами ---
rm -rf out logstats.jar report.json
mkdir -p out

# --- 1. Компиляция ---
javac \
  -cp lib/gson-2.10.1.jar:lib/slf4j-api-2.0.9.jar \
  -d out \
  src/best/project/ever/*.java

# --- 2. Ресурсы ---
cp resources/logback.xml out/

# --- 3. Упаковка ---
jar -cvf out/logstats.jar -C out .

# --- 4. Запуск ---
java -cp "out/logstats.jar:lib/gson-2.10.1.jar:lib/slf4j-api-2.0.9.jar:lib/logback-classic-1.4.14.jar:lib/logback-core-1.4.14.jar" best.project.ever.Main "$@"
