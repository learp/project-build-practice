#!/usr/bin/env bash
# Готовит rootfs/ — «файловую систему контейнера», с которой работает chroot.
#
# Идея демо: образ контейнера — это всего лишь tarball с файлами.
# Docker делает ровно это (слои образа), мы повторяем руками:
# берём alpine, выгружаем его файлы, распаковываем в каталог.
set -euo pipefail
cd "$(dirname "$0")"

docker rm -f rootfs-src >/dev/null 2>&1 || true

docker create --name rootfs-src alpine true
docker export rootfs-src -o rootfs.tar
docker rm rootfs-src >/dev/null

rm -rf rootfs
mkdir rootfs
tar -xf rootfs.tar -C rootfs

echo "OK: rootfs/ готов ($(du -sh rootfs | cut -f1), alpine)"
echo "Запуск игрушечного хоста:"
echo '  docker run -it --privileged --cgroupns=host -v "$(pwd)/rootfs:/rootfs" demo-host bash'
