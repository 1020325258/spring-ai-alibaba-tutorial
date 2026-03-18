#!/bin/bash

export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home

mvn test -Dtest="ObservabilityAspectAnnotationTest,ToolExecutionTemplateTest,ToolResultTest,QueryScopeTest,EntityRegistryTest,OntologyQueryEngineTest" -pl 05-SREmate
