# Clock Panda
Clock Panda is an open source implementation of a schedule management and optimization system.

The goal is to allow users to connect their calendar and let Clock Panda automatically optimize their schedule by creating blocks for lunch, dinner, breaks and focus time.

---
# Planned features and supported calendar platforms
## Features
- Local personal usage
- Self-hosting
- Automatically optimize and schedule lunch, dinner, break and focus times based on existing calendar
## Supported calendar platforms
- Google Calendar

---
# How to use Clock Panda
## Local personal usage

## Self-host

---
# Contribution guidelines
## Code structure
This project is split into multiple gradle subprojects:
- `app` is the main Spring application and view controllers
- `service-xxx` are modules containing business logic executed via endpoints or by background processes e.g. cron jobs, daemon process
- `lib-xxx` are modules containing simply library code e.g. database access, data models
## Commit messages
Commit messages must be prefixed with one of the following tags:
- [feature]: for commits implementing new features
- [fix]: for commits fixing bugs
- [codeclean]: for commits cleaning up code e.g. linting, variable renames
- [doc]: for commits updating documentation
## Testing before pushing
### Test suite
You can build and run the full test suite using `make build`

You can build and run a particular subproject using `./gradlew :<replace with subproject name>:check` e.g. `./gradlew :service-authnz:check`
### Local manual test
You can start a local Clock Panda instance using `make bootRun` and open `http://localhost:8001/` in your browser

If you need to connect a debugger to the Clock Panda instance, you can start it using `make bootRunDebug` and connect to the debug port
