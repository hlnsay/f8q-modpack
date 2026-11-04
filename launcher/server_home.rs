//! F8Q's own section: the sidebar's Server row and its page, with the server's status and a Play
//! that starts the instance straight into it.
use super::*;
use crate::shell::{c, label};
use std::path::PathBuf;
use std::sync::Arc;
use theme::night;

/// What the row and the page call the server.
pub(super) const NAME: &str = "F8Q 1.21.1 [AUTUMN]";
/// Where it answers: exaroton's default port only serves a notice for modded clients.
const ADDRESS: &str = "f8quit.exaroton.me:30096";
/// The instance that plays on it.
const INSTANCE: &str = "cozy-autumn-neoforge";
/// The published list of the server's mods.
const MANIFEST: &str = "https://raw.githubusercontent.com/hlnsay/f8q-modpack/main/manifest.json";
/// What changed in each published update.
const CHANGELOG: &str = "https://raw.githubusercontent.com/hlnsay/f8q-modpack/main/changelog.json";
/// What makes the instance on a computer that doesn't have it.
const INSTALLER: &str =
    "https://github.com/hlnsay/f8q-modpack/releases/download/files/f8q-updater.exe";
/// This build's number: `launcher.json` on GitHub names the newest.
pub(super) const BUILD: u32 = 13;
/// Horizon's releases: its launcher's are tagged `launcher@`, among the game's.
const ENGINE_RELEASE: &str =
    "https://api.github.com/repos/horizon-rust/horizon/releases?per_page=40";
/// The newest build's number, address and checksum.
const LAUNCHER_INFO: &str =
    "https://raw.githubusercontent.com/hlnsay/f8q-modpack/main/launcher.json";
/// How long a status stays fresh before the sidebar asks again.
const FRESH: Duration = Duration::from_secs(60);

#[derive(Clone, Copy, Default, PartialEq)]
enum Status {
    #[default]
    Asking,
    Online {
        players: u64,
        max: u64,
    },
    Offline,
}

/// One published update of the pack.
#[derive(Clone, Default)]
struct Change {
    date: String,
    added: Vec<String>,
    updated: Vec<String>,
    removed: Vec<String>,
}

/// `create-1.21.1-6.0.10.jar`: `Create`. The file name is all the list knows of a mod.
fn mod_title(path: &str) -> String {
    let file = path
        .rsplit('/')
        .next()
        .unwrap_or(path)
        .trim_end_matches(".jar");
    let cut = file
        .char_indices()
        .find(|(i, c)| {
            (*c == '-' || *c == '_') && {
                let rest = file[i + 1..].to_ascii_lowercase();
                rest.starts_with(|n: char| n.is_ascii_digit())
                    || rest.starts_with("neoforge")
                    || rest.starts_with("mc1")
                    || rest.starts_with('v')
            }
        })
        .map_or(file.len(), |(i, _)| i);
    let name = file[..cut].replace(['-', '_'], " ");
    let mut letters = name.chars();
    match letters.next() {
        Some(first) => first.to_uppercase().chain(letters).collect(),
        None => name,
    }
}

/// The changes published so far, newest first; nothing when they can't be read.
fn public_json(url: &str) -> Option<serde_json::Value> {
    let nonce = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .ok()?
        .as_nanos();
    ureq::AgentBuilder::new()
        .timeout(Duration::from_secs(15))
        .build()
        .get(&format!("{url}?refresh={nonce}"))
        .set("Cache-Control", "no-cache")
        .call()
        .ok()?
        .into_json()
        .ok()
}

fn read_changelog(manifest: &serde_json::Value) -> Vec<Change> {
    // New publications carry both panels in the same snapshot.
    let list = manifest
        .get("changelog")
        .filter(|v| v.is_array())
        .cloned()
        .or_else(|| public_json(CHANGELOG));
    parse_changelog(list.as_ref())
}

fn parse_changelog(list: Option<&serde_json::Value>) -> Vec<Change> {
    let strings = |v: &serde_json::Value| -> Vec<String> {
        v.as_array()
            .into_iter()
            .flatten()
            .filter_map(|s| s.as_str().map(str::to_string))
            .collect()
    };
    list.and_then(|l| l.as_array())
        .into_iter()
        .flatten()
        .map(|c| Change {
            date: c["date"].as_str().unwrap_or_default().to_string(),
            added: strings(&c["added"]),
            updated: strings(&c["updated"]),
            removed: strings(&c["removed"]),
        })
        .collect()
}

/// The server's icon, from its status answer: a PNG in base64.
fn read_server_icon() -> Option<Vec<u8>> {
    use base64::Engine;
    use horizon_telemetry::server_status::{read_answer, request, split_address};
    use std::io::Write;
    use std::net::ToSocketAddrs;
    let (host, port) = split_address(ADDRESS)?;
    let at = (host.as_str(), port).to_socket_addrs().ok()?.next()?;
    let timeout = Duration::from_secs(4);
    let mut stream = std::net::TcpStream::connect_timeout(&at, timeout).ok()?;
    stream.set_read_timeout(Some(timeout)).ok()?;
    stream.write_all(&request(&host, port)).ok()?;
    let answer: serde_json::Value = serde_json::from_str(&read_answer(&mut stream).ok()?).ok()?;
    let data = answer["favicon"]
        .as_str()?
        .split(',')
        .next_back()?
        .trim()
        .to_string();
    base64::engine::general_purpose::STANDARD.decode(data).ok()
}

