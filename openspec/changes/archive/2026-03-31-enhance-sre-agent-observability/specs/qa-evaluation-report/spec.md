## MODIFIED Requirements

### Requirement: Evaluation report content completeness

The evaluation report SHALL display complete input, output, and evaluation reasoning without truncation.

#### Scenario: Full input display
- **WHEN** the evaluation report is generated
- **THEN** the report SHALL display the complete user question in a code block
- **AND** SHALL NOT truncate the input

#### Scenario: Full output display
- **WHEN** the evaluation report is generated
- **THEN** the report SHALL display the complete agent response in a code block
- **AND** SHALL NOT truncate the output

#### Scenario: Evaluation reasoning display
- **WHEN** the evaluation report is generated
- **THEN** the report SHALL display the complete evaluation reasoning
- **AND** the reasoning SHALL be clearly labeled as "**评估理由:**"
