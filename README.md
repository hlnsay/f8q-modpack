# F8Q 1.21.1 [AUTUMN]

Список модов и файлы для сервера F8Q.

## Как установить

1. Установите [Horizon](https://playhorizon.dev) и войдите в аккаунт Microsoft.
2. Скачайте [f8q-updater.exe](https://github.com/hlnsay/f8q-modpack/releases/download/files/f8q-updater.exe) и запустите его. Он создаст сборку и скачает моды.
3. Скачайте [F8Q-Launcher-Setup.exe](https://github.com/hlnsay/f8q-modpack/releases/download/files/F8Q-Launcher-Setup.exe) и установите F8Q Launcher.
4. Откройте F8Q Launcher и нажмите «Играть».

Официальный Horizon удалять нельзя: он делает вход Microsoft и запускает игру.

Первый запуск устанавливает NeoForge и занимает несколько минут. Дальше моды обновляются сами при каждом запуске.

Windows может предупредить о неизвестном издателе: файлы не подписаны.

## Для администратора

После изменения модов в сборке выполните `publish.ps1`. Игра должна быть закрыта.

Публичные моды скачиваются прямо с Modrinth. В релизе `files` лежат только файлы, которых там нет, стартовые настройки, обновлятор и установщик.

Токены и пароли в этот репозиторий не добавляются.

## Лицензии

F8Q Launcher основан на [Horizon](https://github.com/horizon-rust/horizon), текст лицензии MIT лежит в `LICENSE-HORIZON`.
