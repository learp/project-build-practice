# docker-demo — контейнер из примитивов Linux

Материалы к Docker-части лекции «Непрерывная интеграция»: слот
«мини-докер» на слайде про контейнеризацию в Linux.

Идея: Docker не изобретал изоляцию — он собрал удобную обёртку над
тем, что Linux умел всегда. Мы собираем контейнер руками:

- **namespaces** — изоляция того, что процесс *видит* (PID, сеть, hostname);
- **chroot** — изоляция файловой системы;
- **cgroups** — изоляция того, что процесс может *использовать* (память, CPU).

Всё проверено на macOS + Docker Desktop (Apple Silicon). Дома
повторяется один в один: Docker Desktop поднимает ту же Linux-VM.

## Подготовка (один раз)

```bash
./prepare-rootfs.sh        # rootfs/ — файловая система контейнера из alpine
docker build -t demo-host .  # «игрушечный хост»: debian + утилиты для демо
```

`prepare-rootfs.sh` делает ровно то, что Docker делает со слоями образа:
выгружает файлы alpine в tarball и распаковывает. Образ контейнера —
это просто tarball с файловой системой.

## Запуск игрушечного хоста

```bash
docker run -it --privileged --cgroupns=host -v "$(pwd)/rootfs:/rootfs" demo-host bash
```

Оба флага обязательны (проверено на Docker Desktop, cgroup v2):

| Флаг | Зачем |
|---|---|
| `--privileged` | Создание namespaces и запись в cgroups требует полномочий; обычным контейнерам это запрещено. По сути мы на минуту становимся тем, кем является `dockerd` |
| `--cgroupns=host` | Без него `memory.max` внутри контейнера даёт `Permission denied` |

Честная оговорка для студентов: наш «хост» — это привилегированный
контейнер внутри Linux-VM Docker Desktop. Для демонстрации примитивов
это не имеет значения — внутри него самый обычный Linux.

## Сценарий: собираем контейнер руками

### 1. Обычный мир

```bash
for i in $(seq 8); do sleep 600 & done   # антураж: пусть будет «оживлённый хост»
jobs                                     # 8 фоновых sleep
ps aux          # процессов много — целый мир
hostname        # случайный id контейнера
ip a            # полный сетевой стек хоста
```

### 2. PID namespace — «процесс один в мире»

```bash
unshare --pid --fork --mount-proc bash
ps aux          # ДВЕ строки: bash и сам ps. Процесс думает, что он один
exit
```

### 3. UTS namespace — своё имя хоста

```bash
unshare --uts bash -c 'hostname mini && hostname'   # mini — только внутри
```

### 4. NET namespace — свой сетевой стек

```bash
unshare --net bash -c 'ip -brief a; echo ---; ip r'
# eth0 исчез; адресов нет ни у одного интерфейса (даже 127.0.0.1 у lo
# не назначен); таблица маршрутов пустая.
# tunl0/gre0/gretap0/... — туннельные заготовки ядра (built-in модули),
# создаются в КАЖДОМ net ns автоматически, всегда DOWN и без адресов —
# не считать за «настоящие» интерфейсы. Проверено 14.11.
```

### 5. chroot — свой корень файловой системы

```bash
chroot /rootfs /bin/sh   # мы внутри alpine; всего остального «не существует»
ls /
exit
```

### 6. Всё вместе одной командой — это контейнер

```bash
unshare --mount --pid --fork --net --uts \
  chroot /rootfs /bin/sh -c 'mount -t proc proc /proc; hostname demo; ps aux; ls /'
```

Один процесс, lo-only, alpine внутри. **Это контейнер. Десять секунд
и ноль докеров.**

Деталь: `mount -t proc proc /proc` нужен, потому что `--mount-proc`
монтирует proc *до* `chroot` — в новый корень он не попадает. `runc`
при старте контейнера делает ровно этот mount сам.

### 7. cgroups — лимит памяти и OOM-kill

```bash
mkdir /sys/fs/cgroup/boom
echo 200M > /sys/fs/cgroup/boom/memory.max
echo 0    > /sys/fs/cgroup/boom/memory.swap.max   # иначе уедет в своп и выживет!
echo $$   > /sys/fs/cgroup/boom/cgroup.procs      # наш процесс — внутрь

python3 -c 'a = bytearray(1_000_000_000)'   # просим гигабайт
# bash: 9 Killed ...   exit code 137 — ядро убило процесс за превышение лимита

python3 -c 'a = bytearray(100_000_000)'     # 100 мегабайт — в лимит влезает
# жив, exit code 0
```

Грабля с `memory.swap.max` — главная в этом демо: по умолчанию своп
разрешён, и процесс с гигабайтом просто уезжает в него, ничем не умирая.
Свойство `0` закрывает своп — лимит становится железным.

Счётчик убийств ядра можно показать так:

```bash
cat /sys/fs/cgroup/boom/memory.events   # oom_kill: сколько раз сработало
```

### 8. Финал: сравнить с Docker

```bash
docker run --rm alpine ps aux   # то же самое, что мы сделали руками
```

Docker не добавляет магии — он автоматизирует ровно эти примитивы
(это делает `runc`), плюс образы, registry и сеть по умолчанию.

## Уборка

```bash
echo $$ > /sys/fs/cgroup/cgroup.procs   # вернуть себя в корень (пока мы в boom, каталог «занят»)
rmdir /sys/fs/cgroup/boom
```

## Что проверено, какие грабли пойманы

| Симптом | Причина | Лечение |
|---|---|---|
| `memory.max: Permission denied` | private cgroup namespace у контейнера | флаг `--cgroupns=host` |
| stress-ng/python «выживает» с 1G при лимите 200M | своп: `memory.swap.max` по умолчанию `max` | `echo 0 > memory.swap.max` |
| `ps` пустой внутри собранного контейнера | `--mount-proc` монтирует proc до `chroot` | `mount -t proc proc /proc` внутри |
| stress-ng «successfully completed» при OOM | он перезапускает воркеров и считает это успехом | жертва — однострочник python: смерть видна (`Killed`, exit 137) |
