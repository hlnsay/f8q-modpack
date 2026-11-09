//! What a double click on the exe does: makes F8Q's instance in the player's Horizon, with the
//! starter settings, the server in its list and this program as its launch wrapper.
use crate::sync::{self, Manifest};
use serde_json::Value;
use std::fs;
use std::io::{self, Read, Write};
use std::path::{Component, Path, PathBuf};
use std::process::Command;

pub const MANIFEST_URL: &str =
    "https://raw.githubusercontent.com/hlnsay/f8q-modpack/main/manifest.json";
const STARTER_URL: &str =
    "https://github.com/hlnsay/f8q-modpack/releases/download/files/f8q-starter.zip";
const OFFICIAL_SETUP_URL: &str =
    "https://github.com/horizon-rust/horizon/releases/download/launcher%400.2.38/horizon-launcher-setup.exe";
const NAME: &str = "F8Q 1.21.1 [AUTUMN]";
const ADDRESS: &str = "f8quit.exaroton.me:30096";
const STARTER_DONE: &str = ".f8q-starter-done";
/// Settings files that describe this computer or its player, not the pack.
const NOT_IN_STARTER: [&str; 2] = ["cozy-options-before-update.txt", "sounds/chat.json"];

/// Whether a starter entry may be written: only the settings, never out of the game folder.
pub fn starter_path(name: &str) -> Option<PathBuf> {
    let path = Path::new(name);
    if !path.components().all(|c| matches!(c, Component::Normal(_))) || name.contains('\\') {
        return None;
    }
    (name == "options.txt" || name.starts_with("config/")).then(|| path.to_path_buf())
}

fn config_files(dir: &Path, base: &Path, out: &mut Vec<(String, PathBuf)>) -> io::Result<()> {
    for item in fs::read_dir(dir)? {
        let path = item?.path();
        if path.is_dir() {
            config_files(&path, base, out)?;
        } else {
            let relative = path.strip_prefix(base).unwrap().to_string_lossy().replace('\\', "/");
            if !NOT_IN_STARTER.contains(&relative.as_str()) {
                out.push((format!("config/{relative}"), path));
            }
        }
    }
    Ok(())
}

/// The settings to publish, by their paths in the game folder.
pub fn published_config(home: &Path) -> Result<Vec<(String, PathBuf)>, String> {
    let mut files = Vec::new();
    let config = home.join("config");
    config_files(&config, &config, &mut files).map_err(|e| e.to_string())?;
    files.sort();
    Ok(files)
}

/// The personal lines of options.txt go; the pack order and keys stay.
fn clean_options(text: &str) -> String {
    text.lines()
        .filter(|l| !l.starts_with("lastServer:"))
        .collect::<Vec<_>>()
        .join("\n")
        + "\n"
}

/// `f8q-updater starter <game dir> <out.zip>`.
pub fn make_starter(home: &Path, out: &Path) -> Result<(), String> {
    let mut files = Vec::new();
    let config = home.join("config");
    config_files(&config, &config, &mut files).map_err(|e| e.to_string())?;
    files.sort();
    let file = fs::File::create(out).map_err(|e| e.to_string())?;
    let mut zip = zip::ZipWriter::new(file);
    let options = zip::write::SimpleFileOptions::default()
        .compression_method(zip::CompressionMethod::Deflated);
    for (name, path) in &files {
        zip.start_file(name.as_str(), options).map_err(|e| e.to_string())?;
        zip.write_all(&fs::read(path).map_err(|e| e.to_string())?)
            .map_err(|e| e.to_string())?;
    }
    let text = fs::read_to_string(home.join("options.txt")).map_err(|e| e.to_string())?;
    zip.start_file("options.txt", options).map_err(|e| e.to_string())?;
    zip.write_all(clean_options(&text).as_bytes()).map_err(|e| e.to_string())?;
    zip.finish().map_err(|e| e.to_string())?;
    println!("{} settings files in {}", files.len() + 1, out.display());
    Ok(())
}

fn unpack_starter(bytes: Vec<u8>, home: &Path) -> Result<usize, String> {
    let mut zip = zip::ZipArchive::new(io::Cursor::new(bytes)).map_err(|e| e.to_string())?;
    let mut written = 0;
    for i in 0..zip.len() {
        let mut entry = zip.by_index(i).map_err(|e| e.to_string())?;
        if entry.is_dir() {
            continue;
        }
        let Some(relative) = starter_path(entry.name()) else {
            return Err(format!("unexpected file in the starter: {}", entry.name()));
        };
        let dest = home.join(relative);
        if let Some(dir) = dest.parent() {
            fs::create_dir_all(dir).map_err(|e| e.to_string())?;
        }
        let mut body = Vec::new();
        entry.read_to_end(&mut body).map_err(|e| e.to_string())?;
        fs::write(dest, body).map_err(|e| e.to_string())?;
        written += 1;
    }
    Ok(written)
}

