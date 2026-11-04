//! Starts NeoForge from the Java command Horizon prepared: Horizon's launcher has no NeoForge
//! install yet, but it does hand over the session, the Java and the vanilla assets. NeoForge's own
//! installer fills a private folder and this builds the command its version file describes.
//!
//! Never print a command: it carries the player's access token.
use serde_json::Value;
use sha1::Sha1;
use sha2::{Digest, Sha512};
use std::collections::HashSet;
use std::fs;
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};

const MINECRAFT: &str = "1.21.1";
const VERSION: &str = "neoforge-21.1.265";
const INSTALLER_URL: &str = "https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.265/neoforge-21.1.265-installer.jar";
const INSTALLER_SHA512: &str = "12ea54ad844d64f56a53f414e495ba971c2cb6263a382557243f7fe31d028d49c9ea804f1219b6d5dbda72a7bb4f7e5a043a3eefdcd41923e6c8e8630200c2c3";
/// Game flags Horizon decides, passed on as they came.
const PASSED: [&str; 16] = [
    "--username",
    "--uuid",
    "--accessToken",
    "--clientId",
    "--xuid",
    "--userType",
    "--userProperties",
    "--assetsDir",
    "--assetIndex",
    "--width",
    "--height",
    "--server",
    "--port",
    "--quickPlaySingleplayer",
    "--quickPlayMultiplayer",
    "--quickPlayPath",
];

pub fn folder(home: &Path) -> PathBuf {
    home.join(".f8q-neoforge")
}

fn version_file(root: &Path, version: &str) -> PathBuf {
    root.join("versions").join(version).join(format!("{version}.json"))
}

fn installed(root: &Path) -> bool {
    root.join(".installed").is_file() && version_file(root, VERSION).is_file()
}

/// Installs NeoForge into `root` once; `java` runs its installer.
pub fn install(agent: &ureq::Agent, java: &str, root: &Path) -> Result<(), String> {
    if installed(root) {
        return Ok(());
    }
    println!("[f8q] Installing NeoForge (the first start takes a few minutes)");
    fs::create_dir_all(root).map_err(|e| e.to_string())?;
    let mut bytes = Vec::new();
    std::io::Read::read_to_end(
        &mut agent
            .get(INSTALLER_URL)
            .call()
            .map_err(|e| format!("NeoForge installer: {e}"))?
            .into_reader(),
        &mut bytes,
    )
    .map_err(|e| format!("NeoForge installer: {e}"))?;
    let hash: String = Sha512::digest(&bytes).iter().map(|b| format!("{b:02x}")).collect();
    if hash != INSTALLER_SHA512 {
        return Err("the NeoForge installer has the wrong checksum".into());
    }
    let jar = root.join("installer.jar");
    fs::write(&jar, bytes).map_err(|e| e.to_string())?;
    // The installer refuses a folder that doesn't look like a launcher's.
    fs::write(root.join("launcher_profiles.json"), "{\"profiles\":{}}").map_err(|e| e.to_string())?;
    let status = Command::new(java)
        .arg("-jar")
        .arg(&jar)
        .arg("--install-client")
        .arg(root)
        .current_dir(root)
        .stdout(Stdio::null())
        .status()
        .map_err(|e| format!("NeoForge installer: {e}"))?;
    if !status.success() || !version_file(root, VERSION).is_file() {
        return Err("the NeoForge installer failed".into());
    }
    let _ = fs::remove_file(&jar);
    fs::write(root.join(".installed"), VERSION).map_err(|e| e.to_string())
}

fn allowed_here(library: &Value) -> bool {
    let Some(rules) = library["rules"].as_array() else {
        return true;
    };
    let mut allowed = false;
    for rule in rules {
        let os = rule["os"]["name"].as_str();
        if os.is_none() || os == Some("windows") {
            allowed = rule["action"] == "allow";
        }
    }
    allowed
}

/// `group:artifact` and its classifier: NeoForge's choice of a library wins over vanilla's.
fn library_key(name: &str) -> String {
    let parts: Vec<&str> = name.split(':').collect();
    match parts.as_slice() {
        [group, artifact, _, classifier, ..] => format!("{group}:{artifact}:{classifier}"),
        [group, artifact, ..] => format!("{group}:{artifact}"),
        _ => name.to_string(),
    }
}

