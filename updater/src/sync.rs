//! Brings a game folder in step with a manifest: the player's own extra files stay, only what the
//! updater installed earlier or the manifest names in `remove` is ever deleted.
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha512};
use std::collections::{BTreeMap, BTreeSet};
use std::fs;
use std::io::{self, Read, Write};
use std::path::{Component, Path, PathBuf};

const STATE_FILE: &str = ".f8q-updater-state.json";
/// The only folders a manifest may touch.
const ALLOWED: [&str; 3] = ["mods", "resourcepacks", "config"];
/// What each published setting was when it was last applied.
const CONFIG_STATE: &str = ".f8q-config-state.json";
/// The list of resource packs last applied.
const PACKS_STATE: &str = ".f8q-packs-state.json";

#[derive(Debug, Deserialize, Serialize, Clone, PartialEq)]
pub struct Entry {
    pub path: String,
    pub sha512: String,
    pub size: u64,
    pub url: String,
}

#[derive(Debug, Deserialize, Serialize, Default)]
pub struct Manifest {
    pub version: u32,
    pub files: Vec<Entry>,
    /// Stale files to delete even though this updater did not install them.
    #[serde(default)]
    pub remove: Vec<String>,
    /// Settings: applied when the published file changes, so a player's own edits stay until then.
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub config: Vec<Entry>,
    /// The published updater, which replaces the copy in the game folder.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub updater: Option<Entry>,
    /// The resource packs in use, lowest first, as options.txt names them.
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub packs: Vec<String>,
    /// Those the game must load although made for another version.
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub incompatible_packs: Vec<String>,
}

#[derive(Debug, Default, PartialEq)]
pub struct Report {
    pub downloaded: usize,
    pub removed: usize,
    pub unchanged: usize,
}

/// A path inside `mods/` or `resourcepacks/`, or an error. The manifest comes from the network,
/// so it never gets to write anywhere else.
pub fn checked_path(path: &str) -> Result<PathBuf, String> {
    let p = Path::new(path);
    let mut parts = p.components();
    let first = match parts.next() {
        Some(Component::Normal(c)) => c.to_string_lossy().to_string(),
        _ => return Err(format!("bad path: {path}")),
    };
    if !ALLOWED.contains(&first.as_str()) || path.contains('\\') {
        return Err(format!("path outside mods and resourcepacks: {path}"));
    }
    let rest: Vec<_> = parts.collect();
    if rest.is_empty() || rest.iter().any(|c| !matches!(c, Component::Normal(_))) {
        return Err(format!("bad path: {path}"));
    }
    Ok(p.to_path_buf())
}

pub fn sha512_of(path: &Path) -> io::Result<String> {
    let mut file = fs::File::open(path)?;
    let mut hash = Sha512::new();
    let mut buf = vec![0u8; 1 << 20];
    loop {
        let n = file.read(&mut buf)?;
        if n == 0 {
            break;
        }
        hash.update(&buf[..n]);
    }
    Ok(hex(&hash.finalize()))
}

fn hex(bytes: &[u8]) -> String {
    bytes.iter().map(|b| format!("{b:02x}")).collect()
}

fn read_state(home: &Path) -> BTreeSet<String> {
    fs::read(home.join(STATE_FILE))
        .ok()
        .and_then(|b| serde_json::from_slice(&b).ok())
        .unwrap_or_default()
}

fn write_state(home: &Path, state: &BTreeSet<String>) -> Result<(), String> {
    let text = serde_json::to_vec_pretty(state).map_err(|e| e.to_string())?;
    fs::write(home.join(STATE_FILE), text).map_err(|e| format!("state file: {e}"))
}

/// Saves `reader` next to `dest` and moves it into place only when its hash matches, so a broken
/// download never replaces a working mod.
fn install(dest: &Path, entry: &Entry, reader: &mut dyn Read) -> Result<(), String> {
    let part = dest.with_extension("f8q-part");
    if let Some(dir) = dest.parent() {
        fs::create_dir_all(dir).map_err(|e| e.to_string())?;
    }
    let result = (|| {
        let mut out = fs::File::create(&part).map_err(|e| e.to_string())?;
        let mut hash = Sha512::new();
        let mut buf = vec![0u8; 1 << 20];
        loop {
            let n = reader.read(&mut buf).map_err(|e| e.to_string())?;
            if n == 0 {
                break;
            }
            hash.update(&buf[..n]);
            out.write_all(&buf[..n]).map_err(|e| e.to_string())?;
        }
        out.flush().map_err(|e| e.to_string())?;
        drop(out);
        let got = hex(&hash.finalize());
        if !got.eq_ignore_ascii_case(&entry.sha512) {
            return Err(format!("wrong checksum for {}", entry.path));
        }
        fs::rename(&part, dest).map_err(|e| e.to_string())
    })();
    if result.is_err() {
        let _ = fs::remove_file(&part);
    }
    result
}

