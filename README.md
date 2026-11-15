# Wanderly

AI travel companion — Android app (Java) + Spring Boot backend.

- `android/` — Android app (Java, XML layouts, Retrofit, MapLibre)
- `backend/` — Spring Boot, Java 21, Postgres 17 (Docker), Flyway, JWT

## Run locally

### 1. Backend

Requirements: **JDK 21** (newer JDKs break Lombok), Docker Desktop.

```powershell
cd backend
copy .env.example .env
copy secrets.properties.example secrets.properties   # then fill it in (see below)
docker compose up -d                                 # Postgres on localhost:5434
.\mvnw.cmd spring-boot:run                           # API on localhost:8080
```

Run `mvnw` **from `backend/`**, otherwise `secrets.properties` is not found.

`secrets.properties` holds everything that must not be in git:

- `gemini.api-key`: free key from https://aistudio.google.com/apikey
- `jwt.secret`: any random string of 32+ characters, e.g. `python -c "import secrets; print(secrets.token_urlsafe(48))"`.
  The backend refuses to start without it. Changing it logs everyone out.
- `spring.datasource.username` / `password`: the same values as `POSTGRES_USER` / `POSTGRES_PASSWORD` in `.env`

Postgres listens only on `127.0.0.1:5434`, not on the local network.

If the build fails with `ExceptionInInitializerError ... EndPosTable`, point the terminal to JDK 21:

```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-21.x.x"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
```

### 2. Android app

Open `android/` in **Android Studio** and Run.

- **Emulator:** works out of the box (backend URL defaults to `http://10.0.2.2:8080/`).
- **Real phone over USB:**
  1. `adb reverse tcp:8080 tcp:8080` (redo after reconnecting the cable)
  2. Add to `~/.gradle/gradle.properties` (your user folder, **not** the repo):
     `apiUrl=http://localhost:8080/`

Do not change the default URL in `build.gradle.kts`.

## Notes

- Gemini free tier: use `gemini-3.5-flash-lite` (500 req/day); other Flash models allow only ~20/day.
- Trip plans: the first one for a new city takes ~30-40 s (place lookups via OpenStreetMap, 1 request/s);
  later ones reuse the cached places (24 h, in memory, so a restart clears them) and take ~15-25 s, mostly Gemini.
- Check-ins (`POST /api/users/visits`) send the traveler's GPS position; the backend finds the objective on
  OpenStreetMap and only accepts the visit within 150 m of it.
- Never commit `secrets.properties` or `.env` — check `git status` before committing.
