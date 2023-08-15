# Clock Panda
Clock Panda is an open source implementation of a schedule management and optimization system.

The goal is to allow users to connect their calendar and let Clock Panda automatically optimize their schedule by creating blocks for lunch, dinner, breaks and focus time.

---
# Planned features and supported calendar platforms
## Features
- [in progress] Local personal usage
- [planned] Self-hosting
- [in progress] Automatically optimize and schedule focus times
- [planned] Automatically optimize and schedule lunch/dinner breaks
- [planned] Prioritized TODO list
- [planned] Analytics
- [planned] Team schedule optimization

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
- [opex]: for commits improving operational excellence e.g. security, logging, performance, monitoring
- [codeclean]: for commits cleaning up code e.g. linting, variable renames
- [doc]: for commits updating documentation
## Testing before pushing
### Test suite
You can build and run the full test suite using `make build`

You can build and run a particular subproject using `./gradlew :<replace with subproject name>:check` e.g. `./gradlew :service-authnz:check`
### Local manual test
You will first need to setup your own Google Calendar API credentials and add them to your local environment:
1. Go to https://console.cloud.google.com/apis/credentials
2. Create a project if needed
3. Setup an OAuth consent screen and add your email as a test user
4. Ask for the following 2 scopes 
 - auth/calendar.readonly
 - auth/calendar.events.owned
5. In the Credentials tab, create an OAuth client ID
6. Copy the client id and client secret in an `.env` file in `app/src/main/resources` (Use `.env.example` as a template)

You can start a local Clock Panda instance using `make bootRun` and open `http://localhost:8001/` in your browser

If you need to connect a debugger to the Clock Panda instance, you can start it using `make bootRunDebug` and connect to the debug port

Clock Panda is configured to run the schedule optimizer only every 30 minutes which is not ideal for testing the optimizer. 
You can use the `OptimizationCronJobPlayground` test class to more rapidly iterate and get feedback. 
