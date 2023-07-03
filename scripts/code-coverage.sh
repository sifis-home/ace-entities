#!/bin/bash

# Compile Jacoco code coverage reports
echo "*** Collecting Jacoco code coverage reports ***"

if [[ -d AceAS/target/site/jacoco ]]
then
    mkdir -p jacoco/AceAS
    cp -r AceAS/target/site/jacoco/* jacoco/AceAS/
fi

if [[ -d AceRS/target/site/jacoco ]]
then
    mkdir -p jacoco/AceRS
    cp -r AceRS/target/site/jacoco/* jacoco/AceRS/
fi

if [[ -d AceClient/target/site/jacoco ]]
then
    mkdir -p jacoco/AceClient
    cp -r AceClient/target/site/jacoco/* jacoco/AceClient/
fi

if [[ -d AceClientDht/target/site/jacoco ]]
then
    mkdir -p jacoco/AceClientDht
    cp -r AceClientDht/target/site/jacoco/* jacoco/AceClientDht/
fi

if [[ -d utils/target/site/jacoco ]]
then
    mkdir -p jacoco/utils
    cp -r utils/target/site/jacoco/* jacoco/utils/
fi