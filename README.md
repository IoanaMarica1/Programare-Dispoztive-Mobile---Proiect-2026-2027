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
copy secrets.properties.example secrets.properties   # then put your Gemini key in it
docker compose up -d                                 # Postgres on localhost:5434
.\mvnw.cmd spring-boot:run                           # API on localhost:8080
```

Run `mvnw` **from `backend/`**, otherwise `secrets.properties` is not found.

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
- First trip plan for a new city takes ~10 s (place lookup via OpenStreetMap), later ones ~2 s.
- Never commit `secrets.properties` or `.env` — check `git status` before committing.
