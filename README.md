# SlurmDroid

A native Android app for monitoring and controlling a Slurm HPC cluster from your phone.

> [!IMPORTANT]
> The app has until now only been tested for the [HPC-Cluster of the universities of the state
> of Baden Württenberg (Germany)](https://www.bwhpc.de/)

## Features

- **Live job monitoring** — active jobs with state, partition, time used/limit, and a progress bar for running jobs
- **Swipe to cancel** — swipe a job left to cancel it with a confirmation dialog
- **Job history** — browse past jobs grouped by date; re-submit any previous job with one tap
- **Cluster dashboard** — collapsible partition table showing node availability and CPU load per partition, color-coded green/yellow/red
- **Notifications** — persistent notification while a job is running (tap to open job details); replaced by a success or error notification when the job finishes. Can be disabled in Settings.
- **Auto-polling** — refreshes every 10 s with a countdown spinner in the toolbar; pull-to-refresh available on every screen
- **TOTP support** — computes one-time passwords automatically from a stored secret; falls back to a manual OTP dialog if no secret is configured
- **Secure credential storage** — password and TOTP seed stored exclusively in Android EncryptedSharedPreferences backed by the Android Keystore; SSH key pair generated and held in the Keystore

## Screenshots

<img src="/assets/screenshots/screenshot_dashboard.png" width="300"/>
<img src="/assets/screenshots/screenshot_job_history.png" width="300"/>
<img src="/assets/screenshots/screenshot_job_details.png" width="300"/>
<img src="/assets/screenshots/screenshot_settings.png" width="300"/>

## Requirements

- Android 8.0+ (API 26)
- SSH access to a Slurm cluster with keyboard-interactive authentication (OTP + password)

## Setup

1. Install the app and open **Settings**
2. Enter your cluster hostname, port, and username
3. Enter your password
4. Either scan your TOTP QR code or paste the Base32 secret manually
5. Optionally generate an SSH key pair and add the public key to `~/.ssh/authorized_keys` on the cluster for faster reconnects
6. Tap **Test Connection** to verify

Credentials are saved automatically as you type.

## Architecture

```
app/src/main/java/org/slurmdroid/
├── core/
│   ├── ssh/          # SshManager, SshAuthHandler, CommandExecutor, credential store
│   ├── feature/      # ServerFeature interface + FeatureRegistry (plugin system)
│   ├── notifications/ # JobNotificationManager
│   └── db/           # Room database
├── features/
│   └── slurm/        # Parser, repository, ViewModels, Screens
├── service/          # SshForegroundService — persistent SSH session + polling loop
└── ui/               # MainActivity, AppNavigation, Dashboard, Settings
```

The app is built around a `ServerFeature` plugin interface — Slurm is the first feature, and additional server tools (e.g. nnU-Net monitoring) can be added without touching the navigation or dashboard code.

**SSH access** is centralised in a single `CommandExecutor`; no feature interacts with JSch directly.

## Tech stack

| Layer | Library |
|---|---|
| UI | Jetpack Compose + Material 3 |
| Navigation | Navigation Compose |
| DI | Hilt |
| SSH | JSch |
| TOTP | java-otp |
| Local DB | Room |
| Secure storage | EncryptedSharedPreferences + Android Keystore |
| Background work | WorkManager + Foreground Service |

## Building

```bash
./gradlew assembleDebug
```

Requires Android SDK with API 35 build tools. No API keys or secrets needed for a local build.

## License

This project is licensed under the GNU General Public License v3.0 — see the [LICENSE](LICENSE) file for details.
