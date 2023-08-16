# Clock Panda
Clock Panda is an open source implementation of a schedule management and optimization system.

The goal is to allow users to connect their work calendar and let Clock Panda automatically optimize their schedule by creating blocks for lunch, dinner, breaks and focus time.

---
# Planned features and supported calendar platforms
## Features
- [beta] Local personal usage
- [planned] Self-hosting
- [beta] Automatically schedule and protect focus times when you do deep work without any interruptions
- [in progress] Prioritized task list: let Clock Panda automatically schedule tasks from your task list
- [planned] Analytics
- [planned] Team schedule optimization

[planned]: This is on the roadmap but not yet started. Prioritization may change

[in progress]: This is actively being worked on

[beta]: This is available but expect you should expect bugs and potential changes

[stable]: This is available and can be considered stable

## Supported calendar platforms
- Google Calendar

---
# How to use Clock Panda
## Local personal usage
Clock Panda was designed to be hosted on a server. However, you can also run it locally for personal usage with some limitations:
- Clock Panda is configured to regularly optimize your schedule but will not be able to do so when your computer is off or asleep
- We cannot provide any security recommendations for running Clock Panda locally. You are responsible for securing your own computer and network
- While locally running Clock Panda is regularly tested, we do not explicitly design Clock Panda to work with minimal consumer grade hardware requirements
- Other potential limitations may be added as we continue to develop Clock Panda

First follow the instructions in the "How to set up your Google calendar API credentials" section

Open a terminal and run the following commands:
```
git clone git@github.com:Djei/clockpanda.git

./gradlew bootRun --args='--spring.profiles.active=local'
```
## Self-host
This feature is not yet available. Only local personal usage is supported at this moment. 

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
- [devex]: for commits improving developer experience e.g. build, test, CI/CD
- [codeclean]: for commits cleaning up code e.g. linting, variable renames
- [doc]: for commits updating documentation

Avoid using multiple tags for the same commit. The tag list is roughly ordered by priority if you need to use multiple tags.
## Testing before pushing
### Test suite
You can build and run the full test suite using `make build`

You can build and run a particular subproject using `./gradlew :<replace with subproject name>:check` e.g. `./gradlew :service-authnz:check`
### Local manual test
First follow the instructions in the "How to set up your Google calendar API credentials" section

You can start a local Clock Panda instance using `make bootRun` and open `http://localhost:8001/` in your browser

If you need to connect a debugger to the Clock Panda instance, you can start it using `make bootRunDebug` and connect to the debug port

Clock Panda is configured to run the schedule optimizer only every 30 minutes which is not ideal for testing the optimizer. 
You can use the `OptimizationCronJobPlayground` test class to more rapidly iterate and get feedback (plus you can use your favorite IDE debugger).
Alternatively, simply restarting the Clock Panda instance will also trigger an optimizer run.

---
# How to set up your Google calendar API credentials
When running locally or in a self-hosted environment, you will first need to setup your own Google Calendar API credentials and add them to your local environment:
1. Go to https://console.cloud.google.com/apis/credentials
2. Create a project if needed
3. Enable the Google Calendar API for your project
3. Setup an OAuth consent screen and add your email as a test user
4. Ask for the following 2 scopes
- auth/calendar.readonly
- auth/calendar.events.owned
5. In the Credentials tab, create an OAuth client ID
6. Add `http://localhost:8001/login/oauth2/code/google` as an authorized redirect URI
7. Copy the client id and client secret in an `.env` file in `app/src/main/resources` (Use `.env.example` as a template)