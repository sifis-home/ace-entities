#!/bin/bash

# Compile Jacoco code coverage reports
echo "*** Collecting Jacoco code coverage reports ***"

mkdir -p jacoco/AceAS
cp -r AceAS/target/site/jacoco/* jacoco/AceAS/

mkdir -p jacoco/AceRS
cp -r AceRS/target/site/jacoco/* jacoco/AceRS/

mkdir -p jacoco/AceClient
cp -r AceClient/target/site/jacoco/* jacoco/AceClient/

mkdir -p jacoco/AceClientDht
cp -r AceClientDht/target/site/jacoco/* jacoco/AceClientDht/