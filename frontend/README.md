# Sudar Chits - Frontend (React + Vite)

Works on phones (cards, bottom menu, bottom-sheet forms) and on desktop (sidebar, table).

## One-time setup
Install Node.js (LTS), then in this folder:

    npm install

## Run while developing
1. Start the Spring Boot project (port 8080).
2. In this folder run:

       npm run dev

3. Open http://localhost:5173 (on a phone: http://<your-pc-ip>:5173 on the same Wi-Fi).
   Calls to /api are forwarded to Spring Boot automatically.

## Build into the Spring Boot project
    npm run build

This writes the app into `src/main/resources/static`. After restarting Spring Boot,
open http://localhost:8080 - one project serves both the API and the UI.

## Sign in (temporary)
Enter a user ID (admin = 1). The ID is sent as the `X-User-Id` header.
Username/password login comes later.
