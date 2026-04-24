## ADDED Requirements

### Requirement: Start entity query logic is extracted to single method

The query engine SHALL extract start entity query logic into a single private method `queryStartEntity(String entityName, String value)` that returns `List<Map<String, Object>>`.

#### Scenario: Start entity query with logging
- **WHEN** `queryStartEntity("Order", "825123110000002753")` is invoked
- **THEN** system logs start node, executes gateway query, logs result count and timing, and returns records

#### Scenario: Reused by both expansion paths
- **WHEN** either `queryWithoutExpansion` or `queryWithExpansion` needs to query start entity
- **THEN** both methods call `queryStartEntity` instead of duplicating logic

### Requirement: Result assembly logic is extracted to single method

The query engine SHALL extract result assembly into a single private method `buildQueryResult(String entityName, String value, List<Map<String, Object>> records)` that returns `Map<String, Object>`.

#### Scenario: Result map structure
- **WHEN** `buildQueryResult("Order", "825123110000002753", records)` is invoked
- **THEN** system returns map with keys "queryEntity", "queryValue", "records"

#### Scenario: Reused by both expansion paths
- **WHEN** either `queryWithoutExpansion` or `queryWithExpansion` needs to build result
- **THEN** both methods call `buildQueryResult` instead of duplicating logic

### Requirement: Relation path building is extracted to single method

The query engine SHALL extract relation path building into a single private method `buildRelationPaths(String entityName, String queryScope)` that returns `List<List<OntologyRelation>>`.

#### Scenario: Parse comma-separated targets
- **WHEN** `buildRelationPaths("Order", "ContractNode,PersonalQuote")` is invoked
- **THEN** system splits by comma, finds path for each target, and returns list of paths

#### Scenario: Throw exception for invalid path
- **WHEN** `buildRelationPaths("Order", "NonExistentEntity")` is invoked
- **THEN** system throws IllegalArgumentException with message "找不到路径: Order -> NonExistentEntity"

### Requirement: Expansion decision is extracted to predicate method

The query engine SHALL extract expansion decision into a single private method `shouldExpandRelations(String queryScope)` that returns boolean.

#### Scenario: Null scope means no expansion
- **WHEN** `shouldExpandRelations(null)` is invoked
- **THEN** system returns false

#### Scenario: Default scope means no expansion
- **WHEN** `shouldExpandRelations("default")` is invoked
- **THEN** system returns false

#### Scenario: List scope means no expansion
- **WHEN** `shouldExpandRelations("list")` is invoked
- **THEN** system returns false

#### Scenario: Entity name means expansion
- **WHEN** `shouldExpandRelations("Contract")` is invoked
- **THEN** system returns true
