# Docker Setup Guide — seat-reserve (Windows)

This walks through installing Docker Desktop on Windows and getting it ready
to run this project's Postgres + Redis containers and Testcontainers-based
tests.

---

## 1. Check system requirements

Docker Desktop on Windows needs **WSL2** (Windows Subsystem for Linux 2).
Most modern Windows 10/11 installs already support this.

Open PowerShell and check your Windows version:
```powershell
winver
```
You need **Windows 10 version 2004+ (Build 19041+)** or **Windows 11** (any build).

---

## 2. Install WSL2

Open PowerShell **as Administrator** and run:
```powershell
wsl --install
```
This installs WSL2 and a default Linux distribution (Ubuntu). If it asks you
to restart your machine, do so.

After restart, verify WSL2 is active:
```powershell
wsl --status
```
It should show `Default Version: 2`. If it shows version 1, run:
```powershell
wsl --set-default-version 2
```

> If `wsl --install` fails or the command isn't recognized, your Windows
> build may be older — go to **Settings → Windows Update** and update fully,
> then retry.

---

## 3. Download and install Docker Desktop

1. Go to https://www.docker.com/products/docker-desktop/
2. Download **Docker Desktop for Windows**
3. Run the installer
4. During install, make sure **"Use WSL 2 instead of Hyper-V"** is checked
   (this is the default on modern installers)
5. Let it finish, then restart your machine if prompted

---

## 4. Start Docker Desktop

1. Open Docker Desktop from the Start menu
2. Wait for the whale icon in your system tray to stop animating — a
   steady (non-spinning) icon means the engine is up
3. You may be asked to accept a service agreement on first launch — accept it
4. If prompted to sign in, you can skip this (a Docker account is optional
   for local use)

---

## 5. Verify the installation

Open a **new** terminal (PowerShell or cmd) and run each of these in order:

```powershell
docker --version
```
Should print something like `Docker version 27.x.x`.

```powershell
docker info
```
Should print a large block of server/client details with no errors. If this
errors with something like "cannot connect to the Docker daemon," Docker
Desktop isn't fully started yet — wait 30 seconds and retry.

```powershell
docker run hello-world
```
This pulls a tiny test image and should print:
```
Hello from Docker!
This message shows that your installation appears to be working correctly.
```

If all three succeed, Docker is correctly installed and running.

---

## 6. Bring up this project's containers

From the project root (`seatreserve/`):
```powershell
docker-compose up -d
```

Check both containers are healthy:
```powershell
docker ps
```
You should see two rows:
- `seatreserve-postgres` — status should say `(healthy)`
- `seatreserve-redis` — status should say `(healthy)`

If a container shows as `Exited` or unhealthy, check its logs:
```powershell
docker logs seatreserve-postgres
docker logs seatreserve-redis
```

---

## 7. Run the full build (including Testcontainers tests)

Now that Docker is running:
```powershell
mvn clean install -U
```
`ConcurrentSeatHoldTest` will pull `postgres:16-alpine` via Testcontainers
(separate from your docker-compose Postgres) on first run — this can take a
minute or two depending on your connection. Subsequent runs will be fast
since the image is cached locally.

---

## 8. Common issues

| Symptom | Fix |
|---|---|
| `wsl --install` says command not recognized | Update Windows fully via Settings → Windows Update, then retry |
| Docker Desktop stuck on "Starting..." | Restart the machine; if it persists, reinstall WSL2 kernel update from https://aka.ms/wsl2kernel |
| `docker info` fails after restart | Docker Desktop doesn't always auto-start — open it manually from the Start menu each session, or enable "Start Docker Desktop when you sign in" in its settings |
| Port `5432` or `6379` already in use | You likely have a native Postgres/Redis install running locally too. Stop those services, or change the exposed ports in `docker-compose.yml` |
| `docker-compose` command not found | Recent Docker Desktop versions use `docker compose` (no hyphen) — try that instead |

---

## 9. Stopping containers when you're done

```powershell
docker-compose down
```
This stops and removes the containers but keeps your Postgres data (stored
in the `pgdata` volume) for next time. To wipe the data too:
```powershell
docker-compose down -v
```
