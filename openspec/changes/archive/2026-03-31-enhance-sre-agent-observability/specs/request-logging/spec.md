## ADDED Requirements

### Requirement: Request log persistence

The system SHALL persist all HTTP request/response logs to the filesystem with daily rotation.

#### Scenario: Log file creation on startup
- **WHEN** the application starts
- **THEN** a log file SHALL be created at `log/sre-agent.yyyy-MM-dd.log`

#### Scenario: Daily log rotation
- **WHEN** a new day begins
- **THEN** a new log file SHALL be created with the new date
- **AND** the previous day's log file SHALL be deleted

#### Scenario: Request start logging
- **WHEN** a request is received
- **THEN** the system SHALL log the session ID and user input

#### Scenario: Request complete logging
- **WHEN** a request completes
- **THEN** the system SHALL log the full response content and total duration

#### Scenario: Error logging
- **WHEN** an error occurs during request processing
- **THEN** the system SHALL log the error message and session ID
