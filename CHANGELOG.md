# Changelog

## [1.1.0] - 2024-10-23

### Added
- Use Testcontainers to test interoperability with Locust master (#4)
- Shutdown Worker when Master stops responding (#6)

### Fixed
- Cancel worker tasks after Quit message from controller (#4)

### Changed
- Refactor to use structured logging (#7)
- Remove unnecessary dependencies (#7)

## [1.0.0] - 2024-09-23

Initial Release!

Published to Maven central.

https://central.sonatype.com/artifact/com.onepeloton.locust4k/locust4k
