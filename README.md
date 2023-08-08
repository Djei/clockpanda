# Clock Panda
Open source implementation of a schedule management and optimization system

# How to run

# How to contribute
## Code structure
This project is split into gradle subprojects.
- `app` is the main Spring application and view controllers
- `service-xxx` are modules containing business logic exposed via endpoints by the application
- `lib-xxx` are modules containing library code with no exposed endpoints by the application e.g. database access and data models

## Commit messages
Commit messages must be prefixed with one of the following tags:
- [feature]: for commits implementing new features
- [fix]: for commits fixing bugs
- [codeclean]: for commits cleaning up code e.g. linting, variable renames
- [doc]: for commits updating documentation
