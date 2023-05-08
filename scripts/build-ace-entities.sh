#!/bin/bash

# Build ace-entities


# Fail script with error if any command fails
set -e

## If indicated install Mysql server
if [ "$1" = "--with-mysql" ]
then
  echo "mysql-server mysql-server/root_password password root" | sudo debconf-set-selections
  echo "mysql-server mysql-server/root_password_again password root" | sudo debconf-set-selections
  sudo apt-get -y install mysql-server
  sudo systemctl start mysql.service
  echo "root" > AceAS/db.pwd # Root username
  echo "root" >> AceAS/db.pwd # Root pw
fi

# Run mvn on ace-entities
# https://stackoverflow.com/questions/65092032/maven-build-failed-but-exit-code-is-still-0
echo "*** Building and packaging ACE entities ***"
# mvn clean org.jacoco:jacoco-maven-plugin:0.8.6:prepare-agent install org.jacoco:jacoco-maven-plugin:0.8.6:report | tee mvn_res
mvn -e clean package | tee mvn_res
if grep 'BUILD FAILURE' mvn_res;then exit 1; fi;
if grep 'BUILD SUCCESS' mvn_res;then exit 0; else exit 1; fi;
rm mvn_res

if [ "$1" = "--with-mysql" ]
then
mv AceAS/db.pwd apps/AceAS
fi