/// Applies settings the admin changed since the last time, and deletes those no longer published.
fn sync_config(
    home: &Path,
    manifest: &Manifest,
    fetch: &dyn Fn(&Entry) -> Result<Box<dyn Read>, String>,
    log: &dyn Fn(String),
    report: &mut Report,
) -> Result<(), String> {
    let mut applied: BTreeMap<String, String> = fs::read(home.join(CONFIG_STATE))
        .ok()
        .and_then(|b| serde_json::from_slice(&b).ok())
        .unwrap_or_default();
    for entry in &manifest.config {
        if applied.get(&entry.path) == Some(&entry.sha512) {
            continue;
        }
        let dest = home.join(&entry.path);
        let same = dest.is_file() && sha512_of(&dest).is_ok_and(|h| h.eq_ignore_ascii_case(&entry.sha512));
        if !same {
            log(format!("Updating {}", entry.path));
            let mut reader = fetch(entry)?;
            install(&dest, entry, &mut *reader)?;
            report.downloaded += 1;
        }
        applied.insert(entry.path.clone(), entry.sha512.clone());
    }
    let wanted: BTreeSet<&String> = manifest.config.iter().map(|e| &e.path).collect();
    for path in applied.keys().filter(|p| !wanted.contains(p)).cloned().collect::<Vec<_>>() {
        if checked_path(&path).is_ok() && home.join(&path).is_file() {
            log(format!("Removing {path}"));
            fs::remove_file(home.join(&path)).map_err(|e| format!("{path}: {e}"))?;
            report.removed += 1;
        }
        applied.remove(&path);
    }
    let text = serde_json::to_vec_pretty(&applied).map_err(|e| e.to_string())?;
    fs::write(home.join(CONFIG_STATE), text).map_err(|e| format!("config state: {e}"))
}

/// The value of a list in options.txt.
pub fn option_list(options: &str, key: &str) -> Vec<String> {
    options
        .lines()
        .find_map(|line| line.strip_prefix(key)?.strip_prefix(':'))
        .and_then(|value| serde_json::from_str(value).ok())
        .unwrap_or_default()
}