/// Where the icon is kept for when the server is off.
fn icon_file() -> PathBuf {
    backend::data_dir().join("f8q-server-icon.png")
}

fn server_image(png: Vec<u8>) -> Arc<gpui::Image> {
    Arc::new(gpui::Image::from_bytes(gpui::ImageFormat::Png, png))
}

/// What the published list says about this computer.
#[derive(Clone, Default)]
struct Check {
    /// The list answered.
    known: bool,
    /// Its files, and how many of them the server's instance lacks.
    total: usize,
    missing: usize,
    /// Published settings not applied here yet.
    settings: usize,
    /// No instance by the server's name, but this one holds some of the mods.
    adopt: Option<String>,
    /// The official launcher answered and has no account.
    no_account: bool,
    /// The mods in the list, by name.
    mods: Vec<String>,
    changelog: Vec<Change>,
}

/// An update under way, shared with its thread.
#[derive(Default)]
pub(super) struct Run {
    pub fraction: f32,
    pub line: String,
    pub done: Option<Result<(), String>>,
}

/// What the page's button does.
#[derive(Clone, Copy, PartialEq)]
enum Need {
    Download,
    Update,
    SignIn,
    Play,
}

#[derive(Default)]
pub(super) struct ServerHome {
    status: Status,
    asked: Option<Instant>,
    /// What Play last did, under the button.
    note: Option<String>,
    check: Check,
    run: Option<Arc<Mutex<Run>>>,
    /// The top bar asked for a sign-in.
    sign_in: bool,
    /// The pack's list of mods is unfolded.
    mods_open: bool,
    /// The changelog is unfolded independently of the mod list.
    changes_open: bool,
    /// Ignore checks started before a newer refresh.
    check_generation: u64,
    /// The server's icon, and whether it was asked for.
    icon: Option<Arc<gpui::Image>>,
    icon_asked: bool,
    /// The answer about a newer launcher came.
    update_answered: bool,
    /// The launcher itself is being replaced, shown over the opening.
    self_update: Option<Arc<Mutex<Run>>>,
    /// The launcher's own update has been looked for.
    update_checked: bool,
    /// A newer launcher came out while this one is open.
    update_waiting: bool,
}

/// The manifest's files `home` holds at the right size.
fn present_files(manifest: &serde_json::Value, home: &std::path::Path) -> usize {
    manifest["files"]
        .as_array()
        .into_iter()
        .flatten()
        .filter(|file| {
            let (Some(path), Some(size)) = (file["path"].as_str(), file["size"].as_u64()) else {
                return false;
            };
            std::fs::metadata(home.join(path)).is_ok_and(|m| m.len() == size)
        })
        .count()
}

/// The published settings not applied here yet.
fn config_to_update(manifest: &serde_json::Value, home: &std::path::Path) -> usize {
    let applied: serde_json::Value = std::fs::read(home.join(".f8q-config-state.json"))
        .ok()
        .and_then(|b| serde_json::from_slice(&b).ok())
        .unwrap_or_default();
    let list = manifest["config"].as_array();
    let changed = list
        .into_iter()
        .flatten()
        .filter(|e| {
            e["path"]
                .as_str()
                .is_some_and(|p| applied[p] != e["sha512"])
        })
        .count();
    let gone = applied.as_object().map_or(0, |state| {
        state
            .keys()
            .filter(|p| !list.is_some_and(|l| l.iter().any(|e| e["path"] == p.as_str())))
            .count()
    });
    changed + gone
}

/// Compares the published list with the server's instance, or looks for a copy of the pack among
/// the player's other Java instances. No answer means nothing to report: playing goes on.
fn check_files(own: Option<PathBuf>, others: Vec<(String, PathBuf)>) -> Check {
    let _ = update_engine();
    let manifest = public_json(MANIFEST);
    let no_account = official_launcher().and_then(|p| has_account(&p)) == Some(false);
    let Some(manifest) = manifest else {
        return Check {
            no_account,
            ..Check::default()
        };
    };
    let total = manifest["files"].as_array().map_or(0, Vec::len);
    let mut mods: Vec<String> = manifest["files"]
        .as_array()
        .into_iter()
        .flatten()
        .filter_map(|f| f["path"].as_str())
        .filter(|p| p.starts_with("mods/"))
        .map(mod_title)
        .collect();
    mods.sort_by_key(|m| m.to_lowercase());
    let mut check = Check {
        known: true,
        total,
        mods,
        changelog: read_changelog(&manifest),
        no_account,
        ..Check::default()
    };
    match own {
        Some(home) => {
            let present = present_files(&manifest, &home);
            check.missing = total.saturating_sub(present);
            check.settings = config_to_update(&manifest, &home);
        }
        None => {
            check.missing = total;
            check.adopt = others
                .into_iter()
                .map(|(id, home)| (present_files(&manifest, &home), id))
                .filter(|(present, _)| *present > 0)
                .max()
                .map(|(_, id)| id);
        }
    }
    check
}

/// `Downloading mods/a.jar (3/113)`: the share done.
fn progress_of(line: &str) -> Option<f32> {
    let (done, total) = line
        .strip_suffix(')')?
        .rsplit_once('(')?
        .1
        .split_once('/')?;
    let (done, total): (f32, f32) = (done.parse().ok()?, total.parse().ok()?);
    (total > 0.).then(|| done / total)
}

