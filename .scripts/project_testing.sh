#!/usr/bin/env bash

run_mvn test --file testing/units/pom.xml
except_code