fn in_folder(dir: &Path) -> Option<PathBuf> {
    let exe = dir.join("horizon-launcher.exe");
    exe.is_file().then_some(exe)
}

/// `dir` and the folders directly inside it: unpacked zips land one level down.
fn with_subfolders(dir: PathBuf) -> Vec<PathBuf> {
    let mut dirs: Vec<PathBuf> = fs::read_dir(&dir)
        .map(|items| items.flatten().map(|i| i.path()).filter(|p| p.is_dir()).collect())
        .unwrap_or_default();
    dirs.insert(0, dir);
    dirs
}

fn find_launcher() -> Option<PathBuf> {
    // The F8Q launcher names its own copy of Horizon.
    if let Some(engine) = std::env::var_os("F8Q_ENGINE").map(PathBuf::from) {
        if engine.is_file() {
            return Some(engine);
        }
    }
    let home = std::env::var_os("USERPROFILE").map(PathBuf::from)?;
    let mut dirs = Vec::new();
    if let Some(local) = std::env::var_os("LOCALAPPDATA") {
        dirs.extend(with_subfolders(PathBuf::from(local).join("Programs")));
    }
    for place in ["Desktop", "OneDrive/Desktop", "Downloads", "Documents"] {
        dirs.extend(with_subfolders(home.join(place)));
    }
    dirs.iter().find_map(|d| in_folder(d))
}

/// Installs the official launcher for this user: it signs in and starts the game.
fn install_official(agent: &ureq::Agent) -> Result<PathBuf, String> {
    println!("Horizon не найден, устанавливаю его");
    let mut bytes = Vec::new();
    agent
        .get(OFFICIAL_SETUP_URL)
        .call()
        .map_err(|e| format!("Horizon: {e}"))?
        .into_reader()
        .read_to_end(&mut bytes)
        .map_err(|e| format!("Horizon: {e}"))?;
    let dir = std::env::temp_dir().join("f8q-horizon-setup");
    fs::create_dir_all(&dir).map_err(|e| e.to_string())?;
    // The launcher takes this name as "run as the setup".
    let setup = dir.join("horizon-launcher-setup.exe");
    fs::write(&setup, bytes).map_err(|e| e.to_string())?;
    let status = Command::new(&setup)
        .args(["setup", "--quiet", "--desktop-shortcut"])
        .status()
        .map_err(|e| format!("Horizon: {e}"))?;
    let _ = fs::remove_dir_all(&dir);
    if !status.success() {
        return Err("Horizon не установился".into());
    }
    find_launcher().ok_or_else(|| "Horizon установлен, но не найден".to_string())
}

fn run(launcher: &Path, args: &[&str]) -> Result<String, String> {
    let out = Command::new(launcher)
        .args(args)
        .env("HORIZON_LAUNCHER_NO_SELF_UPDATE", "1")
        .output()
        .map_err(|e| e.to_string())?;
    if !out.status.success() {
        return Err(format!(
            "horizon-launcher {}: {}",
            args.first().copied().unwrap_or(""),
            String::from_utf8_lossy(&out.stderr).trim()
        ));
    }
    Ok(String::from_utf8_lossy(&out.stdout).to_string())
}

fn instance_of(launcher: &Path, name: &str) -> Result<Option<(String, PathBuf)>, String> {
    let text = run(launcher, &["instance", "list", "--json"])?;
    let list: Value = serde_json::from_str(&text).map_err(|e| e.to_string())?;
    Ok(list.as_array().into_iter().flatten().find_map(|i| {
        (i["name"] == name && !i["java"].is_null()).then(|| {
            (
                i["id"].as_str().unwrap_or_default().to_string(),
                PathBuf::from(i["home"].as_str().unwrap_or_default()),
            )
        })
    }))
}

#[repr(C)]
struct MemoryStatus {
    length: u32,
    load: u32,
    total: u64,
    rest: [u64; 6],
}

extern "system" {
    fn GlobalMemoryStatusEx(status: *mut MemoryStatus) -> i32;
}

/// 8 GB, or 6 on a computer with less than 16 GB of memory.
fn memory_mb() -> &'static str {
    let mut status = MemoryStatus {
        length: std::mem::size_of::<MemoryStatus>() as u32,
        load: 0,
        total: 0,
        rest: [0; 6],
    };
    // SAFETY: the structure is the one the call documents, with its size set.
    let known = unsafe { GlobalMemoryStatusEx(&mut status) } != 0;
    if known && status.total >= 15 << 30 {
        "8192"
    } else {
        "6144"
    }
}