/// Runs the updater with no window, passing its lines to `run`.
pub(super) fn run_updater(
    run: &Mutex<Run>,
    exe: Option<PathBuf>,
    args: Vec<String>,
) -> Result<(), String> {
    use std::io::BufRead;
    use std::process::{Command, Stdio};
    run.lock().unwrap().line = tr("Getting ready…").to_string();
    // With a copy already here, a failed check is no reason to stop.
    if let Err(e) = update_engine() {
        if official_launcher().is_none() {
            return Err(e);
        }
    }
    let exe = match exe {
        Some(exe) => exe,
        None => {
            let path = std::env::temp_dir().join("f8q-updater.exe");
            let reply = ureq::get(INSTALLER).call().map_err(|e| e.to_string())?;
            let mut body = Vec::new();
            std::io::Read::read_to_end(&mut reply.into_reader(), &mut body)
                .map_err(|e| e.to_string())?;
            std::fs::write(&path, body).map_err(|e| e.to_string())?;
            path
        }
    };
    let mut command = Command::new(exe);
    command
        .args(args)
        .stdout(Stdio::piped())
        .stderr(Stdio::piped());
    if let Some(engine) = official_launcher() {
        command.env("F8Q_ENGINE", engine);
    }
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        command.creation_flags(0x0800_0000);
    }
    let mut child = command.spawn().map_err(|e| e.to_string())?;
    let stdout = child.stdout.take().ok_or("no output")?;
    for line in std::io::BufReader::new(stdout)
        .lines()
        .map_while(Result::ok)
    {
        let mut run = run.lock().unwrap();
        if let Some(fraction) = progress_of(&line) {
            run.fraction = fraction;
        }
        run.line = line.trim_start_matches("[f8q] ").to_string();
    }
    let mut errors = String::new();
    if let Some(mut stderr) = child.stderr.take() {
        let _ = std::io::Read::read_to_string(&mut stderr, &mut errors);
    }
    match child.wait() {
        Ok(status) if status.success() => Ok(()),
        Ok(_) if !errors.trim().is_empty() => Err(errors.trim().to_string()),
        Ok(_) => Err(tr("The update failed. Try again.").to_string()),
        Err(e) => Err(e.to_string()),
    }
}

/// The installed launcher, or None for the setup and development builds: those update nothing.
fn installed_exe() -> Option<PathBuf> {
    let exe = std::env::current_exe().ok()?;
    let name = exe.file_name()?.to_string_lossy().to_ascii_lowercase();
    (name == "f8q-launcher.exe").then_some(exe)
}

/// The newest build when it is newer than this one.
fn newer_launcher() -> Option<serde_json::Value> {
    let exe = installed_exe()?;
    // What the last update moved aside can go now that this copy runs.
    let _ = std::fs::remove_file(exe.with_extension("old"));
    let agent = ureq::AgentBuilder::new()
        .timeout(Duration::from_secs(4))
        .build();
    let text = agent.get(LAUNCHER_INFO).call().ok()?.into_string().ok()?;
    let info: serde_json::Value = serde_json::from_str(&text).ok()?;
    (info["build"].as_u64()? > u64::from(BUILD)).then_some(info)
}

/// Downloads the new launcher, puts it in this one's place and starts it. A running exe can be
/// renamed, not overwritten.
fn replace_launcher(run: &Mutex<Run>, info: &serde_json::Value) -> Result<(), String> {
    use sha2::{Digest, Sha512};
    use std::io::Read;
    let exe = installed_exe().ok_or("not installed")?;
    let url = info["url"].as_str().ok_or("no address")?;
    let size = info["size"].as_u64().unwrap_or(0) as f32;
    let reply = ureq::get(url).call().map_err(|e| e.to_string())?;
    let mut reader = reply.into_reader();
    let mut body = Vec::new();
    let mut chunk = vec![0u8; 1 << 16];
    loop {
        let read = reader.read(&mut chunk).map_err(|e| e.to_string())?;
        if read == 0 {
            break;
        }
        body.extend_from_slice(&chunk[..read]);
        if size > 0. {
            run.lock().unwrap().fraction = (body.len() as f32 / size).min(1.);
        }
    }
    let hash: String = Sha512::digest(&body)
        .iter()
        .map(|b| format!("{b:02x}"))
        .collect();
    if Some(hash.as_str()) != info["sha512"].as_str() {
        return Err(tr("The update failed. Try again.").to_string());
    }
    let fresh = exe.with_extension("new");
    std::fs::write(&fresh, body).map_err(|e| e.to_string())?;
    let old = exe.with_extension("old");
    let _ = std::fs::remove_file(&old);
    std::fs::rename(&exe, &old).map_err(|e| e.to_string())?;
    if let Err(e) = std::fs::rename(&fresh, &exe) {
        let _ = std::fs::rename(&old, &exe);
        return Err(e.to_string());
    }
    std::process::Command::new(&exe)
        .spawn()
        .map_err(|e| e.to_string())?;
    std::process::exit(0);
}

/// The official launcher's output for `args`, with no window.
pub(super) fn run_official(official: &std::path::Path, args: &[&str]) -> Result<String, String> {
    let mut command = std::process::Command::new(official);
    command
        .args(args)
        .env("HORIZON_LAUNCHER_NO_SELF_UPDATE", "1");
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        command.creation_flags(0x0800_0000);
    }
    let output = command.output().map_err(|e| e.to_string())?;
    if output.status.success() {
        Ok(String::from_utf8_lossy(&output.stdout).to_string())
    } else {
        Err(String::from_utf8_lossy(&output.stderr).trim().to_string())
    }
}