fn with_option_list(options: &str, key: &str, list: &[String]) -> String {
    let value = format!("{key}:{}", serde_json::to_string(list).unwrap_or_default());
    let mut found = false;
    let mut lines: Vec<String> = options
        .lines()
        .map(|line| {
            if line.starts_with(key) && line[key.len()..].starts_with(':') {
                found = true;
                value.clone()
            } else {
                line.to_string()
            }
        })
        .collect();
    if !found {
        lines.push(value);
    }
    lines.join("
") + "
"
}

/// The published packs in `options`, in their order. Packs of the player's own stay, on top.
fn apply_packs(options: &str, manifest: &Manifest) -> String {
    let mut packs = manifest.packs.clone();
    let own: Vec<String> = option_list(options, "resourcePacks")
        .into_iter()
        .filter(|p| p.starts_with("file/") && !packs.contains(p))
        .collect();
    let at = packs.iter().position(|p| !p.starts_with("file/") && p != "vanilla" && !p.starts_with("mod/"));
    let at = at.unwrap_or(packs.len());
    packs.splice(at..at, own);
    let mut allowed = option_list(options, "incompatibleResourcePacks");
    for pack in &manifest.incompatible_packs {
        if !allowed.contains(pack) {
            allowed.push(pack.clone());
        }
    }
    with_option_list(&with_option_list(options, "resourcePacks", &packs), "incompatibleResourcePacks", &allowed)
}

/// Turns the published packs on when the published list changes, so a player's own order stays
/// until then, as settings do.
fn sync_packs(home: &Path, manifest: &Manifest, log: &dyn Fn(String)) -> Result<(), String> {
    if manifest.packs.is_empty() {
        return Ok(());
    }
    let wanted = serde_json::to_string(&manifest.packs).map_err(|e| e.to_string())?;
    if fs::read_to_string(home.join(PACKS_STATE)).is_ok_and(|done| done == wanted) {
        return Ok(());
    }
    let path = home.join("options.txt");
    // Without options.txt the starter settings bring the list.
    if let Ok(options) = fs::read_to_string(&path) {
        log("Turning on the resource packs".to_string());
        fs::write(&path, apply_packs(&options, manifest)).map_err(|e| format!("options.txt: {e}"))?;
    }
    fs::write(home.join(PACKS_STATE), wanted).map_err(|e| format!("packs state: {e}"))
}

/// Validates everything first: a bad manifest changes nothing in the game folder.
pub fn sync(
    home: &Path,
    manifest: &Manifest,
    fetch: &dyn Fn(&Entry) -> Result<Box<dyn Read>, String>,
    log: &dyn Fn(String),
) -> Result<Report, String> {
    if manifest.version != 1 {
        return Err(format!("unknown manifest version {}", manifest.version));
    }
    for e in &manifest.files {
        checked_path(&e.path)?;
    }
    for r in &manifest.remove {
        checked_path(r)?;
    }
    for e in &manifest.config {
        checked_path(&e.path)?;
    }
    let previous = read_state(home);
    let wanted: BTreeSet<String> = manifest.files.iter().map(|e| e.path.clone()).collect();
    let mut report = Report::default();
    let mut state = previous.clone();
    let total = manifest.files.len();
    for (index, entry) in manifest.files.iter().enumerate() {
        let dest = home.join(&entry.path);
        if dest.is_file() && sha512_of(&dest).is_ok_and(|h| h.eq_ignore_ascii_case(&entry.sha512)) {
            report.unchanged += 1;
            state.insert(entry.path.clone());
            continue;
        }
        log(format!("Downloading {} ({}/{total})", entry.path, index + 1));
        let mut reader = fetch(entry)?;
        install(&dest, entry, &mut *reader)?;
        report.downloaded += 1;
        state.insert(entry.path.clone());
        // Saved as it goes: an interrupted update still knows what it installed.
        write_state(home, &state)?;
    }
    sync_config(home, manifest, fetch, log, &mut report)?;
    sync_packs(home, manifest, log)?;
    let stale = previous
        .difference(&wanted)
        .cloned()
        .chain(manifest.remove.iter().filter(|r| !wanted.contains(*r)).cloned())
        .collect::<BTreeSet<_>>();
    for path in stale {
        // An old state file is not trusted more than the manifest.
        if checked_path(&path).is_err() {
            continue;
        }
        let full = home.join(&path);
        if full.is_file() {
            log(format!("Removing {path}"));
            fs::remove_file(&full).map_err(|e| format!("{path}: {e}"))?;
            report.removed += 1;
        }
        state.remove(&path);
    }
    write_state(home, &state)?;
    Ok(report)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Cursor;

    fn temp(name: &str) -> PathBuf {
        let d = std::env::temp_dir().join(format!("f8q-test-{name}-{}", std::process::id()));
        let _ = fs::remove_dir_all(&d);
        fs::create_dir_all(d.join("mods")).unwrap();
        d
    }

    fn sha(bytes: &[u8]) -> String {
        hex(&Sha512::digest(bytes))
    }

    fn entry(path: &str, body: &[u8]) -> Entry {
        Entry {
            path: path.into(),
            sha512: sha(body),
            size: body.len() as u64,
            url: "https://example.test/x".into(),
        }
    }

    fn serve(body: &'static [u8]) -> impl Fn(&Entry) -> Result<Box<dyn Read>, String> {
        move |_| Ok(Box::new(Cursor::new(body)) as Box<dyn Read>)
    }

    fn no_network(_: &Entry) -> Result<Box<dyn Read>, String> {
        panic!("nothing should be downloaded")
    }

    #[test]
    fn paths_stay_inside_the_allowed_folders() {
        assert!(checked_path("mods/a.jar").is_ok());
        assert!(checked_path("resourcepacks/a.zip").is_ok());
        for bad in [
            "../a.jar",
            "mods/../../a.jar",
            "saves/a.dat",
            "/etc/a",
            "C:/a.jar",
            "mods",
            "mods\\..\\a.jar",
        ] {
            assert!(checked_path(bad).is_err(), "{bad}");
        }
    }

    #[test]
    fn missing_files_are_downloaded_then_left_alone() {
        let home = temp("install");
        let m = Manifest {
            version: 1,
            files: vec![entry("mods/a.jar", b"new")],
            remove: vec![],
            config: vec![],
            updater: None,
            ..Manifest::default()
        };
        let r = sync(&home, &m, &serve(b"new"), &|_| {}).unwrap();
        assert_eq!(r.downloaded, 1);
        assert_eq!(fs::read(home.join("mods/a.jar")).unwrap(), b"new");
        let r = sync(&home, &m, &no_network, &|_| {}).unwrap();
        assert_eq!(r.unchanged, 1);
    }

    #[test]
    fn a_corrupt_download_keeps_the_old_file() {
        let home = temp("corrupt");
        fs::write(home.join("mods/a.jar"), b"old").unwrap();
        let m = Manifest {
            version: 1,
            files: vec![entry("mods/a.jar", b"new")],
            remove: vec![],
            config: vec![],
            updater: None,
            ..Manifest::default()
        };
        assert!(sync(&home, &m, &serve(b"broken"), &|_| {}).is_err());
        assert_eq!(fs::read(home.join("mods/a.jar")).unwrap(), b"old");
        assert!(!home.join("mods/a.f8q-part").exists());
    }

    #[test]
    fn only_files_it_installed_are_removed() {
        let home = temp("remove");
        fs::write(home.join("mods/mine.jar"), b"player").unwrap();
        let first = Manifest {
            version: 1,
            files: vec![entry("mods/a.jar", b"a")],
            remove: vec![],
            config: vec![],
            updater: None,
            ..Manifest::default()
        };
        sync(&home, &first, &serve(b"a"), &|_| {}).unwrap();
        let second = Manifest {
            version: 1,
            files: vec![],
            remove: vec![],
            config: vec![],
            updater: None,
            ..Manifest::default()
        };
        let r = sync(&home, &second, &no_network, &|_| {}).unwrap();
        assert_eq!(r.removed, 1);
        assert!(!home.join("mods/a.jar").exists());
        assert!(home.join("mods/mine.jar").exists());
    }

    #[test]
    fn the_remove_list_deletes_stale_files() {
        let home = temp("list");
        fs::write(home.join("mods/old.jar"), b"old").unwrap();
        let m = Manifest {
            version: 1,
            files: vec![],
            remove: vec!["mods/old.jar".into()],
            config: vec![],
            updater: None,
            ..Manifest::default()
        };
        assert_eq!(sync(&home, &m, &no_network, &|_| {}).unwrap().removed, 1);
        assert!(!home.join("mods/old.jar").exists());
    }

    #[test]
    fn settings_change_only_when_published_ones_do() {
        let home = temp("config");
        let first = Manifest {
            version: 1,
            config: vec![entry("config/a.toml", b"one")],
            ..Manifest::default()
        };
        sync(&home, &first, &serve(b"one"), &|_| {}).unwrap();
        fs::write(home.join("config/a.toml"), b"player edit").unwrap();
        sync(&home, &first, &no_network, &|_| {}).unwrap();
        assert_eq!(fs::read(home.join("config/a.toml")).unwrap(), b"player edit");
        let second = Manifest {
            version: 1,
            config: vec![entry("config/a.toml", b"two")],
            ..Manifest::default()
        };
        sync(&home, &second, &serve(b"two"), &|_| {}).unwrap();
        assert_eq!(fs::read(home.join("config/a.toml")).unwrap(), b"two");
        let third = Manifest {
            version: 1,
            ..Manifest::default()
        };
        sync(&home, &third, &no_network, &|_| {}).unwrap();
        assert!(!home.join("config/a.toml").exists());
    }

    #[test]
    fn published_packs_are_turned_on_and_a_players_own_stay() {
        let home = temp("packs");
        fs::write(
            home.join("options.txt"),
            "fov:0.7
resourcePacks:[\"vanilla\",\"file/old.zip\",\"file/mine.zip\",\"mod_resources\"]
incompatibleResourcePacks:[]
lang:ru_ru
",
        )
        .unwrap();
        let m = Manifest {
            version: 1,
            packs: vec!["vanilla".into(), "file/new.zip".into(), "file/old.zip".into(), "mod_resources".into()],
            incompatible_packs: vec!["file/new.zip".into()],
            ..Manifest::default()
        };
        sync(&home, &m, &no_network, &|_| {}).unwrap();
        let text = fs::read_to_string(home.join("options.txt")).unwrap();
        assert_eq!(
            option_list(&text, "resourcePacks"),
            ["vanilla", "file/new.zip", "file/old.zip", "file/mine.zip", "mod_resources"]
        );
        assert_eq!(option_list(&text, "incompatibleResourcePacks"), ["file/new.zip"]);
        assert!(text.starts_with("fov:0.7
") && text.ends_with("lang:ru_ru
"));

        // The player's later order stays until the published list changes.
        fs::write(home.join("options.txt"), "resourcePacks:[\"vanilla\"]
").unwrap();
        sync(&home, &m, &no_network, &|_| {}).unwrap();
        assert_eq!(fs::read_to_string(home.join("options.txt")).unwrap(), "resourcePacks:[\"vanilla\"]
");
    }

    #[test]
    fn a_bad_manifest_changes_nothing() {
        let home = temp("bad");
        fs::write(home.join("mods/keep.jar"), b"keep").unwrap();
        let m = Manifest {
            version: 1,
            files: vec![entry("../evil.jar", b"x")],
            remove: vec!["mods/keep.jar".into()],
            config: vec![],
            updater: None,
            ..Manifest::default()
        };
        assert!(sync(&home, &m, &no_network, &|_| {}).is_err());
        assert!(home.join("mods/keep.jar").exists());
    }
}
