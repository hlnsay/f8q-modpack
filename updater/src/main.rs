#![cfg_attr(not(test), windows_subsystem = "windows")]
//! No arguments: installs F8Q's instance into the player's Horizon (`install.rs`).
//! `f8q-updater sync <manifest url or file> [game dir]`: updates the folder.
//! `f8q-updater wrap <manifest url or file> <game command...>`: the same as Horizon's wrapper.
//! `f8q-updater publish <game dir> <out dir> <base url>`: writes the manifest for that folder.
//!
//! Sync stays out of the way when it can: an unreachable list means the game starts with what it
//! has, but a failed update stops it, since a half-updated instance can't join the server.
mod install;
mod neoforge;
mod sync;

use serde::Deserialize;
use std::collections::{BTreeMap, HashMap};
use std::fs;
use std::io::Read;
use std::path::{Path, PathBuf};
use std::process::ExitCode;
use std::time::Duration;
use sync::{Entry, Manifest};

const AGENT: &str = "f8q-updater/0.1";
/// F8Q's own releases: the only updater ever taken. Another server's manifest may bring mods, never
/// a program that runs outside the game.
const OWN_RELEASES: &str = "https://github.com/hlnsay/f8q-modpack/releases/download/";
/// Where the published settings are read from.
const CONFIG_BASE: &str = "https://raw.githubusercontent.com/hlnsay/f8q-modpack/main";

fn agent() -> ureq::Agent {
    ureq::AgentBuilder::new()
        .user_agent(AGENT)
        .timeout_connect(Duration::from_secs(15))
        .timeout_read(Duration::from_secs(60))
        .build()
}

fn load_manifest(source: &str) -> Result<Manifest, String> {
    let text = if source.starts_with("https://") {
        agent()
            .get(source)
            .query("refresh", &std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap_or_default().as_nanos().to_string())
            .set("Cache-Control", "no-cache")
            .call()
            .map_err(|e| e.to_string())?
            .into_string()
            .map_err(|e| e.to_string())?
    } else {
        fs::read_to_string(source).map_err(|e| e.to_string())?
    };
    serde_json::from_str(&text).map_err(|e| format!("bad manifest: {e}"))
}

fn run_sync(source: &str, home: &Path) -> ExitCode {
    let manifest = match load_manifest(source) {
        Ok(m) => m,
        Err(e) => {
            println!("[f8q] the mod list can't be read, starting as is: {e}");
            return ExitCode::SUCCESS;
        }
    };
    let net = agent();
    let fetch = move |e: &Entry| -> Result<Box<dyn Read>, String> {
        if !e.url.starts_with("https://") {
            return Err(format!("{}: not an https download", e.path));
        }
        let mut last = String::new();
        for _ in 0..3 {
            match net.get(&e.url).call() {
                Ok(r) => return Ok(Box::new(r.into_reader())),
                Err(err) => last = err.to_string(),
            }
        }
        Err(format!("{}: {last}", e.path))
    };
    let result = sync::sync(home, &manifest, &fetch, &|line| println!("[f8q] {line}"));
    if let (Ok(_), Some(updater)) = (&result, &manifest.updater) {
        if let Err(e) = update_self(updater, home) {
            println!("[f8q] the updater stays as it is: {e}");
        }
    }
    match result {
        Ok(r) => {
            println!(
                "[f8q] up to date: {} downloaded, {} removed, {} unchanged",
                r.downloaded, r.removed, r.unchanged
            );
            ExitCode::SUCCESS
        }
        Err(e) => {
            eprintln!("[f8q] update failed: {e}");
            ExitCode::FAILURE
        }
    }
}

extern "system" {
    fn AllocConsole() -> i32;
}

/// Replaces the copy in the game folder with the published updater, for the next start. A running
/// exe can be renamed, not overwritten. A copy anywhere else is left alone.
fn update_self(entry: &Entry, home: &Path) -> Result<(), String> {
    let own = std::env::current_exe().map_err(|e| e.to_string())?;
    let inside = own
        .parent()
        .and_then(|dir| fs::canonicalize(dir).ok())
        .is_some_and(|dir| fs::canonicalize(home).is_ok_and(|h| h == dir));
    if !inside || sync::sha512_of(&own).is_ok_and(|h| h.eq_ignore_ascii_case(&entry.sha512)) {
        return Ok(());
    }
    if !entry.url.starts_with(OWN_RELEASES) {
        return Err("not F8Q's own download".into());
    }
    let mut bytes = Vec::new();
    agent()
        .get(&entry.url)
        .call()
        .map_err(|e| e.to_string())?
        .into_reader()
        .read_to_end(&mut bytes)
        .map_err(|e| e.to_string())?;
    let fresh = own.with_extension("new");
    fs::write(&fresh, bytes).map_err(|e| e.to_string())?;
    if !sync::sha512_of(&fresh).is_ok_and(|h| h.eq_ignore_ascii_case(&entry.sha512)) {
        let _ = fs::remove_file(&fresh);
        return Err("wrong checksum".into());
    }
    let old = own.with_extension("old");
    let _ = fs::remove_file(&old);
    fs::rename(&own, &old).map_err(|e| e.to_string())?;
    fs::rename(&fresh, &own).map_err(|e| e.to_string())?;
    println!("[f8q] Updater updated for the next start");
    Ok(())
}

