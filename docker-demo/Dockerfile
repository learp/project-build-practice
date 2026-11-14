# «Игрушечный хост» для демо «контейнер из примитивов Linux».
# Это НЕ контейнер для приложения — это чистый Linux, в котором мы будем
# строить контейнер руками: unshare, chroot, cgroups.
#
# Сборка:  docker build -t demo-host .
# Запуск:  docker run -it --privileged --cgroupns=host -v "$(pwd)/rootfs:/rootfs" demo-host bash
#
# --privileged — обязательный: создание namespaces/cgroups требует полномочий.
# --cgroupns=host — обязательно на Docker Desktop (cgroup v2): без него запись
#   в /sys/fs/cgroup/.../memory.max даёт Permission denied.
FROM debian:bookworm-slim

RUN apt-get update && apt-get install -y --no-install-recommends \
        # unshare, chroot, mount — сами примитивы из util-linux
        util-linux \
        # ps aux
        procps \
        # ip a (показывать сетевой стек)
        iproute2 \
        # жертва для OOM-kill: одна строка python просит гигабайт —
        # и видимо умирает от руки ядра (stress-ng умирает слишком стоически)
        python3 \
    && rm -rf /var/lib/apt/lists/*

# Примечание: smoke-тест unshare здесь невозможен — сборка образа идёт
# в непривилегированном контейнере (ещё одна иллюстрация изоляции).
# Проверка демо — в прогоне с --privileged, см. README.
