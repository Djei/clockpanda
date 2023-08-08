# Clock Panda
Open source implementation of a schedule management and optimization system

# How to use Clock Panda
## Local personal usage

## Self-host

# How to contribute
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
You can build and run the full test suite using `./gradlew check`

You can build and run a particular sub project using `./gradlew :<replace with subproject name>:check` e.g. `./gradlew :service-authnz:check`

### Local manual test
You can start a local Clock Panda instance using `make bootRun` and open `http://localhost:8001/` in your browser
