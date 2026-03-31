## 1. Router Bug Fix

- [ ] 1.1 Fix RouterNode routeByLLM() to map LLM output to node names (query → queryAgent, investigate → investigateAgent)
- [ ] 1.2 Fix sre-agent-qa.yaml expected description for query-order-contracts test

## 2. Frontend Streaming Output

- [ ] 2.1 Remove 50-character accumulation limit in useChat.ts
- [ ] 2.2 Update markdown parsing frequency from 10 to 5 updates

## 3. Backend Request Logging

- [ ] 3.1 Create logback-spring.xml with daily rotation (max 1 day retention)
- [ ] 3.2 Create RequestLogService for request lifecycle logging
- [ ] 3.3 Integrate RequestLogService into ChatController

## 4. Evaluation Report Enhancement

- [ ] 4.1 Remove truncation in QaEvaluationReporter.buildResultSection()
- [ ] 4.2 Add complete input/output display in code blocks
- [ ] 4.3 Add separate evaluation reasoning display

## 5. Verification

- [ ] 5.1 Run QaPairEvaluationIT to verify all tests pass
- [ ] 5.2 Test frontend streaming output behavior
- [ ] 5.3 Verify log files are created and rotated correctly
