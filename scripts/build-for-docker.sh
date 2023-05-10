#!/bin/bash

# This script prepares Docker Dockerfiles and Contexts for the ACE entities
# If the flag --build-images is specified, it also builds the Docker images

# Fail script with error if any command fails
set -e

## Build the Jar files for the ACE entities, if needed
# the package phase will make a folder for each entity in the 'apps' folder
FILE=apps/AceAS/AceAS.jar
if [ -f "$FILE" ]; then
    echo "$FILE exists."
else
    echo "$FILE does not exist."
    echo "Building ace-entities..."
    ./scripts/build-ace-entities.sh --with-mysql
fi

## Create working directory for image building
mkdir -p docker-build/ace-entities
cd docker-build

# Copy needed files (jar files and library files)
cp -r ../apps/* ./ace-entities

## Create base Dockerfile. Initial part is same for all images.
#  Setting the timezone, otherwise the default is UTC.

echo 'FROM eclipse-temurin:8-jdk-jammy' > Dockerfile.base
echo 'WORKDIR /apps' >> Dockerfile.base
echo 'ENV TZ="Europe/Rome"' >> Dockerfile.base
echo 'RUN apt-get -y update && \' >> Dockerfile.base
echo '    apt-get install -yq tzdata && \' >> Dockerfile.base
echo '    ln -fs /usr/share/zoneinfo/Europe/Rome /etc/localtime && \' >> Dockerfile.base
echo '    dpkg-reconfigure -f noninteractive tzdata && \' >> Dockerfile.base
echo '    mkdir -p /apps/lib' >> Dockerfile.base
echo '' >> Dockerfile.base


## Prepare to build images
cd ace-entities

# Note that entrypoints must be adapted according to the location of entities, including the Servers, SQL database and DHT.
# See the docker-compose.yml for a prepared setup.

# ACE Authorization Server
# Assumes container name mysql for MySQL server
# Assumes root password xxxxxx for MySQL server
echo "root" > db.pwd
echo "xxxxxx" >> db.pwd

dockerfile=Dockerfile-AceAS
cp ../Dockerfile.base $dockerfile
echo 'RUN mkdir -p /apps/AceAS' >> $dockerfile
echo 'EXPOSE 5683/udp' >> $dockerfile
echo 'ADD db.pwd /apps/AceAS' >> $dockerfile
echo "ADD AceAS/AceAS.jar /apps/AceAS" >> $dockerfile
echo 'ADD lib /apps/lib/' >> $dockerfile
echo '' >> $dockerfile
echo 'ENTRYPOINT ["java", "-jar", "AceAS/AceAS.jar", "-D"]' >> $dockerfile

if [ "$1" == "--build-images" ]
then
  docker build -f $dockerfile -t ace-authorization-server .
fi


# ACE Resource Server
dockerfile=Dockerfile-AceRS
cp ../Dockerfile.base $dockerfile
echo 'RUN mkdir -p /apps/AceRS' >> $dockerfile
echo 'EXPOSE 5685/udp' >> $dockerfile
echo "ADD AceRS/AceRS.jar /apps/AceRS" >> $dockerfile
echo 'ADD lib /apps/lib/' >> $dockerfile
echo '' >> $dockerfile
echo 'ENTRYPOINT ["java", "-jar", "AceRS/AceRS.jar", "-o"]' >> $dockerfile

if [ "$1" == "--build-images" ]
then
  docker build -f $dockerfile -t ace-resource-server .
fi


# ACE Client
dockerfile=Dockerfile-AceClient
cp ../Dockerfile.base $dockerfile
echo 'RUN mkdir -p /apps/AceClient' >> $dockerfile
echo "ADD AceClient/AceClient.jar /apps/AceClient" >> $dockerfile
echo 'ADD lib /apps/lib/' >> $dockerfile
echo '' >> $dockerfile
echo 'ENTRYPOINT ["java", "-jar", "AceClient/AceClient.jar", "-o"]' >> $dockerfile

if [ "$1" == "--build-images" ]
then
  docker build -f $dockerfile -t ace-client .
fi


# ACE Client DHT
dockerfile=Dockerfile-AceClientDht
cp ../Dockerfile.base $dockerfile
echo 'RUN mkdir -p /apps/AceClientDht' >> $dockerfile
echo "ADD AceClientDht/AceClientDht.jar /apps/AceClientDht" >> $dockerfile
echo 'ADD lib /apps/lib/' >> $dockerfile
echo '' >> $dockerfile
echo 'ENTRYPOINT ["java", "-jar", "AceClientDht/AceClientDht.jar", "-D", "-o"]' >> $dockerfile


if [ "$1" == "--build-images" ]
then
  docker build -f $dockerfile -t ace-client-dht .
fi

rm ../Dockerfile.base