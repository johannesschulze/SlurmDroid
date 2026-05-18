# SlurmDroid – Vollständige Projektspezifikation für Claude Code

## Überblick

Native Android App (Kotlin, Jetpack Compose, MVVM) zur Überwachung und Steuerung eines Slurm-Clusters via SSH. Die Architektur ist von Anfang an auf Erweiterbarkeit ausgelegt – Slurm ist das erste von mehreren geplanten Server-Features (später z.B. nnU-Net-Monitoring).

---

## Architekturprinzipien

- **MVVM** mit StateFlow/ViewModel
- **Einziger SSH-Zugriffspunkt:** `CommandExecutor` – kein Feature greift direkt auf JSch zu
- **Feature-Module:** Jedes Server-Feature (Slurm, später nnU-Net) implementiert das `ServerFeature`-Interface und registriert sich in der `FeatureRegistry`
- **Navigation und Dashboard** sind dynamisch – sie iterieren über die `FeatureRegistry`, kein Hardcoding
- **Room** als lokale Datenbank; Features registrieren eigene Entities

---

## Authentifizierung

- **SSH-Bibliothek:** JSch
- **Methode:** Keyboard-Interactive Authentication
- **Prompt-Reihenfolge auf dem Cluster:** erst OTP, dann Passwort
- **TOTP:** wird in der App berechnet (`com.eatthepath:java-otp`), Seed per QR-Code (`otpauth://totp/...`-Format) oder manuell eingeben
- **Reconnect-Strategie:** zuerst Key-Auth versuchen, bei Fehler erneut OTP+Passwort
- **Sichere Speicherung:** TOTP-Seed, Passwort und SSH-Key ausschließlich über Android Keystore / EncryptedSharedPreferences – niemals im Klartext

---

## Projektstruktur

```
app/src/main/java/com/example/slurmdroid/
├── core/
│   ├── ssh/
│   │   ├── SshManager.kt             # JSch Session-Management, Reconnect-Logik
│   │   ├── SshAuthHandler.kt         # Keyboard-Interactive: OTP-Prompt → TOTP, Passwort-Prompt → Keystore
│   │   └── CommandExecutor.kt        # execute(command): Result<String> — einziger SSH-Zugriffspunkt
│   ├── feature/
│   │   ├── ServerFeature.kt          # Interface (siehe unten)
│   │   └── FeatureRegistry.kt        # Liste aktiver Features; initial: [SlurmFeature]
│   └── db/
│       └── AppDatabase.kt            # Gemeinsame Room-DB, alle Feature-Entities hier registrieren
│
├── features/
│   ├── slurm/
│   │   ├── data/
│   │   │   ├── SinfoParser.kt        # sinfo-Output → List<Partition>
│   │   │   ├── SqueueParser.kt       # squeue-Output → List<SlurmJob>
│   │   │   └── SlurmRepository.kt    # Koordiniert CommandExecutor + Parser + Room
│   │   ├── domain/
│   │   │   ├── Partition.kt
│   │   │   ├── SlurmJob.kt
│   │   │   └── JobTemplate.kt        # Gespeicherter sbatch-Befehl für Re-Submit
│   │   ├── ui/
│   │   │   ├── dashboard/
│   │   │   │   ├── SlurmDashboardCard.kt   # Partition-Karten + kompakte Jobliste
│   │   │   │   └── SlurmDashboardViewModel.kt
│   │   │   ├── jobs/
│   │   │   │   ├── JobsScreen.kt           # Detaillierte Jobliste, Fortschrittsbalken, Swipe-to-cancel
│   │   │   │   └── JobsViewModel.kt
│   │   │   └── history/
│   │   │       ├── HistoryScreen.kt        # Vergangene Jobs, Re-Submit-Dialog
│   │   │       └── HistoryViewModel.kt
│   │   └── SlurmFeature.kt           # Implementiert ServerFeature
│   │
│   └── nnunet/                       # Platzhalter – noch nicht implementieren
│       └── NnUNetFeature.kt
│
├── ui/
│   ├── main/
│   │   ├── MainActivity.kt
│   │   └── AppNavigation.kt          # Routen werden dynamisch aus FeatureRegistry bezogen
│   ├── dashboard/
│   │   └── MainDashboardScreen.kt    # Iteriert über Features, zeigt deren DashboardCard()
│   └── settings/
│       ├── SettingsScreen.kt         # SSH-Einstellungen + pro Feature eine SettingsSection()
│       └── SettingsViewModel.kt
│
└── service/
    └── SshForegroundService.kt       # Persistente SSH-Session, Polling-Loop über alle Features
```

---

## ServerFeature-Interface

```kotlin
interface ServerFeature {
    val featureId: String
    val displayName: String
    val icon: ImageVector

    fun provideRoutes(): List<FeatureRoute>

    @Composable
    fun DashboardCard()

    suspend fun poll(executor: CommandExecutor)

    @Composable
    fun SettingsSection() {}
}
```

---

## Slurm-Befehle

| Zweck | Befehl |
|---|---|
| Partitionen | `sinfo -o "%P\|%a\|%D\|%T\|%C" --noheader` |
| Eigene Jobs | `squeue -u <user> -o "%i\|%j\|%T\|%M\|%l\|%R\|%P" --noheader` |
| Job submittieren | `sbatch <befehl>` |
| Job abbrechen | `scancel <jobid>` |

---

## Polling-Strategie

- **Normalbetrieb:** 60-Sekunden-Intervall via WorkManager
- **Nach Aktivität** (Submit/Cancel): sofortiger Refresh, dann Backoff `[0, 5, 10, 20, 60]` Sekunden bis Status stabil
- **Manuell:** Pull-to-Refresh auf allen Screens
- **Implementierung:** `SshForegroundService` iteriert über `FeatureRegistry` und ruft `feature.poll(executor)` auf

