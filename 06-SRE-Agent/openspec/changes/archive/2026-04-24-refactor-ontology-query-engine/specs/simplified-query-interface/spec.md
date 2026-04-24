## ADDED Requirements

### Requirement: Query engine exposes two string-based methods only

The query engine SHALL expose exactly two public query methods, both accepting string parameters:
- `query(String entityName, String value, String queryScope)` for basic queries
- `query(String entityName, String value, String queryScope, Map<String, String> extraParams)` for queries requiring additional parameters

#### Scenario: Basic query without extra parameters
- **WHEN** caller invokes `query("Order", "825123110000002753", "Contract")`
- **THEN** system executes query and returns result map

#### Scenario: Query with extra parameters
- **WHEN** caller invokes `query("Order", "825123110000002753", "PersonalQuote", Map.of("bindType", "1"))`
- **THEN** system passes extraParams to gateway and returns result map

#### Scenario: Convenience overload delegates to full method
- **WHEN** caller invokes `query(entityName, value, queryScope)` without extraParams
- **THEN** system internally calls `query(entityName, value, queryScope, null)`

### Requirement: Enum-based query methods are removed

The query engine SHALL NOT expose enum-based query methods `query(String, String, QueryScope)` or `query(String, String, QueryScope, Map)`.

#### Scenario: Enum conversion handled internally
- **WHEN** caller passes queryScope string "list" or "default"
- **THEN** system internally converts to QueryScope enum and processes correctly

#### Scenario: Custom entity names supported
- **WHEN** caller passes queryScope string "ContractNode,PersonalQuote" (not in QueryScope enum)
- **THEN** system processes as comma-separated target entities without enum conversion

## REMOVED Requirements

### Requirement: Query engine exposes enum-based methods
**Reason**: Redundant with string-based methods; internal enum conversion already handles all cases
**Migration**: Replace `query(entityName, value, QueryScope.CONTRACT)` with `query(entityName, value, "Contract")`
