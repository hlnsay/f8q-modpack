use std::fs::{self, File};
use std::path::{Path, PathBuf};

#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub enum Phase { #[default] Idle, Starting, Running }

pub fn existing_window() -> bool {
    #[cfg(windows)] {
        #[link(name="user32")]
        unsafe extern "system" { fn FindWindowW(class:*const u16,title:*const u16)->*mut std::ffi::c_void; }
        let title:Vec<u16>="F8Q NEOFORGE 1.21.1".encode_utf16().chain(Some(0)).collect();
        // SAFETY: a null class and a valid, terminated UTF-16 title; no window input is sent.
        return !unsafe { FindWindowW(std::ptr::null(),title.as_ptr()) }.is_null();
    }
    #[cfg(not(windows))] { false }
}

pub fn probe(home:&Path)->Phase {
    if existing_window() { return Phase::Running; }
    probe_lock(home)
}

fn probe_lock(home:&Path)->Phase {
    let Ok(file)=File::options().read(true).write(true).open(home.join(".f8q-game.lock")) else {return Phase::Idle};
    match file.try_lock() {
        Ok(())=>Phase::Idle,
        Err(fs::TryLockError::WouldBlock)=>{
            let state=fs::read(home.join(".f8q-game-state.json")).ok().and_then(|b|serde_json::from_slice::<serde_json::Value>(&b).ok());
            if state.as_ref().and_then(|s|s["phase"].as_str())==Some("running") {Phase::Running} else {Phase::Starting}
        }
        Err(_)=>Phase::Idle,
    }
}

pub struct Session { _lock:File, state:PathBuf }
impl Session {
    pub fn acquire(home:&Path)->Result<Option<Self>,String> {
        if existing_window() { return Ok(None); }
        Self::acquire_lock(home)
    }
    fn acquire_lock(home:&Path)->Result<Option<Self>,String> {
        fs::create_dir_all(home).map_err(|e|e.to_string())?;
        let file=File::options().read(true).write(true).create(true).truncate(false).open(home.join(".f8q-game.lock")).map_err(|e|e.to_string())?;
        match file.try_lock() {
            Ok(())=>{
                let session=Self{_lock:file,state:home.join(".f8q-game-state.json")};
                session.phase("starting");Ok(Some(session))
            }
            Err(fs::TryLockError::WouldBlock)=>Ok(None),
            Err(e)=>Err(e.to_string()),
        }
    }
    pub fn phase(&self,phase:&str) {
        let _=fs::write(&self.state,serde_json::json!({"phase":phase,"wrapper_pid":std::process::id()}).to_string());
    }
}
impl Drop for Session {fn drop(&mut self){let _=fs::remove_file(&self.state);}}

#[cfg(test)] mod tests {
    use super::*;
    #[test] fn exclusive_lock_blocks_duplicate_start_and_stale_state_does_not_block() {
        let home=std::env::temp_dir().join(format!("f8q-session-test-{}",std::process::id()));
        fs::create_dir_all(&home).unwrap();
        let session=Session::acquire_lock(&home).unwrap().unwrap();
        assert!(Session::acquire_lock(&home).unwrap().is_none());
        assert_eq!(probe_lock(&home),Phase::Starting);
        session.phase("running");assert_eq!(probe_lock(&home),Phase::Running);
        drop(session);assert_eq!(probe_lock(&home),Phase::Idle);
        fs::write(home.join(".f8q-game-state.json"),r#"{"phase":"running","wrapper_pid":1}"#).unwrap();
        assert_eq!(probe_lock(&home),Phase::Idle);
        assert!(Session::acquire_lock(&home).unwrap().is_some());
    }
}