/// Whether the official launcher has a Microsoft account; None when it can't be asked.
fn has_account(official: &std::path::Path) -> Option<bool> {
    let mut command = std::process::Command::new(official);
    command.args(["account", "list", "--json"]);
    command.env("HORIZON_LAUNCHER_NO_SELF_UPDATE", "1");
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        command.creation_flags(0x0800_0000);
    }
    let output = command.output().ok()?;
    let list: serde_json::Value = serde_json::from_slice(&output.stdout).ok()?;
    Some(list["accounts"].as_array().is_some_and(|a| !a.is_empty()))
}

/// `To sign in, open URL in a browser and enter the code CODE`: the address and the code.
fn sign_in_prompt(line: &str) -> Option<(String, String)> {
    let rest = line.split("open ").nth(1)?;
    let (url, rest) = rest.split_once(" in a browser")?;
    let code = rest.rsplit(' ').next()?;
    Some((url.to_string(), code.to_string()))
}

/// Signs in through the official launcher with no window: the player gets the code here.
fn run_sign_in(run: &Mutex<Run>, official: PathBuf) -> Result<(), String> {
    use std::io::BufRead;
    use std::process::{Command, Stdio};
    let mut command = Command::new(official);
    command
        .args(["account", "sign-in"])
        .env("HORIZON_LAUNCHER_NO_SELF_UPDATE", "1")
        .stdout(Stdio::null())
        .stderr(Stdio::piped());
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        command.creation_flags(0x0800_0000);
    }
    let mut child = command.spawn().map_err(|e| e.to_string())?;
    let stderr = child.stderr.take().ok_or("no output")?;
    let mut last = String::new();
    for line in std::io::BufReader::new(stderr)
        .lines()
        .map_while(Result::ok)
    {
        if let Some((url, code)) = sign_in_prompt(&line) {
            run.lock().unwrap().line = trf(
                "Open {url} and enter the code {code}",
                &[("url", &url), ("code", &code)],
            )
            .to_string();
            let _ = Command::new("rundll32")
                .args(["url.dll,FileProtocolHandler", &url])
                .spawn();
        } else {
            last = line;
        }
    }
    match child.wait() {
        Ok(status) if status.success() => Ok(()),
        Ok(_) if !last.trim().is_empty() => Err(last),
        Ok(_) => Err(tr("Sign-in failed. Try again.").to_string()),
        Err(e) => Err(e.to_string()),
    }
}

/// The folder beside this launcher that holds its own copy of Horizon.
fn engine_folder() -> Option<PathBuf> {
    match installed_exe() {
        Some(exe) => Some(exe.parent()?.join("engine")),
        None => Some(PathBuf::from(std::env::var_os("LOCALAPPDATA")?).join(r"F8Q\engine")),
    }
}

/// Only one download of Horizon at a time.
static ENGINE: Mutex<()> = Mutex::new(());

/// Keeps the copy of Horizon current. The setup exe is the launcher itself: under the plain name
/// it runs as one. A failed check leaves the copy there as it is.
pub(super) fn update_engine() -> Result<(), String> {
    let _only_one = ENGINE.lock().unwrap();
    let dir = engine_folder().ok_or("no folder for Horizon")?;
    let exe = dir.join("horizon-launcher.exe");
    let version = dir.join("version.txt");
    let reply = ureq::get(ENGINE_RELEASE)
        .set("User-Agent", "f8q-launcher")
        .call()
        .map_err(|e| e.to_string())?;
    let releases: serde_json::Value = reply.into_json().map_err(|e| e.to_string())?;
    let release = releases
        .as_array()
        .into_iter()
        .flatten()
        .find(|r| {
            r["prerelease"] == false
                && r["tag_name"]
                    .as_str()
                    .is_some_and(|t| t.starts_with("launcher@"))
        })
        .ok_or("no release")?;
    let tag = release["tag_name"].as_str().ok_or("no release")?;
    if exe.is_file() && std::fs::read_to_string(&version).is_ok_and(|v| v == tag) {
        return Ok(());
    }
    let url = release["assets"]
        .as_array()
        .into_iter()
        .flatten()
        .find(|a| a["name"] == "horizon-launcher-setup.exe")
        .and_then(|a| a["browser_download_url"].as_str())
        .ok_or("no Horizon download")?;
    let reply = ureq::get(url).call().map_err(|e| e.to_string())?;
    let mut body = Vec::new();
    std::io::Read::read_to_end(&mut reply.into_reader(), &mut body).map_err(|e| e.to_string())?;
    std::fs::create_dir_all(&dir).map_err(|e| e.to_string())?;
    let fresh = dir.join("horizon-launcher.new");
    std::fs::write(&fresh, body).map_err(|e| e.to_string())?;
    let old = dir.join("horizon-launcher.old");
    let _ = std::fs::remove_file(&old);
    if exe.is_file() {
        std::fs::rename(&exe, &old).map_err(|e| e.to_string())?;
    }
    std::fs::rename(&fresh, &exe).map_err(|e| e.to_string())?;
    std::fs::write(&version, tag).map_err(|e| e.to_string())
}

/// This launcher's copy of Horizon, or one the player installed.
pub(super) fn official_launcher() -> Option<PathBuf> {
    if let Some(exe) = engine_folder().map(|d| d.join("horizon-launcher.exe")) {
        if exe.is_file() {
            return Some(exe);
        }
    }
    let home = PathBuf::from(std::env::var_os("USERPROFILE")?);
    let mut dirs = Vec::new();
    if let Some(local) = std::env::var_os("LOCALAPPDATA") {
        dirs.push(PathBuf::from(local).join(r"Programs\Horizon Launcher"));
    }
    dirs.extend([
        home.join("Desktop"),
        home.join(r"OneDrive\Desktop"),
        home.join("Downloads"),
    ]);
    dirs.extend(
        ["Desktop", "OneDrive/Desktop", "Downloads", "Documents"]
            .iter()
            .flat_map(|place| std::fs::read_dir(home.join(place)).into_iter().flatten())
            .flatten()
            .map(|item| item.path()),
    );
    dirs.into_iter()
        .map(|d| d.join("horizon-launcher.exe"))
        .find(|p| p.is_file())
}