fn classpath(root: &Path, files: &[&Value]) -> Vec<String> {
    let mut seen = HashSet::new();
    let mut out = Vec::new();
    for file in files {
        for library in file["libraries"].as_array().into_iter().flatten() {
            let (Some(name), Some(path)) = (
                library["name"].as_str(),
                library["downloads"]["artifact"]["path"].as_str(),
            ) else {
                continue;
            };
            if !allowed_here(library) || !seen.insert(library_key(name)) {
                continue;
            }
            let full = root.join("libraries").join(path);
            if full.is_file() {
                out.push(full.display().to_string());
            }
        }
    }
    out
}

fn sha1_hex(bytes: &[u8]) -> String {
    Sha1::digest(bytes).iter().map(|b| format!("{b:02x}")).collect()
}

/// The vanilla libraries: NeoForge's installer fetches only its own and leaves these to the
/// launcher.
pub fn fetch_libraries(agent: &ureq::Agent, root: &Path) -> Result<(), String> {
    let vanilla = read_json(&version_file(root, MINECRAFT))?;
    for library in vanilla["libraries"].as_array().into_iter().flatten() {
        let artifact = &library["downloads"]["artifact"];
        let (Some(path), Some(url), Some(sha1)) = (
            artifact["path"].as_str(),
            artifact["url"].as_str(),
            artifact["sha1"].as_str(),
        ) else {
            continue;
        };
        let full = root.join("libraries").join(path);
        if !allowed_here(library) || full.is_file() {
            continue;
        }
        if !url.starts_with("https://") {
            return Err(format!("{path}: not an https download"));
        }
        println!("[f8q] Downloading {path}");
        let mut bytes = Vec::new();
        std::io::Read::read_to_end(
            &mut agent
                .get(url)
                .call()
                .map_err(|e| format!("{path}: {e}"))?
                .into_reader(),
            &mut bytes,
        )
        .map_err(|e| format!("{path}: {e}"))?;
        if sha1_hex(&bytes) != sha1 {
            return Err(format!("{path}: wrong checksum"));
        }
        if let Some(dir) = full.parent() {
            fs::create_dir_all(dir).map_err(|e| e.to_string())?;
        }
        fs::write(&full, bytes).map_err(|e| format!("{path}: {e}"))?;
    }
    Ok(())
}

fn read_json(path: &Path) -> Result<Value, String> {
    let text = fs::read_to_string(path).map_err(|e| format!("{}: {e}", path.display()))?;
    serde_json::from_str(&text).map_err(|e| format!("{}: {e}", path.display()))
}

fn value_of<'a>(args: &[&'a str], flag: &str) -> Option<&'a str> {
    let at = args.iter().position(|a| *a == flag)?;
    args.get(at + 1).copied()
}

