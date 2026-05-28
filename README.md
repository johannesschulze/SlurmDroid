# SlurmDroid

A native Android app for monitoring and controlling a Slurm HPC cluster from your phone, with a plugin system for additional server tools.

> [!IMPORTANT]
> The app has until now only been tested for the [HPC-Cluster of the universities of the state
> of Baden-Württemberg (Germany)](https://www.bwhpc.de/)

## Features

### Slurm

- **Live job monitoring** — active jobs with state, partition, time used/limit, and a progress bar for running jobs
- **Swipe to cancel** — swipe a job left to cancel it with a confirmation dialog
- **Submit jobs** — submit new jobs via an inline command or by selecting a `.slurm` script file from the server; configure partition, nodes, CPUs, memory, GPUs, time limit, and optional script arguments
- **Job history** — past jobs grouped by date with one-tap re-submit; Slurm array jobs are grouped under their parent with all array tasks listed and individually navigable
- **Re-submit any job** — jobs submitted outside the app are also captured (via `scontrol`) and can be re-submitted from the history screen
- **Job details** — per-job screen showing timing, resources, live sstat metrics (running jobs), exit code, and the sbatch command used to submit the job; array parent jobs list all tasks inline
- **Log viewer** — browse and read `.log`/`.err` files from a configurable log directory directly in the app
- **Cluster dashboard** — collapsible partition table showing node availability and CPU load per partition, color-coded green/yellow/red
- **Notifications** — persistent notification while a job is running (tap to open job details); replaced by a success or error notification when the job finishes; can be disabled in Settings
- **Auto-polling** — refreshes every 10 s with a countdown spinner in the toolbar; pull-to-refresh available on every screen
- **TOTP support** — computes one-time passwords automatically from a stored secret; falls back to a manual OTP dialog if no secret is configured
- **Secure credential storage** — password and TOTP seed stored exclusively in Android EncryptedSharedPreferences backed by the Android Keystore; SSH key pair generated and held in the Keystore

### nnU-Net plugin

A separate plugin APK that connects to SlurmDroid and monitors [nnU-Net](https://github.com/MIC-DKFZ/nnUNet) training on the cluster:

- **Dataset list** — all datasets discovered under `nnUNet_results`, `nnUNet_raw`, and `nnUNet_preprocessed`, shown with human-readable names parsed from the `DatasetXXX_Name` convention
- **Pipeline status** — per-dataset pipeline card showing raw data, planning, preprocessing (with progress bar), training, and postprocessing stages
- **Training progress** — per-configuration fold list with progress bars, elapsed time, and ETA; overall summary card when multiple folds are present
- **Running fold indicator** — folds whose training log was updated in the last 15 minutes are marked with a ▶ badge
- **Fold detail view** — tap any fold for a full detail screen: progress summary, Canvas loss curve (train loss + val loss over all epochs), and a scrollable per-epoch metrics table (train_loss / val_loss / mean Pseudo dice)

## Screenshots

<img alt="Screenshot of the dashboard" src="/assets/screenshots/screenshot_dashboard.png" width="300"/>
<img alt="Screenshot of job list" src="/assets/screenshots/screenshot_job_history.png" width="300"/>
<img alt="Screenshot of the job details" src="/assets/screenshots/screenshot_job_details.png" width="300"/>
<img alt="Screenshot of the job log view" src="/assets/screenshots/screenshot_log.png" width="300"/>
<img alt="Screenshot of the settings" src="/assets/screenshots/screenshot_settings.png" width="300"/>

## Requirements

- Android 8.0+ (API 26)
- SSH access to a Slurm cluster with keyboard-interactive authentication (OTP + password)

## Setup

### SlurmDroid

1. Install the app and open **Settings**
2. Enter your cluster hostname, port, and username
3. Enter your password
4. Either scan your TOTP QR code or paste the Base32 secret manually
5. Optionally generate an SSH key pair and add the public key to `~/.ssh/authorized_keys` on the cluster for faster reconnects
6. Set a log directory (default: `~/slurm_logs`) if your scripts write output files there
7. Tap **Test Connection** to verify

Credentials are saved automatically as you type.

### nnU-Net plugin

1. Install the `nnunet-plugin` APK alongside SlurmDroid
2. In SlurmDroid → **Settings**, enable the nnU-Net plugin and set your `nnUNet_base_dir` (or configure `nnUNet_results`, `nnUNet_raw`, and `nnUNet_preprocessed` individually if you use non-standard paths)
3. Open the nnU-Net app — it connects to SlurmDroid automatically and starts monitoring on the next poll

The plugin respects the standard `nnUNet_results`, `nnUNet_raw`, and `nnUNet_preprocessed` environment variables if the base directory is left blank.

## Architecture

```
app/src/main/java/org/slurmdroid/
├── core/
│   ├── ssh/          # SshManager, SshAuthHandler, CommandExecutor, credential store
│   ├── feature/      # ServerFeature interface + FeatureRegistry
│   ├── notifications/ # JobNotificationManager
│   └── db/           # Room database
├── features/
│   └── slurm/        # Parser, repository, ViewModels, Screens
├── service/          # SshForegroundService — persistent SSH session + polling loop
└── ui/               # MainActivity, AppNavigation, Dashboard, Settings

plugin-api/           # Shared AIDL interface + UI helpers (included in both APKs)

nnunet-plugin/        # Standalone APK — nnU-Net monitoring
└── src/main/java/org/slurmdroid/nnunet/
    ├── data/         # NnUNetLogParser
    ├── domain/       # NnUNetWorkflow, FoldProgress, EpochMetrics, …
    └── ui/           # DatasetListScreen, DatasetDetailScreen, ProgressScreen, FoldDetailScreen
```

**Plugin system:** plugins are separate APKs that communicate with SlurmDroid over AIDL. SlurmDroid exposes an `ICommandBridge` interface so plugins can run SSH commands via the existing connection; plugins expose an `ISlurmDroidPlugin` interface so SlurmDroid can forward poll ticks and settings. Plugins are auto-discovered by package name, bound on install/update, and can be enabled or disabled per-plugin in Settings.

**SSH access** is centralized in a single `CommandExecutor`; no feature or plugin interacts with JSch directly.

**Job capture:** `scontrol show job` output is stored for every job seen in `squeue` and for recently-completed jobs from `sacct`, so the `SubmitLine` is available for re-submission even for jobs that were never submitted through the app.

## Tech stack

| Layer           | Library                                       |
|-----------------|:----------------------------------------------|
| UI              | Jetpack Compose + Material 3                  |
| Navigation      | Navigation Compose                            |
| DI              | Hilt                                          |
| SSH             | JSch                                          |
| TOTP            | java-otp                                      |
| Local DB        | Room                                          |
| Secure storage  | EncryptedSharedPreferences + Android Keystore |
| Background work | WorkManager + Foreground Service              |
| Cross-process   | AIDL                                          |

## Building

```bash
# Main app
./gradlew :app:assembleDebug

# nnU-Net plugin APK
./gradlew :nnunet-plugin:assembleDebug
```

Requires Android SDK with API 35 build tools. No API keys or secrets needed for a local build.

## License

This project is licensed under the GNU General Public License v3.0 — see the [LICENSE](LICENSE) file for details.
