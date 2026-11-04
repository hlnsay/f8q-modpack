# F8Q Launcher build 14

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