/// The JVM and game arguments for `incoming`, the Java command Horizon built without arguments
/// of NeoForge's own.
pub fn arguments(root: &Path, home: &Path, incoming: &[&str]) -> Result<Vec<String>, String> {
    let neo = read_json(&version_file(root, VERSION))?;
    let vanilla = read_json(&version_file(root, MINECRAFT))?;
    let libraries = root.join("libraries").display().to_string();
    let natives = root.join("natives");
    fs::create_dir_all(&natives).map_err(|e| e.to_string())?;
    let natives = natives.display().to_string();

    let mut args: Vec<String> = incoming
        .iter()
        .filter(|a| a.starts_with("-Xmx"))
        .map(|a| a.to_string())
        .collect();
    if args.is_empty() {
        args.push("-Xmx4G".into());
    }
    if incoming.contains(&"-XX:+UseZGC") {
        args.push("-XX:+UseZGC".into());
        args.push("-XX:+ZGenerational".into());
    }
    let title_agent = home.join("config/f8q/window-title-agent.jar");
    if title_agent.is_file() {
        args.push(format!("-javaagent:{}", title_agent.display()));
    }
    args.extend([
        "-Dfile.encoding=UTF-8".to_string(),
        "-Dminecraft.launcher.brand=Horizon".to_string(),
        format!("-Djava.library.path={natives}"),
        format!("-Djna.tmpdir={natives}"),
        format!("-Dorg.lwjgl.system.SharedLibraryExtractPath={natives}"),
        format!("-Dio.netty.native.workdir={natives}"),
    ]);
    let mut cp = classpath(root, &[&neo, &vanilla]);
    let client = root.join("versions").join(MINECRAFT).join(format!("{MINECRAFT}.jar"));
    if client.is_file() {
        cp.push(client.display().to_string());
    }
    args.push("-cp".into());
    args.push(cp.join(";"));
    for jvm in neo["arguments"]["jvm"].as_array().into_iter().flatten() {
        let Some(text) = jvm.as_str() else { continue };
        let mut text = text
            .replace("${library_directory}", &libraries)
            .replace("${classpath_separator}", ";")
            .replace("${version_name}", VERSION);
        if text.starts_with("-DignoreList=") {
            text.push_str(&format!(",{MINECRAFT}.jar"));
        }
        args.push(text);
    }
    args.push(
        neo["mainClass"]
            .as_str()
            .ok_or("no main class in NeoForge's version file")?
            .to_string(),
    );
    args.extend(["--version".to_string(), VERSION.to_string()]);
    args.extend(["--gameDir".to_string(), home.display().to_string()]);
    for flag in PASSED {
        if let Some(value) = value_of(incoming, flag) {
            args.push(flag.to_string());
            args.push(value.to_string());
        }
    }
    if incoming.contains(&"--fullscreen") {
        args.push("--fullscreen".into());
    }
    for game in neo["arguments"]["game"].as_array().into_iter().flatten() {
        if let Some(text) = game.as_str() {
            args.push(text.to_string());
        }
    }
    Ok(args)
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn libraries_follow_their_os_rules() {
        assert!(allowed_here(&json!({})));
        assert!(allowed_here(
            &json!({"rules":[{"action":"allow","os":{"name":"windows"}}]})
        ));
        assert!(!allowed_here(
            &json!({"rules":[{"action":"allow","os":{"name":"osx"}}]})
        ));
        assert!(!allowed_here(&json!({"rules":[
            {"action":"allow"},{"action":"disallow","os":{"name":"windows"}}
        ]})));
    }

    #[test]
    fn natives_are_not_the_same_library_as_their_code() {
        assert_ne!(
            library_key("org.lwjgl:lwjgl:3.3.3"),
            library_key("org.lwjgl:lwjgl:3.3.3:natives-windows")
        );
        assert_eq!(library_key("a:b:1"), library_key("a:b:2"));
    }

    #[test]
    fn the_session_is_passed_on_and_memory_kept() {
        let root = std::env::temp_dir().join(format!("f8q-neo-{}", std::process::id()));
        let _ = fs::remove_dir_all(&root);
        for (name, body) in [
            (
                VERSION,
                json!({"mainClass":"main.Class",
                    "arguments":{"game":["--launchTarget","forgeclient"],
                        "jvm":["-DignoreList=client-extra,${version_name}.jar",
                               "-p","${library_directory}/x.jar"]},
                    "libraries":[]}),
            ),
            (MINECRAFT, json!({"libraries":[]})),
        ] {
            let file = version_file(&root, name);
            fs::create_dir_all(file.parent().unwrap()).unwrap();
            fs::write(file, body.to_string()).unwrap();
        }
        let home = root.join("game");
        let incoming = [
            "-Xmx8192M",
            "-XX:+UseZGC",
            "net.minecraft.client.main.Main",
            "--username",
            "player",
            "--accessToken",
            "secret",
            "--assetsDir",
            "D:/assets",
        ];
        let args = arguments(&root, &home, &incoming).unwrap();
        let has = |a: &str, b: &str| args.windows(2).any(|w| w[0] == a && w[1] == b);
        assert!(has("--username", "player"));
        assert!(has("--accessToken", "secret"));
        assert!(has("--assetsDir", "D:/assets"));
        assert!(has("--launchTarget", "forgeclient"));
        assert!(args.iter().any(|a| a == "-Xmx8192M"));
        assert!(args.iter().any(|a| a == "main.Class"));
        assert!(!args.iter().any(|a| a == "net.minecraft.client.main.Main"));
        assert!(args
            .iter()
            .any(|a| a == "-DignoreList=client-extra,neoforge-21.1.265.jar,1.21.1.jar"));
    }
}