impl Launcher {
    /// Asks the server for its players, at most once a minute: the sidebar calls it on every frame.
    pub(super) fn ask_server_status(&mut self, cx: &mut Context<Self>) {
        self.load_server_icon(cx);
        self.check_for_launcher_update(cx);
        if self.selected_instance().is_none_or(|i| i.java.is_none()) {
            if let Some(instance) = self.server_instance() {
                self.select_instance(&instance);
            }
        }
        if std::mem::take(&mut self.server_home.sign_in) {
            self.start_sign_in_official(cx);
        }
        if self
            .server_home
            .asked
            .is_some_and(|at| at.elapsed() < FRESH)
        {
            return;
        }
        self.server_home.asked = Some(Instant::now());
        self.ping_added_servers(cx);
        // Later checks only light the refresh button: nothing is replaced under the player.
        if self.server_home.update_answered && self.server_home.self_update.is_none() {
            let check = cx
                .background_executor()
                .spawn(async { newer_launcher().is_some() });
            cx.spawn(async move |this, cx| {
                let waiting = check.await;
                let _ = this.update(cx, |this, cx| {
                    this.server_home.update_waiting = waiting;
                    cx.notify();
                });
            })
            .detach();
        }
        let own = self.server_instance().map(|i| i.home);
        let others: Vec<(String, PathBuf)> = self
            .instances
            .list
            .iter()
            .filter(|i| i.java.as_ref().is_some_and(|j| j.minecraft == "1.21.1"))
            .map(|i| (i.id.clone(), i.home.clone()))
            .collect();
        self.server_home.check_generation += 1;
        let generation = self.server_home.check_generation;
        let check = cx
            .background_executor()
            .spawn(async move { check_files(own, others) });
        cx.spawn(async move |this, cx| {
            let check = check.await;
            let _ = this.update(cx, |this, cx| {
                if generation != this.server_home.check_generation {
                    return;
                }
                this.server_home.check = check;
                cx.notify();
            });
        })
        .detach();
        let task = cx
            .background_executor()
            .spawn(async { horizon_launcher::telemetry::server_players(ADDRESS) });
        cx.spawn(async move |this, cx| {
            let answer = task.await;
            let _ = this.update(cx, |this, cx| {
                this.server_home.status = match answer {
                    Some(p) => Status::Online {
                        players: p.online,
                        max: p.max,
                    },
                    None => Status::Offline,
                };
                cx.notify();
            });
        })
        .detach();
    }

    /// The status as one line, and whether the server answered.
    pub(super) fn server_status_line(&self) -> (String, bool) {
        match self.server_home.status {
            Status::Asking => (tr("Checking…").to_string(), false),
            Status::Offline => (tr("Offline").to_string(), false),
            Status::Online { players, max } => (
                trf(
                    "{online} of {max} players",
                    &[("online", &players.to_string()), ("max", &max.to_string())],
                )
                .to_string(),
                true,
            ),
        }
    }

    fn server_instance(&self) -> Option<instances::Instance> {
        self.instances
            .list
            .iter()
            .find(|i| i.id == INSTANCE || i.name == NAME)
            .cloned()
    }

    /// The top bar's sign-in, handled on the next frame where there is a window context.
    pub(super) fn want_official_sign_in(&mut self) {
        self.server_home.sign_in = true;
    }

    fn start_sign_in_official(&mut self, cx: &mut Context<Self>) {
        if self.server_home.run.is_some() {
            return;
        }
        let Some(official) = official_launcher() else {
            self.start_update(cx);
            return;
        };
        let run = Arc::new(Mutex::new(Run {
            line: tr("Getting ready…").to_string(),
            ..Run::default()
        }));
        self.server_home.note = None;
        self.server_home.run = Some(run.clone());
        let worker = run.clone();
        std::thread::spawn(move || {
            let result = run_sign_in(&worker, official);
            worker.lock().unwrap().done = Some(result);
        });
        self.follow_run(run, cx);
    }

    /// Redraws while `run` works, then checks everything again.
    pub(super) fn follow_run(&mut self, run: Arc<Mutex<Run>>, cx: &mut Context<Self>) {
        cx.spawn(async move |this, cx| loop {
            cx.background_executor()
                .timer(Duration::from_millis(200))
                .await;
            let done = run.lock().unwrap().done.take();
            let finished = done.is_some();
            let alive = this.update(cx, |this, cx| {
                if let Some(result) = done {
                    this.server_home.run = None;
                    this.server_home.self_update = None;
                    this.server_home.note = result.err();
                    this.reload_instances();
                    this.server_home.asked = None;
                    this.ask_server_status(cx);
                }
                cx.notify();
            });
            if finished || alive.is_err() {
                break;
            }
        })
        .detach();
    }