fn quiet_install(instance: Option<&str>) -> ExitCode {
    if install::install(&agent(), true, instance, &install::Target::f8q()) {
        ExitCode::SUCCESS
    } else {
        ExitCode::FAILURE
    }
}

fn game_dir(game: &[&str]) -> Option<PathBuf> {
    std::env::var_os("INST_MC_DIR").map(PathBuf::from).or_else(|| {
        let at = game.iter().position(|a| *a == "--gameDir")?;
        game.get(at + 1).map(PathBuf::from)
    })
}

/// Updates, then runs `game` and hands back how it ended.
fn run_wrapped(source: &str, game: &[&str]) -> ExitCode {
    let Some(home) = game_dir(game) else {
        eprintln!("[f8q] no game folder");
        return ExitCode::FAILURE;
    };
    if run_sync(source, &home) != ExitCode::SUCCESS {
        return ExitCode::FAILURE;
    }
    let root = neoforge::folder(&home);
    let prepared = neoforge::install(&agent(), game[0], &root)
        .and_then(|()| neoforge::fetch_libraries(&agent(), &root))
        .and_then(|()| neoforge::arguments(&root, &home, &game[1..]));
    let args = match prepared {
        Ok(args) => args,
        Err(e) => {
            eprintln!("[f8q] NeoForge can't start: {e}");
            return ExitCode::FAILURE;
        }
    };
    let started = std::process::Command::new(game[0])
        .args(&args)
        .current_dir(&home)
        .status();
    match started {
        Ok(status) if status.success() => ExitCode::SUCCESS,
        Ok(status) => ExitCode::from(status.code().unwrap_or(1).clamp(1, 255) as u8),
        Err(e) => {
            eprintln!("[f8q] the game can't start: {e}");
            ExitCode::FAILURE
        }
    }
}

#[derive(Deserialize)]
struct RemoteFile {
    url: String,
    hashes: HashMap<String, String>,
}

#[derive(Deserialize)]
struct RemoteVersion {
    files: Vec<RemoteFile>,
}

/// Modrinth's own download links for the hashes it knows: public mods are never rehosted.
fn modrinth_urls(hashes: &[String]) -> Result<HashMap<String, String>, String> {
    let body = serde_json::json!({ "hashes": hashes, "algorithm": "sha512" });
    let found: HashMap<String, RemoteVersion> = agent()
        .post("https://api.modrinth.com/v2/version_files")
        .send_json(body)
        .map_err(|e| e.to_string())?
        .into_json()
        .map_err(|e| e.to_string())?;
    let mut urls = HashMap::new();
    for (hash, version) in found {
        if let Some(f) = version
            .files
            .iter()
            .find(|f| f.hashes.get("sha512") == Some(&hash))
        {
            urls.insert(hash, f.url.clone());
        }
    }
    Ok(urls)
}

fn files_of(home: &Path) -> Result<BTreeMap<String, PathBuf>, String> {
    let mut out = BTreeMap::new();
    for (dir, ext) in [("mods", "jar"), ("resourcepacks", "zip")] {
        for item in fs::read_dir(home.join(dir)).map_err(|e| format!("{dir}: {e}"))? {
            let path = item.map_err(|e| e.to_string())?.path();
            let right = path.extension().is_some_and(|x| x.eq_ignore_ascii_case(ext));
            if path.is_file() && right {
                let name = path.file_name().unwrap().to_string_lossy().to_string();
                out.insert(format!("{dir}/{name}"), path);
            }
        }
    }
    Ok(out)
}

/// Spaces and pluses in names survive as a link.
fn url_escape(name: &str) -> String {
    name.bytes()
        .map(|b| match b {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' => {
                (b as char).to_string()
            }
            _ => format!("%{b:02X}"),
        })
        .collect()
}

