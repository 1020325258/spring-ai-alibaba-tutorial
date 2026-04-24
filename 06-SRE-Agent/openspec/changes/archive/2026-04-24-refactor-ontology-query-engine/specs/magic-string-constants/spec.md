## ADDED Requirements

### Requirement: Magic strings are replaced with class constants

The query engine SHALL define private static final constants for all magic strings used in query logic.

#### Scenario: Scope constants defined
- **WHEN** code references scope values
- **THEN** system uses `SCOPE_DEFAULT`, `SCOPE_LIST` constants instead of "default", "list" literals

#### Scenario: Result key constants defined
- **WHEN** code builds result map
- **THEN** system uses `KEY_QUERY_ENTITY`, `KEY_QUERY_VALUE`, `KEY_RECORDS` constants instead of string literals

#### Scenario: Constants improve refactoring safety
- **WHEN** developer needs to change a key name
- **THEN** system allows single-point change at constant definition, preventing inconsistencies

### Requirement: Method names follow consistent naming convention

The query engine SHALL rename methods to follow consistent "queryWith*" naming pattern:
- `queryListOnly` becomes `queryWithoutExpansion`
- `queryWithScopeString` becomes `queryWithExpansion`

#### Scenario: Without expansion method name clarity
- **WHEN** developer reads `queryWithoutExpansion` method signature
- **THEN** method name clearly indicates it returns only start entity without expanding relations

#### Scenario: With expansion method name clarity
- **WHEN** developer reads `queryWithExpansion` method signature
- **THEN** method name clearly indicates it expands relations along paths

### Requirement: Path plan logging uses Stream API

The query engine SHALL refactor `logPathPlan` method to use Stream API instead of StringBuilder for path formatting.

#### Scenario: Path formatted with stream
- **WHEN** `logPathPlan("Order", paths)` is invoked
- **THEN** system uses `path.stream().map(...).collect(Collectors.joining(" "))` to format path string

#### Scenario: Duplicate if-else branch removed
- **WHEN** formatting relation arrows
- **THEN** system removes redundant `if (i == 0)` branch that had identical logic to else branch

### Requirement: Unused methods are removed

The query engine SHALL remove the `attachPathResults` method that is never invoked.

#### Scenario: Dead code eliminated
- **WHEN** developer searches for `attachPathResults` usage
- **THEN** method does not exist in codebase (removed during refactoring)