    /// Once per start: a newer launcher replaces this one, with its progress on the page.
    fn check_for_launcher_update(&mut self, cx: &mut Context<Self>) {
        if std::mem::replace(&mut self.server_home.update_checked, true) {
            return;
        }
        let check = cx.background_executor().spawn(async { newer_launcher() });
        cx.spawn(async move |this, cx| {
            let info = check.await;
            let _ = this.update(cx, |this, cx| {
                this.server_home.update_answered = true;
                let Some(info) = info else {
                    cx.notify();
                    return;
                };
                let run = Arc::new(Mutex::new(Run {
                    line: tr("Updating the launcher…").to_string(),
                    ..Run::default()
                }));
                this.server_home.run = Some(run.clone());
                this.server_home.self_update = Some(run.clone());
                let worker = run.clone();
                std::thread::spawn(move || {
                    let result = replace_launcher(&worker, &info);
                    worker.lock().unwrap().done = Some(result);
                });
                this.follow_run(run, cx);
                cx.notify();
            });
        })
        .detach();
    }

    /// The top bar's refresh button, while a newer launcher waits: closes this one, and the
    /// next start updates over the opening.
    pub(super) fn launcher_update_button(&self, cx: &mut Context<Self>) -> Option<Stateful<Div>> {
        if !self.server_home.update_waiting {
            return None;
        }
        Some(
            crate::glass::plate(horizon_theme::glass::radius::LARGE)
                .id("launcher-update")
                .flex()
                .flex_none()
                .items_center()
                .justify_center()
                .size(px(44.))
                .rounded(px(horizon_theme::glass::radius::LARGE))
                .hover(|s| s.bg(crate::glass_ui::white(0.14)))
                .cursor_pointer()
                .child(div().relative().flex().child(crate::glass_ui::icon(
                    "refresh",
                    20.,
                    crate::glass_ui::white(horizon_theme::glass::ink::FULL),
                )))
                .on_click(|_, _, _| {
                    if let Some(exe) = installed_exe() {
                        if std::process::Command::new(exe).spawn().is_ok() {
                            std::process::exit(0);
                        }
                    }
                }),
        )
    }

    /// Whether the opening should stay: the update check is out, or an update is running.
    pub(super) fn holds_opening(&self) -> bool {
        self.server_home.self_update.is_some() || !self.server_home.update_answered
    }

    /// The launcher's own update, for the opening: how far, and what it says.
    pub(super) fn self_update_progress(&self) -> Option<(f32, String)> {
        let run = self.server_home.self_update.as_ref()?.lock().unwrap();
        Some((run.fraction, run.line.clone()))
    }

    /// The server's icon at `size`, once known.
    pub(super) fn server_icon(&self, size: f32) -> Option<gpui::Img> {
        self.server_home
            .icon
            .clone()
            .map(|image| gpui::img(image).size(px(size)).rounded(px(size / 4.)))
    }

    /// Once per start: the saved icon at once, then the server's own.
    fn load_server_icon(&mut self, cx: &mut Context<Self>) {
        if std::mem::replace(&mut self.server_home.icon_asked, true) {
            return;
        }
        if let Ok(png) = std::fs::read(icon_file()) {
            self.server_home.icon = Some(server_image(png));
        }
        let task = cx.background_executor().spawn(async { read_server_icon() });
        cx.spawn(async move |this, cx| {
            let Some(png) = task.await else {
                return;
            };
            let _ = std::fs::write(icon_file(), &png);
            let _ = this.update(cx, |this, cx| {
                this.server_home.icon = Some(server_image(png));
                cx.notify();
            });
        })
        .detach();
    }

