//! Atomic launcher replacement while old GUI, MCP or game-launch processes still hold images.
use std::fs::{self, File, OpenOptions};
use std::io;
use std::path::{Path, PathBuf};
use std::time::{SystemTime, UNIX_EPOCH};

pub struct UpdateFiles {
    _lock: File,
    executable: PathBuf,
    staged: PathBuf,
    previous: PathBuf,
}
impl UpdateFiles {
    pub fn acquire(executable: &Path, build: u64) -> Result<Self, String> {
        let lock = OpenOptions::new().read(true).write(true).create(true).truncate(false)
            .open(executable.with_extension("update.lock")).map_err(|e| e.to_string())?;
        lock.try_lock().map_err(|e| e.to_string())?;
        let stamp = SystemTime::now().duration_since(UNIX_EPOCH).map_err(|e| e.to_string())?.as_nanos();
        let id = format!("{}-{build}-{stamp}", std::process::id());
        Ok(Self {
            _lock: lock,
            executable: executable.to_owned(),
            staged: executable.with_file_name(format!("f8q-launcher.download-{id}.exe")),
            previous: executable.with_file_name(format!("f8q-launcher.previous-{id}.exe")),
        })
    }
    pub fn install(&self, bytes: &[u8]) -> io::Result<()> {
        fs::write(&self.staged, bytes)?;
        fs::rename(&self.executable, &self.previous)?;
        if let Err(error) = fs::rename(&self.staged, &self.executable) {
            fs::rename(&self.previous, &self.executable)?;
            return Err(error);
        }
        Ok(())
    }
    pub fn rollback(&self) -> io::Result<()> {
        fs::rename(&self.executable, &self.staged)?;
        fs::rename(&self.previous, &self.executable)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::process::{Command, Stdio};
    use std::thread;
    use std::time::{Duration, Instant};
    fn folder(label: &str) -> PathBuf {
        let p = std::env::temp_dir().join(format!("f8q-update-{label}-{}-{}", std::process::id(), SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_nanos()));
        fs::create_dir_all(&p).unwrap(); p
    }
    #[test]
    fn replacement_and_rollback_preserve_previous_contents() {
        let root = folder("rollback"); let exe = root.join("f8q-launcher.exe");
        fs::write(&exe, b"old build").unwrap();
        let files = UpdateFiles::acquire(&exe, 24).unwrap(); files.install(b"new build").unwrap();
        assert_eq!(fs::read(&exe).unwrap(), b"new build");
        files.rollback().unwrap(); assert_eq!(fs::read(&exe).unwrap(), b"old build");
        drop(files); fs::remove_dir_all(root).unwrap();
    }
    #[test]
    fn only_one_updater_can_replace_a_launcher() {
        let root = folder("lock"); let exe = root.join("f8q-launcher.exe");
        let first = UpdateFiles::acquire(&exe, 24).unwrap();
        assert!(UpdateFiles::acquire(&exe, 24).is_err()); drop(first);
        drop(UpdateFiles::acquire(&exe, 24).unwrap()); fs::remove_dir_all(root).unwrap();
    }
    #[test]
    #[ignore]
    fn image_holder() {
        let Some(root) = std::env::var_os("F8Q_IMAGE_HOLDER") else { return; };
        fs::write(Path::new(&root).join("ready"), b"ready").unwrap();
        let mut input = String::new(); let _ = io::stdin().read_line(&mut input);
    }
    #[test]
    fn a_running_old_mcp_image_does_not_block_the_next_update() {
        let root = folder("held-image"); let exe = root.join("f8q-launcher.exe");
        fs::copy(std::env::current_exe().unwrap(), &exe).unwrap();
        let mut command = Command::new(&exe);
        command.args(["--exact", "tests::image_holder", "--ignored"])
            .env("F8Q_IMAGE_HOLDER", &root).stdin(Stdio::piped()).stdout(Stdio::null());
        #[cfg(windows)] {
            use std::os::windows::process::CommandExt;
            command.creation_flags(0x08000000);
        }
        let mut child = command.spawn().unwrap();
        let until = Instant::now() + Duration::from_secs(5);
        while !root.join("ready").exists() && Instant::now() < until { thread::sleep(Duration::from_millis(10)); }
        assert!(root.join("ready").exists());
        let locked_old = exe.with_extension("old"); fs::rename(&exe, &locked_old).unwrap();
        #[cfg(windows)] assert!(fs::remove_file(&locked_old).is_err());
        fs::write(&exe, b"intermediate build").unwrap();
        let files = UpdateFiles::acquire(&exe, 24).unwrap(); files.install(b"latest build").unwrap();
        assert_eq!(fs::read(&exe).unwrap(), b"latest build");
        assert!(child.try_wait().unwrap().is_none());
        drop(child.stdin.take()); child.wait().unwrap(); drop(files); fs::remove_dir_all(root).unwrap();
    }
}
