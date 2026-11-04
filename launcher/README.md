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

Внутренний номер автообновления — 15; он увеличивается независимо от публичной версии.
История лаунчера находится в `launcher.json`, история модпака — в `changelog.json`.
Панель истории рисуется поверх страницы, а тема и установщик используют общую чёрно-белую палитру.
Кнопка закрытия сохраняет стандартное красное наведение Windows.

Остальные файлы `.rs` из этой папки заменяют одноимённые файлы в `launcher/src/`;
`build.rs`, `F8Q_VERSION` и `F8Q_CHANGELOG.json` размещаются в `launcher/`,
`ru.json` — в `launcher/lang/`, `f8q-icon.ico` — в `packaging/icons/horizon.ico`.