    /// What changed in the pack and what is in it: under the play button.
    fn pack_panels(&self, cx: &mut Context<Self>) -> Div {
        let t = self.theme;
        let n = t.night;
        let check = &self.server_home.check;
        let heading = |text: String| shell::text(night::TITLE, c(n.ink)).relative().child(text);
        let line = |sign: &'static str, color: Hsla, name: &String| {
            div()
                .relative()
                .flex()
                .gap(px(8.))
                .child(shell::text(night::UI, color).child(sign))
                .child(
                    shell::text(night::UI, c(n.ink_soft))
                        .min_w_0()
                        .truncate()
                        .child(name.clone()),
                )
        };
        let changes_open = self.server_home.changes_open;
        let fold_changes = div()
            .id("pack-changes-fold")
            .relative()
            .flex()
            .items_center()
            .justify_between()
            .gap(px(12.))
            .cursor_pointer()
            .child(heading(tr("What's new").to_string()))
            .child(
                crate::glass_ui::icon("arrow-down-01", 18., c(n.ink_soft))
                    .flex_none()
                    .with_transformation(gpui::Transformation::rotate(gpui::radians(
                        if changes_open {
                            std::f32::consts::PI
                        } else {
                            0.
                        },
                    ))),
            )
            .on_click(cx.listener(|this, _: &ClickEvent, _, cx| {
                this.server_home.changes_open = !this.server_home.changes_open;
                cx.notify();
            }));
        let mut log = crate::glass::plate(20.)
            .flex()
            .flex_col()
            .flex_1()
            .min_w(px(280.))
            .gap(px(12.))
            .p(px(20.))
            .child(fold_changes);
        if changes_open {
            for change in check.changelog.iter().take(5) {
                log = log
                    .child(
                        shell::text(night::CAPTION, c(n.muted))
                            .relative()
                            .child(change.date.clone()),
                    )
                    .children(change.added.iter().map(|m| line("+", c(n.success), m)))
                    .children(change.updated.iter().map(|m| line("~", c(n.accent), m)))
                    .children(change.removed.iter().map(|m| line("-", c(n.danger), m)));
            }
            if check.changelog.is_empty() {
                log = log.child(
                    shell::text(night::UI, c(n.muted))
                        .relative()
                        .child(tr("No changes yet.")),
                );
            }
        }
        let mut names = div().relative().flex().flex_wrap().gap(px(6.));
        for name in &check.mods {
            names = names.child(
                div()
                    .px(px(10.))
                    .py(px(4.))
                    .rounded(px(8.))
                    .bg(crate::glass_ui::white(0.1))
                    .child(shell::text(night::CAPTION, c(n.ink_soft)).child(name.clone())),
            );
        }
        let open = self.server_home.mods_open;
        let fold =
            div()
                .id("pack-mods-fold")
                .relative()
                .flex()
                .items_center()
                .justify_between()
                .gap(px(12.))
                .cursor_pointer()
                .child(heading(
                    trf(
                        "Mods in the pack: {count}",
                        &[("count", &check.mods.len().to_string())],
                    )
                    .to_string(),
                ))
                .child(
                    crate::glass_ui::icon("arrow-down-01", 18., c(n.ink_soft))
                        .flex_none()
                        .with_transformation(gpui::Transformation::rotate(gpui::radians(
                            if open { std::f32::consts::PI } else { 0. },
                        ))),
                )
                .on_click(cx.listener(|this, _: &ClickEvent, _, cx| {
                    this.server_home.mods_open = !this.server_home.mods_open;
                    cx.notify();
                }));
        let list = crate::glass::plate(20.)
            .flex()
            .flex_col()
            .flex_1()
            .min_w(px(280.))
            .gap(px(12.))
            .p(px(20.))
            .child(fold)
            .when(open, |d| d.child(names));
        div()
            .flex()
            .flex_wrap()
            .items_start()
            .gap(px(12.))
            .child(log)
            .child(list)
    }

    /// A new progress for Home's page, already shown.
    pub(super) fn start_run(&mut self) -> Arc<Mutex<Run>> {
        let run = Arc::new(Mutex::new(Run::default()));
        self.server_home.note = None;
        self.server_home.run = Some(run.clone());
        run
    }

    pub(super) fn server_home_busy(&self) -> bool {
        self.server_home.run.is_some()
    }

    pub(super) fn server_home_progress(&self) -> Option<(f32, String)> {
        let run = self.server_home.run.as_ref()?.lock().unwrap();
        Some((run.fraction, run.line.clone()))
    }

    pub(super) fn server_home_note(&self) -> Option<String> {
        self.server_home.note.clone()
    }

    /// What the button should do now.
    fn need(&self) -> Need {
        let check = &self.server_home.check;
        match self.server_instance() {
            None if check.adopt.is_some() => Need::Update,
            None => Need::Download,
            Some(_) if check.known && check.total > 0 && check.missing == check.total => {
                Need::Download
            }
            Some(_) if check.missing + check.settings > 0 => Need::Update,
            Some(_) if check.no_account => Need::SignIn,
            Some(_) => Need::Play,
        }
    }

    /// Downloads what's missing here in the launcher: a known instance updates with its own
    /// updater, anything else goes through the install, which makes or adopts the instance.
    fn start_update(&mut self, cx: &mut Context<Self>) {
        if self.server_home.run.is_some() {
            return;
        }
        let instance = self.server_instance();
        let (exe, args) = match instance
            .as_ref()
            .map(|i| (i, i.home.join("f8q-updater.exe")))
        {
            Some((i, exe)) if exe.is_file() => (
                Some(exe),
                vec![
                    "sync".to_string(),
                    MANIFEST.to_string(),
                    i.home.display().to_string(),
                ],
            ),
            _ => {
                let mut args = vec!["install".to_string(), "--quiet".to_string()];
                if let Some(id) = self.server_home.check.adopt.clone() {
                    args.extend(["--instance".to_string(), id]);
                }
                (None, args)
            }
        };
        let run = Arc::new(Mutex::new(Run {
            line: tr("Getting ready…").to_string(),
            ..Run::default()
        }));
        self.server_home.note = None;
        self.server_home.run = Some(run.clone());
        let worker = run.clone();
        std::thread::spawn(move || {
            let result = run_updater(&worker, exe, args);
            worker.lock().unwrap().done = Some(result);
        });
        self.follow_run(run, cx);
    }

    /// This build has no Microsoft sign-in of its own, so the official launcher starts the game
    /// with the player's session.
    pub(super) fn play_on_server(&mut self) {
        let Some(instance) = self.server_instance() else {
            return;
        };
        let Some(official) = official_launcher() else {
            self.server_home.note =
                Some(tr("Getting the game ready, try again in a minute.").to_string());
            return;
        };
        let mut command = std::process::Command::new(official);
        command.args(["play", "--instance", &instance.id, "--server", ADDRESS]);
        command.env("HORIZON_LAUNCHER_NO_SELF_UPDATE", "1");
        #[cfg(windows)]
        {
            use std::os::windows::process::CommandExt;
            command.creation_flags(0x0800_0000);
        }
        self.server_home.note = match command.spawn() {
            Ok(_) => {
                Some(tr("Starting the game… The first start takes a few minutes.").to_string())
            }
            Err(e) => Some(e.to_string()),
        };
    }

    pub(super) fn server_home_page(&mut self, cx: &mut Context<Self>) -> Div {
        let t = self.theme;
        let n = t.night;
        let (line, online) = self.server_status_line();
        let running = self.client.is_some() || self.starting_client.is_some();
        let state = div()
            .flex()
            .items_center()
            .gap(px(8.))
            .child(
                div()
                    .flex_none()
                    .size(px(6.))
                    .bg(if online { t.success } else { t.warn }),
            )
            .child(
                shell::text(night::CAPTION, c(n.ink_soft))
                    .font_weight(FontWeight::MEDIUM)
                    .child(format!("{ADDRESS} · {line}")),
            );
        let need = self.need();
        let progress = self.server_home.run.as_ref().map(|run| {
            let run = run.lock().unwrap();
            (run.fraction, run.line.clone())
        });
        let busy = progress.is_some();
        let mut play = div()
            .id("server-play")
            .flex()
            .flex_none()
            .items_center()
            .justify_center()
            .gap(px(10.))
            .min_w(px(200.))
            .h(px(56.))
            .px(px(24.))
            .rounded(px(horizon_theme::glass::radius::LARGE))
            .bg(c(n.accent))
            .text_color(c(n.on_accent))
            .font_family(horizon_theme::glass::FONT)
            .text_size(px(18.))
            .font_weight(FontWeight::SEMIBOLD)
            .cursor_pointer()
            .hover(|s| s.opacity(0.92))
            .child(crate::glass_ui::icon(
                if busy || need == Need::Update {
                    "f8q-refresh"
                } else if need == Need::Download {
                    "download-04"
                } else {
                    "play"
                },
                20.,
                c(n.on_accent),
            ))
            .child(label(
                tr(if busy {
                    "Updating…"
                } else if running {
                    "Running"
                } else {
                    match need {
                        Need::Download => "Download the instance",
                        Need::Update => "Update",
                        Need::SignIn => "Sign in with Microsoft",
                        Need::Play => "Play",
                    }
                })
                .to_string(),
            ));
        if running || busy {
            play = disabled(play);
        } else if need == Need::Play {
            play = play.on_click(cx.listener(|this, _: &ClickEvent, _, cx| {
                this.play_on_server();
                cx.notify();
            }));
        } else if need == Need::SignIn {
            play = play.on_click(cx.listener(|this, _: &ClickEvent, _, cx| {
                this.start_sign_in_official(cx);
                cx.notify();
            }));
        } else {
            play = play.on_click(cx.listener(|this, _: &ClickEvent, _, cx| {
                this.start_update(cx);
                cx.notify();
            }));
        }
        let (players, _) = self.server_status_line();
        let total = self.server_home.check.total;
        let fact = |value: String, name: SharedString| {
            crate::glass::plate(16.)
                .flex()
                .flex_col()
                .flex_1()
                .min_w_0()
                .gap(px(4.))
                .p(px(16.))
                .child(
                    shell::text(night::TITLE, c(n.ink))
                        .relative()
                        .truncate()
                        .child(value),
                )
                .child(
                    shell::text(night::CAPTION, c(n.muted))
                        .relative()
                        .truncate()
                        .child(name),
                )
        };
        let facts = div()
            .flex()
            .flex_col()
            .gap(px(12.))
            .child(
                div()
                    .flex()
                    .flex_wrap()
                    .gap(px(12.))
                    .child(fact(players, tr("On the server").into()))
                    .child(fact(
                        if total > 0 {
                            total.to_string()
                        } else {
                            "-".to_string()
                        },
                        tr("Files in the pack").into(),
                    ))
                    .child(fact("1.21.1".to_string(), "NeoForge".into())),
            )
            .child(self.pack_panels(cx));
        div()
            .flex()
            .flex_col()
            .gap(px(24.))
            .w_full()
            .max_w(px(980.))
            .pt(px(72.))
            .pb(px(32.))
            .child(
                div().flex().flex_none().flex_col().gap(px(20.)).child(
                    crate::glass::plate(16.).p(px(20.)).child(
                        div()
                            .relative()
                            .flex()
                            .flex_col()
                            .gap(px(8.))
                            .child(
                                div()
                                    .flex()
                                    .items_center()
                                    .gap(px(16.))
                                    .children(self.server_icon(56.))
                                    .child(shell::text(night::DISPLAY, c(n.ink)).child(NAME)),
                            )
                            .child(state),
                    ),
                ),
            )
            .child(
                div()
                    .flex()
                    .flex_wrap()
                    .items_center()
                    .gap(px(10.))
                    .child(play),
            )
            .children(progress.map(|(fraction, line)| {
                div()
                    .flex()
                    .flex_col()
                    .gap(px(8.))
                    .w_full()
                    .child(
                        div()
                            .relative()
                            .flex()
                            .child(progress_bar(Some((fraction, fraction)), &t)),
                    )
                    .child(shell::text(night::CAPTION, c(n.ink_soft)).truncate().child(
                        if fraction > 0. {
                            format!("{:.0}%  {line}", fraction * 100.)
                        } else {
                            line
                        },
                    ))
            }))
            .children(
                self.server_home
                    .note
                    .clone()
                    .map(|note| shell::text(night::UI, c(n.ink_soft)).child(note)),
            )
            .child(facts)
    }
}

#[cfg(test)]
mod pack_panel_tests {
    use super::*;
    #[test]
    fn published_snapshot_includes_config_only_changes() {
        let manifest = serde_json::json!({"files": [{"path":"mods/a.jar"}], "changelog": [
            {"date":"2026-11-04", "updated":["Camera and emote wheel disabled"], "added":[], "removed":[]}
        ]});
        let changes = read_changelog(&manifest);
        assert_eq!(changes.len(), 1);
        assert_eq!(changes[0].updated, ["Camera and emote wheel disabled"]);
        assert_eq!(changes[0].date, "2026-11-04");
    }
}
