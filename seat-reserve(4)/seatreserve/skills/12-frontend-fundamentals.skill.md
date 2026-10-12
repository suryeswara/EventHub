# Skill: Frontend Fundamentals (HTML/CSS/JS/Fetch)

## Why this project needs it
The seat map UI is what makes the concurrency guarantee *visible* — a
recruiter clicking a seat and seeing the "someone just took that seat"
conflict message is a far stronger demo than reading a test file.

## Core concepts to know
- **The Fetch API**: `fetch('/api/v1/events/1/seats')` — the browser's
  built-in way to make HTTP requests to your backend. Returns a
  `Promise`, so it's used with `async`/`await` throughout `index.html`.
- **`async`/`await`**: makes asynchronous code (things that take time,
  like a network request) read top-to-bottom like synchronous code,
  instead of nesting callbacks.
- **DOM manipulation**: `document.createElement`, `.appendChild`,
  `.classList.add` — building and updating the seat grid dynamically in
  JavaScript rather than hardcoding every seat in the HTML.
- **Event-driven UI**: `btn.addEventListener('click', ...)` — the UI
  reacts to user actions rather than following a fixed script.
- **Graceful degradation**: the `index.html` file tries the real API
  first and silently falls back to an in-memory demo if the backend
  isn't reachable — a defensive pattern worth recognizing, not just
  using.
- **CSS custom properties (`--gold`, `--coral`, etc.)**: centralizing
  the color palette as variables in `:root` so the whole design can be
  restyled by changing a handful of values.

## Where it shows up in seat-reserve
- `src/main/resources/static/index.html` — the whole frontend
- Served automatically by Spring Boot's static resource handling —
  no separate frontend server needed

## What to practice next
Open `index.html` and trace exactly what happens, function by
function, from the moment you click an available seat to the moment
the ticket stub panel appears. Being able to narrate that flow is the
actual skill — not memorizing DOM API method names.
