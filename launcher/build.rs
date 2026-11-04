//! Embeds Horizon's icon (`packaging/icons/horizon.ico`) in the Windows executables, as icon
//! resource 1: Explorer and the task bar show it, and the window takes it as its icon.
fn main() {
    let dir = std::env::var("CARGO_MANIFEST_DIR").unwrap_or_default();
    let ico = format!("{dir}/../packaging/icons/horizon.ico");
    println!("cargo:rerun-if-changed={ico}");
    #[cfg(windows)]
    if std::env::var("CARGO_CFG_TARGET_OS").as_deref() == Ok("windows") {
        let mut resource = winresource::WindowsResource::new();
        resource.set_icon(&ico);
        let version = std::fs::read_to_string(format!("{dir}/F8Q_VERSION")).expect("F8Q version");
        println!("cargo:rerun-if-changed={dir}/F8Q_VERSION");
        let mut parts=version.trim().split('.').map(|v|v.parse::<u64>().expect("numeric version"));
        let version_number=(parts.next().unwrap_or(0)<<48)|(parts.next().unwrap_or(0)<<32)|(parts.next().unwrap_or(0)<<16);
        resource.set("FileDescription", "F8Q Launcher")
            .set("ProductName", "F8Q Launcher").set("CompanyName", "F8Q")
            .set("FileVersion", version.trim()).set("ProductVersion", version.trim())
            .set_version_info(winresource::VersionInfo::FILEVERSION, version_number)
            .set_version_info(winresource::VersionInfo::PRODUCTVERSION, version_number);
        if let Err(e) = resource.compile() {
            panic!("embedding {ico}: {e}");
        }
    }
}
