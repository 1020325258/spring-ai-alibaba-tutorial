## MODIFIED Requirements

### Requirement: Streaming output behavior

The frontend SHALL update the UI immediately upon receiving each SSE event, without character accumulation delays.

#### Scenario: Immediate UI update on SSE event
- **WHEN** an SSE event is received from the backend
- **THEN** the frontend SHALL immediately update the message content
- **AND** the pending content buffer SHALL be cleared

#### Scenario: Reduced markdown parsing frequency
- **WHEN** multiple SSE events are received in rapid succession
- **THEN** the frontend SHALL parse markdown to nodes every 5 updates
- **AND** the final parse SHALL occur when streaming completes
