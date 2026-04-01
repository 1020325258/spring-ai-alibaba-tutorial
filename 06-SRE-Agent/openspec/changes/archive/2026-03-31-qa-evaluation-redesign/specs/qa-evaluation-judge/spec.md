## ADDED Requirements

### Requirement: Judge SHALL evaluate Agent output semantically

The system SHALL use an LLM (Qwen-Turbo) to evaluate whether the actual output meets the expected natural language description.

#### Scenario: Output matches expected semantically
- **WHEN** actual output contains all core information described in expected
- **THEN** Judge returns `{pass: true, reason: "..."}`

#### Scenario: Output missing required information
- **WHEN** actual output is missing information explicitly required by expected
- **THEN** Judge returns `{pass: false, reason: "..."}`

### Requirement: Judge SHALL use temperature=0 for deterministic output

The system SHALL call Qwen-Turbo with temperature=0 to ensure stable evaluation results.

#### Scenario: Consistent evaluation across runs
- **WHEN** the same input is evaluated multiple times
- **THEN** Judge returns consistent pass/fail result

### Requirement: Judge SHALL output structured JSON

The system SHALL return evaluation results in strict JSON format: `{"pass": boolean, "reason": string}`.

#### Scenario: Valid JSON output
- **WHEN** Judge completes evaluation
- **THEN** output is parseable JSON with `pass` (boolean) and `reason` (string, max 30 chars)

#### Scenario: Invalid JSON handling
- **WHEN** LLM returns non-JSON response
- **THEN** system throws EvaluationException with original response

### Requirement: Judge SHALL use specific evaluation prompt

The system SHALL use a predefined prompt template for evaluation that emphasizes semantic matching over exact wording.

#### Scenario: Prompt includes all context
- **WHEN** Judge is called
- **THEN** prompt includes: question, expected, actualOutput, judgment rules