fn publish(home: &Path, out: &Path, base: &str) -> Result<(), String> {
    let files = files_of(home)?;
    let mut hashed = Vec::new();
    for (path, full) in &files {
        let hash = sync::sha512_of(full).map_err(|e| format!("{path}: {e}"))?;
        let size = fs::metadata(full).map_err(|e| e.to_string())?.len();
        hashed.push((path.clone(), full.clone(), hash, size));
    }
    let hashes: Vec<String> = hashed.iter().map(|h| h.2.clone()).collect();
    let mut urls = modrinth_urls(&hashes)?;
    // Files Modrinth doesn't have and whose license forbids hosting a copy: their authors' own link.
    let sources: HashMap<String, String> = fs::read(home.join(".f8q-sources.json"))
        .ok()
        .and_then(|bytes| serde_json::from_slice(&bytes).ok())
        .unwrap_or_default();
    for (path, _, sha512, _) in &hashed {
        if let Some(url) = sources.get(path.rsplit('/').next().unwrap_or(path)) {
            urls.entry(sha512.clone()).or_insert_with(|| url.clone());
        }
    }
    let upload = out.join("upload");
    fs::create_dir_all(&upload).map_err(|e| e.to_string())?;
    let options = fs::read_to_string(home.join("options.txt")).unwrap_or_default();
    let mut manifest = Manifest {
        version: 1,
        packs: sync::option_list(&options, "resourcePacks"),
        incompatible_packs: sync::option_list(&options, "incompatibleResourcePacks"),
        ..Manifest::default()
    };
    let mut own = 0;
    for (path, full, sha512, size) in hashed {
        let url = match urls.get(&sha512) {
            Some(u) => u.clone(),
            None => {
                // GitHub turns spaces in a release file's name into dots.
                let name = path.rsplit('/').next().unwrap().replace(' ', ".");
                fs::copy(&full, upload.join(&name)).map_err(|e| format!("{name}: {e}"))?;
                println!("own file: {path}");
                own += 1;
                format!("{}/{}", base.trim_end_matches('/'), url_escape(&name))
            }
        };
        manifest.files.push(Entry {
            path,
            sha512,
            size,
            url,
        });
    }
    let exe = std::env::current_exe().map_err(|e| e.to_string())?;
    manifest.updater = Some(Entry {
        path: "f8q-updater.exe".into(),
        sha512: sync::sha512_of(&exe).map_err(|e| e.to_string())?,
        size: fs::metadata(&exe).map_err(|e| e.to_string())?.len(),
        url: format!("{}/f8q-updater.exe", base.trim_end_matches('/')),
    });
    for (path, full) in install::published_config(home)? {
        let copy = out.join(&path);
        if let Some(dir) = copy.parent() {
            fs::create_dir_all(dir).map_err(|e| e.to_string())?;
        }
        fs::copy(&full, &copy).map_err(|e| format!("{path}: {e}"))?;
        let parts: Vec<String> = path.split('/').map(url_escape).collect();
        manifest.config.push(Entry {
            url: format!("{CONFIG_BASE}/{}?sha512={}", parts.join("/"), sync::sha512_of(&full).map_err(|e| e.to_string())?),
            sha512: sync::sha512_of(&full).map_err(|e| e.to_string())?,
            size: fs::metadata(&full).map_err(|e| e.to_string())?.len(),
            path,
        });
    }
    let text = serde_json::to_string_pretty(&manifest).map_err(|e| e.to_string())?;
    fs::write(out.join("manifest.json"), text).map_err(|e| e.to_string())?;
    println!(
        "{} files: {} from Modrinth, {own} to upload from {}",
        manifest.files.len(),
        manifest.files.len() - own,
        upload.display()
    );
    Ok(())
}

fn main() -> ExitCode {
    let args: Vec<String> = std::env::args().skip(1).collect();
    let args: Vec<&str> = args.iter().map(String::as_str).collect();
    match args.as_slice() {
        [] => {
            // SAFETY: takes no arguments; a double click gets a window for the steps.
            unsafe { AllocConsole() };
            if install::install(&agent(), false, None, &install::Target::f8q()) {
                ExitCode::SUCCESS
            } else {
                ExitCode::FAILURE
            }
        }
        ["install", "--quiet"] => quiet_install(None),
        ["install", "--quiet", "--instance", id] => quiet_install(Some(id)),
        ["install", "--quiet", "--server", name, address, manifest] => {
            let target = install::Target {
                name: name.to_string(),
                address: address.to_string(),
                manifest: manifest.to_string(),
                starter: false,
            };
            if install::install(&agent(), true, None, &target) {
                ExitCode::SUCCESS
            } else {
                ExitCode::FAILURE
            }
        }
        ["starter", home, out] => match install::make_starter(Path::new(home), Path::new(out)) {
            Ok(()) => ExitCode::SUCCESS,
            Err(e) => {
                eprintln!("starter failed: {e}");
                ExitCode::FAILURE
            }
        },
        ["sync", source] => match std::env::var_os("INST_MC_DIR") {
            Some(home) => run_sync(source, Path::new(&home)),
            None => {
                eprintln!("no game folder: give it, or run from Horizon's pre-launch");
                ExitCode::FAILURE
            }
        },
        ["sync", source, home] => run_sync(source, Path::new(home)),
        // Horizon's wrapper: pre-launch sends the whole game line through cmd.exe, which is too
        // short for NeoForge's. A wrapper gets its arguments directly.
        ["wrap", source, game @ ..] if !game.is_empty() => run_wrapped(source, game),
        ["publish", home, out, base] => match publish(Path::new(home), Path::new(out), base) {
            Ok(()) => ExitCode::SUCCESS,
            Err(e) => {
                eprintln!("publish failed: {e}");
                ExitCode::FAILURE
            }
        },
        _ => {
            eprintln!("usage: f8q-updater sync <manifest> [game dir]");
            eprintln!("       f8q-updater publish <game dir> <out dir> <base url>");
            ExitCode::FAILURE
        }
    }
}
