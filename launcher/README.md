# F8Q Launcher v.0.0.1

Изменения интерфейса: собственная SVG-иконка обновления, независимое сворачивание
«Что нового», обновление обоих блоков после установки без перезапуска лаунчера.
Список модов и история читаются из одного опубликованного `manifest.json`.
Ответ проверки, начатой до обновления, не перезаписывает более свежие данные.

Исходники поверх локальной копии Horizon:

- `server_home.rs` → `launcher/src/server_home.rs`;
- `theme-icons.rs` → `crates/horizon-theme/src/lib.rs`;
- `f8q-refresh.svg` → `crates/horizon-theme/icons/glass/f8q-refresh.svg`.

Сборка: `cargo build --release --manifest-path launcher/Cargo.toml`.
Лицензия исходного Horizon: MIT, см. `LICENSE-HORIZON` в корне репозитория.

Build 14 также обходит кэш метаданных GitHub при обновлении лаунчера, первой установке и обновлении сборки.

Внутренний номер автообновления — 16; он увеличивается независимо от публичной версии.
История лаунчера находится в `launcher.json`, история модпака — в `changelog.json`.
Панель истории рисуется поверх страницы, а тема и установщик используют общую чёрно-белую палитру.
Кнопка закрытия сохраняет стандартное красное наведение Windows.

Остальные файлы `.rs` из этой папки заменяют одноимённые файлы в `launcher/src/`;
`build.rs`, `F8Q_VERSION` и `F8Q_CHANGELOG.json` размещаются в `launcher/`,
`ru.json` — в `launcher/lang/`, `f8q-icon.ico` — в `packaging/icons/horizon.ico`.

Version 0.0.2 restores saved appearance, accent, background, glass and blur settings. Monochrome is an additional default accent. game_session.rs prevents duplicate game starts and drives the immediate launch status. FancyMenu assets are distributed in config/fancymenu.


Версия 0.0.8: вход по нику добавлен в настройки аккаунта. Гостевые сеансы Java запускает сама сборка F8Q; вход Microsoft и обновление его сессии сохраняют прежний официальный механизм. Новые файлы account.rs, cli.rs, glass_account.rs, owner_page.rs, switch_page.rs размещаются в launcher/src/, account/tests.rs — в launcher/src/account/. Серверная часть и результаты проверок находятся в ../server-access/. В репозитории нет паролей и персональных таблиц идентификаторов.

Версия 0.0.9 убирает подсказки регистрации и пароля: сервер подключает игроков сразу по нику.

Версия 0.0.10: launcher_update.rs (и объявление модуля в lib.rs) заменяет файлы с уникальными резервными именами под межпроцессной блокировкой. Наличие старого .old не считается завершённым обновлением. Подтверждение запуска новой версии обязательно; при ошибке восстанавливается предыдущая. Проверено на Windows с реально запущенным старым исполняемым файлом.
