# qa-evaluation-report Specification

## Purpose
TBD - created by archiving change qa-evaluation-redesign. Update Purpose after archive.
## Requirements
### Requirement: Reporter SHALL generate Markdown report after all tests complete

The system SHALL generate a complete Markdown report at `docs/test-execution-report.md` after all QA pairs are evaluated.

#### Scenario: Report generation after test completion
- **WHEN** all QA pairs have been evaluated
- **THEN** a Markdown report is written to `docs/test-execution-report.md`

### Requirement: Report SHALL include summary statistics

The report SHALL include a summary table showing pass/fail counts.

#### Scenario: Summary table format
- **WHEN** report is generated
- **THEN** report contains a table with columns: 状态, 数量
- **AND** rows for ✅ 通过 and ❌ 失败 with counts

### Requirement: Report SHALL include detailed results per QA pair

The report SHALL include a detailed section for each QA pair with: input, expected, actual output, and evaluation result.

#### Scenario: Each QA pair has detailed section
- **WHEN** report is generated
- **THEN** each QA pair section includes:
  - **输入:** original question
  - **预期:** expected description
  - **实际输出:** actual Agent output (in code block)
  - **评估结果:** pass/fail status with reason

#### Scenario: Pass status formatting
- **WHEN** a QA pair passes evaluation
- **THEN** section header shows ✅ and evaluation result shows "✅ 通过 — {reason}"

#### Scenario: Fail status formatting
- **WHEN** a QA pair fails evaluation
- **THEN** section header shows ❌ and evaluation result shows "❌ 失败 — {reason}"

### Requirement: Report SHALL include execution timestamp

The report SHALL include the execution time in the header.

#### Scenario: Timestamp in report header
- **WHEN** report is generated
- **THEN** report header includes: > 执行时间: YYYY-MM-DD HH:mm:ss