---

## Room-Datenbank

**`job_history`**

| Feld | Typ |
|---|---|
| id | Long (PK, autoGenerate) |
| timestamp | Long |
| jobName | String |
| fullCommand | String (vollständiger sbatch-Befehl) |
| partition | String |
| lastKnownStatus | String |
| slurmJobId | String? (nullable) |

**`ssh_profiles`** *(für spätere Mehrserver-Unterstützung vorbereitet)*

| Feld | Typ |
|---|---|
| id | Long (PK, autoGenerate) |
| hostname | String |
| port | Int |
| username | String |
| keyAlias | String (Verweis auf Keystore-Eintrag) |

---

## UI-Verhalten

### Partitions-Karten (Dashboard)
- Farbcodierung: grün (Knoten verfügbar) / gelb (teilweise ausgelastet) / rot (voll/offline)
- Anzeige: Partitionsname, verfügbare/gesamt Knoten, CPU-Auslastung

### Jobliste
- **RUNNING:** Fortschrittsbalken (bisherige Laufzeit vs. Zeitlimit)
- **PENDING:** Wartegrund aus `%R`-Feld anzeigen
- **Swipe-to-cancel** mit Bestätigungsdialog
- **FAB** zum Starten eines neuen Jobs aus der History

### Job-History & Re-Submit
- Liste aller submitted Jobs aus Room
- „Erneut starten"-Button öffnet Edit-Dialog mit vorausgefülltem `sbatch`-Befehl
- Nach Bestätigung: Submit + in `job_history` speichern + Backoff-Polling starten

### Einstellungen
- SSH-Hostname, Port, Benutzername
- SSH-Key importieren oder generieren
- TOTP-Seed: QR-Code scannen (ZXing) oder manuell eingeben
- Verbindungstest-Button

---

## Abhängigkeiten (build.gradle)

```groovy
// SSH
implementation 'com.jcraft:jsch:0.1.55'

// TOTP
implementation 'com.eatthepath:java-otp:0.4.0'

// QR-Code
implementation 'com.google.zxing:core:3.5.2'
implementation 'androidx.camera:camera-camera2:1.3.x'
implementation 'androidx.camera:camera-lifecycle:1.3.x'
implementation 'androidx.camera:camera-view:1.3.x'

// Room
implementation 'androidx.room:room-runtime:2.6.x'
implementation 'androidx.room:room-ktx:2.6.x'
kapt 'androidx.room:room-compiler:2.6.x'

// Security / Keystore
implementation 'androidx.security:security-crypto:1.1.0-alpha06'

// Compose + Navigation + Material3
implementation platform('androidx.compose:compose-bom:2024.x')
implementation 'androidx.compose.material3:material3'
implementation 'androidx.compose.material:material-icons-extended'
implementation 'androidx.navigation:navigation-compose:2.7.x'

// ViewModel + Lifecycle
implementation 'androidx.lifecycle:lifecycle-viewmodel-compose:2.7.x'
implementation 'androidx.lifecycle:lifecycle-runtime-compose:2.7.x'

// WorkManager
implementation 'androidx.work:work-runtime-ktx:2.9.x'

// Hilt (Dependency Injection – empfohlen für Feature-Architektur)
implementation 'com.google.dagger:hilt-android:2.x'
kapt 'com.google.dagger:hilt-compiler:2.x'
implementation 'androidx.hilt:hilt-navigation-compose:1.x'
implementation 'androidx.hilt:hilt-work:1.x'
```

---

## Implementierungsreihenfolge für Claude Code

1. **Projektgerüst** – Gradle-Setup, Hilt, Room, Paketstruktur
2. **`ServerFeature`-Interface + `FeatureRegistry`** – Erweiterbarkeit von Anfang an
3. **`SshManager` + `SshAuthHandler`** – JSch Keyboard-Interactive mit OTP-erst-dann-Passwort-Logik; isoliert gegen echten Server testen
4. **`CommandExecutor`** – generischer `execute()`-Wrapper mit `sealed class Result`
5. **`SshForegroundService`** – persistente Session, Polling-Loop
6. **Slurm-Parser** – `SinfoParser`, `SqueueParser`; mit Beispiel-Output testen
7. **`SlurmRepository` + Room-Entities**
8. **UI: Settings** – SSH + TOTP-Einrichtung zuerst, da alles andere davon abhängt
9. **UI: Dashboard + Jobs + History**
10. **Backoff-Polling-Logik** nach Submit/Cancel
11. **`NnUNetFeature`-Platzhalter** eintragen (leere Implementierung, nicht aktiviert)

---

## Kritische Implementierungshinweise

- **JSch Keyboard-Interactive:** `UserInfo`-Interface implementieren; Prompt-String parsen um OTP- vs. Passwort-Prompt zu unterscheiden – Prompt-Text des Clusters vorher einmal manuell prüfen und als Konstante hinterlegen
- **TOTP:** Seed niemals im Klartext speichern; OTP wird zur Laufzeit berechnet, nie persistiert
- **Fehlermodellierung:** `sealed class Result<T> { Success, AuthError, ConnectionError, ParseError, UnknownError }` – alle Schichten nutzen diesen Typ, UI reagiert gezielt
- **Foreground Service:** benötigt persistente Notification; sauber disconnecten in `onDestroy`
- **Room + neues Feature:** einziger globaler Eingriff beim Hinzufügen eines Features – neue Entity in `AppDatabase` registrieren und Schema-Version erhöhen
- **nnU-Net später:** wird `nvidia-smi`, `tail` auf Logdateien und `ps`-Output brauchen – alles über `CommandExecutor` abbildbar, kein Architektureingriff nötig