fn install_steps(agent: &ureq::Agent, adopt: Option<&str>, target: &Target) -> Result<(), String> {
    let fresh = find_launcher().is_none();
    let launcher = match find_launcher() {
        Some(path) => path,
        None => install_official(agent)?,
    };
    println!("Horizon: {}", launcher.display());
    if let Some(id) = adopt {
        // The player's own copy of the pack becomes the server's instance.
        run(&launcher, &["instance", "rename", id, &target.name])?;
    }
    if instance_of(&launcher, &target.name)?.is_none() {
        println!("Creating the instance");
        run(&launcher, &["instance", "create", &target.name, "--minecraft", "1.21.1"])?;
    }
    let (id, home) = instance_of(&launcher, &target.name)?.ok_or("the instance was not created")?;
    fs::create_dir_all(&home).map_err(|e| e.to_string())?;

    let own = std::env::current_exe().map_err(|e| e.to_string())?;
    let installed = home.join("f8q-updater.exe");
    if own != installed {
        fs::copy(&own, &installed).map_err(|e| format!("copying the updater: {e}"))?;
    }

    if target.starter && !home.join(STARTER_DONE).exists() {
        println!("Applying the settings");
        let mut bytes = Vec::new();
        agent
            .get(STARTER_URL)
            .call()
            .map_err(|e| format!("starter settings: {e}"))?
            .into_reader()
            .read_to_end(&mut bytes)
            .map_err(|e| e.to_string())?;
        unpack_starter(bytes, &home)?;
        fs::write(home.join(STARTER_DONE), "").map_err(|e| e.to_string())?;
        run(&launcher, &["multiplayer", "add", &target.address, "--name", if target.starter { "F8Q" } else { &target.name }, "--instance", &id])?;
    }

    let memory = memory_mb();
    run(
        &launcher,
        &["instance", "java", &id, "--auto", "--memory", memory, "--args", "-XX:+UseZGC -XX:+ZGenerational"],
    )?;
    let path = installed.to_string_lossy().replace('\\', "/");
    let wrapper = format!("\"{path}\" wrap {}", target.manifest);
    run(&launcher, &["instance", "launch", &id, "--wrapper", &wrapper])?;
    run(&launcher, &["instance", "select", &id])?;

    println!("Downloading mods");
    let text = agent
        .get(&target.manifest)
        .query("refresh", &std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap_or_default().as_nanos().to_string())
        .set("Cache-Control", "no-cache")
        .call()
        .map_err(|e| e.to_string())?
        .into_string()
        .map_err(|e| e.to_string())?;
    let manifest: Manifest = serde_json::from_str(&text).map_err(|e| e.to_string())?;
    let net = agent.clone();
    let fetch = move |e: &sync::Entry| -> Result<Box<dyn Read>, String> {
        let reply = net.get(&e.url).call().map_err(|err| format!("{}: {err}", e.path))?;
        Ok(Box::new(reply.into_reader()))
    };
    sync::sync(&home, &manifest, &fetch, &|line| println!("{line}"))?;
    println!("\nГотово. Откройте F8Q Launcher заново и нажмите «Играть».");
    println!("Первый запуск устанавливает NeoForge и занимает несколько минут.");
    if fresh {
        println!("\nВыберите ник или аккаунт Microsoft в настройках F8Q Launcher.");
    }
    Ok(())
}

/// Which pack to install: F8Q's, or a server's the player added.
pub struct Target {
    pub name: String,
    pub address: String,
    pub manifest: String,
    /// F8Q's starter settings; another server's pack brings none.
    pub starter: bool,
}

impl Target {
    pub fn f8q() -> Self {
        Self {
            name: NAME.to_string(),
            address: ADDRESS.to_string(),
            manifest: MANIFEST_URL.to_string(),
            starter: true,
        }
    }
}

pub fn install(agent: &ureq::Agent, quiet: bool, instance: Option<&str>, target: &Target) -> bool {
    let result = install_steps(agent, instance, target);
    if let Err(e) = &result {
        eprintln!("\nInstall failed: {e}");
    }
    if !quiet {
        println!("\nPress Enter to close.");
        let _ = io::stdin().read_line(&mut String::new());
    }
    result.is_ok()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn the_starter_writes_only_settings() {
        assert!(starter_path("options.txt").is_some());
        assert!(starter_path("config/a/b.toml").is_some());
        for bad in ["mods/a.jar", "../options.txt", "config/../x", "/etc/x", "config\\..\\x", "options.txt.bak"] {
            assert!(starter_path(bad).is_none(), "{bad}");
        }
    }

    #[test]
    fn the_last_server_is_not_shared() {
        let clean = clean_options("fov:0.7\nlastServer:play.example:25565\nlang:ru_ru\n");
        assert_eq!(clean, "fov:0.7\nlang:ru_ru\n");
    }
}
