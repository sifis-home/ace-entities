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
echo 'ENTRYPOINT ["java", "-jar", "AceClientDht/AceClientDht.jar", "-D"]' >> $dockerfile


if [ "$1" == "--build-images" ]
then
  docker build -f $dockerfile -t ace-client-dht .
fi

exit
#
## ACE Client DHT
#dockerfile=Dockerfile-AceClientDht
#cp ../Dockerfile.base $dockerfile
#JAR_FILE=`ls AceClientDht/AceClientDht*.jar | head -1`
#echo "ADD $JAR_FILE /apps/AceClientDht" >> $dockerfile
#echo 'ADD lib /apps/lib/' >> $dockerfile
#echo '' >> $dockerfile
#echo "ENTRYPOINT [\"java\", \"-jar\", $JAR_FILE, \"-D\"]" >> $dockerfile
#
#exit
#
## OscoreRsServer: Group Manager (ACE Resource Server)
#dockerfile=Dockerfile-OscoreRsServer
#cp ../Dockerfile.base $dockerfile
#echo 'EXPOSE 5783/udp' >> $dockerfile
#echo 'ADD OscoreRsServer.jar /apps' >> $dockerfile
#echo 'ADD lib /apps/lib/' >> $dockerfile
#echo '' >> $dockerfile
#echo 'ENTRYPOINT ["java", "-jar", "OscoreRsServer.jar", "-dht"]' >> $dockerfile
## docker build -f $dockerfile -t oscorersserver .
#
## OscoreAsRsClient: Group OSCORE Server/Client which will join the group(s)
## Client 1 (for Group A)
## Assumes container name "authorization-server" for ACE Authorization Server
## Assumes container name "group-manager" for ACE Resource Server
#dockerfile=Dockerfile-OscoreAsRsClient-Client1
#cp ../Dockerfile.base $dockerfile
#echo 'ADD OscoreAsRsClient.jar /apps' >> $dockerfile
#echo 'ADD lib /apps/lib/' >> $dockerfile
#echo '' >> $dockerfile
#echo 'ENTRYPOINT ["java", "-jar", "OscoreAsRsClient.jar", "-name", "Client1", "-delay", "95", "-as", "coap://authorization-server:5583", "-gm", "coap://group-manager:5783", "-dht"]' >> $dockerfile
## docker build -f $dockerfile -t oscoreasrsclient-client1 .
#
## OscoreAsRsClient: Group OSCORE Server/Client which will join the group(s)
## Client 2 (for Group B)
## Assumes container name "authorization-server" for ACE Authorization Server
## Assumes container name "group-manager" for ACE Resource Server
#dockerfile=Dockerfile-OscoreAsRsClient-Client2
#cp ../Dockerfile.base $dockerfile
#echo 'ADD OscoreAsRsClient.jar /apps' >> $dockerfile
#echo 'ADD lib /apps/lib/' >> $dockerfile
#echo '' >> $dockerfile
#echo 'ENTRYPOINT ["java", "-jar", "OscoreAsRsClient.jar", "-name", "Client2", "-delay", "80", "-as", "coap://authorization-server:5583", "-gm", "coap://group-manager:5783", "-dht"]' >> $dockerfile
## docker build -f $dockerfile -t oscoreasrsclient-client2 .
#
## OscoreAsRsClient: Group OSCORE Server/Client which will join the group(s)
## Server 1 (for Group A)
## Assumes container name "authorization-server" for ACE Authorization Server
## Assumes container name "group-manager" for ACE Resource Server
#dockerfile=Dockerfile-OscoreAsRsClient-Server1
#cp ../Dockerfile.base $dockerfile
#echo 'EXPOSE 4683/udp' >> $dockerfile
#echo 'ADD LED-on.py /apps' >> $dockerfile
#echo 'ADD LED-off.py /apps' >> $dockerfile
#echo 'ADD OscoreAsRsClient.jar /apps' >> $dockerfile
#echo 'ADD lib /apps/lib/' >> $dockerfile
#echo '' >> $dockerfile
#echo 'ENTRYPOINT ["java", "-jar", "OscoreAsRsClient.jar", "-name", "Server1", "-delay", "65", "-as", "coap://authorization-server:5583", "-gm", "coap://group-manager:5783"]' >> $dockerfile
## docker build -f $dockerfile -t oscoreasrsclient-server1 .
#
## OscoreAsRsClient: Group OSCORE Server/Client which will join the group(s)
## Server 2 (for Group A)
## Assumes container name "authorization-server" for ACE Authorization Server
## Assumes container name "group-manager" for ACE Resource Server
#dockerfile=Dockerfile-OscoreAsRsClient-Server2
#cp ../Dockerfile.base $dockerfile
#echo 'EXPOSE 4683/udp' >> $dockerfile
#echo 'ADD LED-on.py /apps' >> $dockerfile
#echo 'ADD LED-off.py /apps' >> $dockerfile
#echo 'ADD OscoreAsRsClient.jar /apps' >> $dockerfile
#echo 'ADD lib /apps/lib/' >> $dockerfile
#echo '' >> $dockerfile
#echo 'ENTRYPOINT ["java", "-jar", "OscoreAsRsClient.jar", "-name", "Server2", "-delay", "55", "-as", "coap://authorization-server:5583", "-gm", "coap://group-manager:5783"]' >> $dockerfile
## docker build -f $dockerfile -t oscoreasrsclient-server2 .
#
## OscoreAsRsClient: Group OSCORE Server/Client which will join the group(s)
## Server 3 (for Group A)
## Assumes container name "authorization-server" for ACE Authorization Server
## Assumes container name "group-manager" for ACE Resource Server
#dockerfile=Dockerfile-OscoreAsRsClient-Server3
#cp ../Dockerfile.base $dockerfile
#echo 'EXPOSE 4683/udp' >> $dockerfile
#echo 'ADD LED-on.py /apps' >> $dockerfile
#echo 'ADD LED-off.py /apps' >> $dockerfile
#echo 'ADD OscoreAsRsClient.jar /apps' >> $dockerfile
#echo 'ADD lib /apps/lib/' >> $dockerfile
#echo '' >> $dockerfile
#echo 'ENTRYPOINT ["java", "-jar", "OscoreAsRsClient.jar", "-name", "Server3", "-delay", "45", "-as", "coap://authorization-server:5583", "-gm", "coap://group-manager:5783"]' >> $dockerfile
## docker build -f $dockerfile -t oscoreasrsclient-server3 .
#
## OscoreAsRsClient: Group OSCORE Server/Client which will join the group(s)
## Server 4 (for Group B)
## Assumes container name "authorization-server" for ACE Authorization Server
## Assumes container name "group-manager" for ACE Resource Server
#dockerfile=Dockerfile-OscoreAsRsClient-Server4
#cp ../Dockerfile.base $dockerfile
#echo 'EXPOSE 4683/udp' >> $dockerfile
#echo 'ADD LED-on.py /apps' >> $dockerfile
#echo 'ADD LED-off.py /apps' >> $dockerfile
#echo 'ADD OscoreAsRsClient.jar /apps' >> $dockerfile
#echo 'ADD lib /apps/lib/' >> $dockerfile
#echo '' >> $dockerfile
#echo 'ENTRYPOINT ["java", "-jar", "OscoreAsRsClient.jar", "-name", "Server4", "-delay", "35", "-as", "coap://authorization-server:5583", "-gm", "coap://group-manager:5783"]' >> $dockerfile
## docker build -f $dockerfile -t oscoreasrsclient-server4 .
#
## OscoreAsRsClient: Group OSCORE Server/Client which will join the group(s)
## Server 5 (for Group B)
## Assumes container name "authorization-server" for ACE Authorization Server
## Assumes container name "group-manager" for ACE Resource Server
#dockerfile=Dockerfile-OscoreAsRsClient-Server5
#cp ../Dockerfile.base $dockerfile
#echo 'EXPOSE 4683/udp' >> $dockerfile
#echo 'ADD LED-on.py /apps' >> $dockerfile
#echo 'ADD LED-off.py /apps' >> $dockerfile
#echo 'ADD OscoreAsRsClient.jar /apps' >> $dockerfile
#echo 'ADD lib /apps/lib/' >> $dockerfile
#echo '' >> $dockerfile
#echo 'ENTRYPOINT ["java", "-jar", "OscoreAsRsClient.jar", "-name", "Server5", "-delay", "25", "-as", "coap://authorization-server:5583", "-gm", "coap://group-manager:5783"]' >> $dockerfile
## docker build -f $dockerfile -t oscoreasrsclient-server5 .
#
## OscoreAsRsClient: Group OSCORE Server/Client which will join the group(s)
## Server 6 (for Group B)
## Assumes container name "authorization-server" for ACE Authorization Server
## Assumes container name "group-manager" for ACE Resource Server
#dockerfile=Dockerfile-OscoreAsRsClient-Server6
#cp ../Dockerfile.base $dockerfile
#echo 'EXPOSE 4683/udp' >> $dockerfile
#echo 'ADD LED-on.py /apps' >> $dockerfile
#echo 'ADD LED-off.py /apps' >> $dockerfile
#echo 'ADD OscoreAsRsClient.jar /apps' >> $dockerfile
#echo 'ADD lib /apps/lib/' >> $dockerfile
#echo '' >> $dockerfile
#echo 'ENTRYPOINT ["java", "-jar", "OscoreAsRsClient.jar", "-name", "Server6", "-delay", "15", "-as", "coap://authorization-server:5583", "-gm", "coap://group-manager:5783"]' >> $dockerfile
## docker build -f $dockerfile -t oscoreasrsclient-server6 .
#
## Adversary: Adversary for testing attacks against the group(s)
#dockerfile=Dockerfile-Adversary
#cp ../Dockerfile.base $dockerfile
#echo 'ADD Adversary.jar /apps' >> $dockerfile
#echo 'ADD lib /apps/lib/' >> $dockerfile
#echo '' >> $dockerfile
#echo 'ENTRYPOINT ["java", "-jar", "Adversary.jar"]' >> $dockerfile
## docker build -f $dockerfile -t adversary .
#
#
### Prepare to build images for EDHOC Applications
#
#cd ../edhoc
#
## Phase0Client: CoAP-only client
## Assumes container name "phase4-server" for server-side
#dockerfile=Dockerfile-Phase0Client
#cp ../Dockerfile.base $dockerfile
#echo 'ADD Phase0Client.jar /apps' >> $dockerfile
#echo 'ADD lib /apps/lib/' >> $dockerfile
#echo '' >> $dockerfile
#echo 'ENTRYPOINT ["java", "-jar", "Phase0Client.jar", "-server", "coap://phase4-server:5697", "-dht"]' >> $dockerfile
## docker build -f $dockerfile -t phase0client .
#
## Phase1Server: EDHOC server using method 0 and no optimized request
#dockerfile=Dockerfile-Phase1Server
#cp ../Dockerfile.base $dockerfile
#echo 'EXPOSE 5694/udp' >> $dockerfile
#echo 'ADD LED-on.py /apps' >> $dockerfile
#echo 'ADD LED-off.py /apps' >> $dockerfile
#echo 'ADD Phase1Server.jar /apps' >> $dockerfile
#echo 'ADD lib /apps/lib/' >> $dockerfile
#echo '' >> $dockerfile
#echo 'ENTRYPOINT ["java", "-jar", "Phase1Server.jar"]' >> $dockerfile
## docker build -f $dockerfile -t phase1server .
#
## Phase1Client: EDHOC client using method 0 and no optimized request
## Assumes container name phase1-server for server-side
#dockerfile=Dockerfile-Phase1Client
#cp ../Dockerfile.base $dockerfile
#echo 'ADD Phase1Client.jar /apps' >> $dockerfile
#echo 'ADD lib /apps/lib/' >> $dockerfile
#echo '' >> $dockerfile
#echo 'ENTRYPOINT ["java", "-jar", "Phase1Client.jar", "-server", "coap://phase1-server:5694", "-dht"]' >> $dockerfile
## docker build -f $dockerfile -t phase1client .
#
## Phase2Server: EDHOC server using method 3 and no optimized request
#dockerfile=Dockerfile-Phase2Server
#cp ../Dockerfile.base $dockerfile
#echo 'EXPOSE 5695/udp' >> $dockerfile
#echo 'ADD LED-on.py /apps' >> $dockerfile
#echo 'ADD LED-off.py /apps' >> $dockerfile
#echo 'ADD Phase2Server.jar /apps' >> $dockerfile
#echo 'ADD lib /apps/lib/' >> $dockerfile
#echo '' >> $dockerfile
#echo 'ENTRYPOINT ["java", "-jar", "Phase2Server.jar"]' >> $dockerfile
## docker build -f $dockerfile -t phase2server .
#
## Phase2Client: EDHOC client using method 3 and no optimized request
## Assumes container name phase2-server for server-side
#dockerfile=Dockerfile-Phase2Client
#cp ../Dockerfile.base $dockerfile
#echo 'ADD Phase2Client.jar /apps' >> $dockerfile
#echo 'ADD lib /apps/lib/' >> $dockerfile
#echo '' >> $dockerfile
#echo 'ENTRYPOINT ["java", "-jar", "Phase2Client.jar", "-server", "coap://phase2-server:5695", "-dht"]' >> $dockerfile
## docker build -f $dockerfile -t phase2client .
#
## Phase3Server: EDHOC server using method 0 and the optimized request
#dockerfile=Dockerfile-Phase3Server
#cp ../Dockerfile.base $dockerfile
#echo 'EXPOSE 5696/udp' >> $dockerfile
#echo 'ADD LED-on.py /apps' >> $dockerfile
#echo 'ADD LED-off.py /apps' >> $dockerfile
#echo 'ADD Phase3Server.jar /apps' >> $dockerfile
#echo 'ADD lib /apps/lib/' >> $dockerfile
#echo '' >> $dockerfile
#echo 'ENTRYPOINT ["java", "-jar", "Phase3Server.jar"]' >> $dockerfile
## docker build -f $dockerfile -t phase3server .
#
## Phase3Client: EDHOC client using method 0 and the optimized request
## Assumes container name phase3-server for server-side
#dockerfile=Dockerfile-Phase3Client
#cp ../Dockerfile.base $dockerfile
#echo 'ADD Phase3Client.jar /apps' >> $dockerfile
#echo 'ADD lib /apps/lib/' >> $dockerfile
#echo '' >> $dockerfile
#echo 'ENTRYPOINT ["java", "-jar", "Phase3Client.jar", "-server", "coap://phase3-server:5696", "-dht"]' >> $dockerfile
## docker build -f $dockerfile -t phase3client .
#
## Phase4Server: EDHOC server using method 3 and the optimized request
#dockerfile=Dockerfile-Phase4Server
#cp ../Dockerfile.base $dockerfile
#echo 'EXPOSE 5697/udp' >> $dockerfile
#echo 'ADD LED-on.py /apps' >> $dockerfile
#echo 'ADD LED-off.py /apps' >> $dockerfile
#echo 'ADD Phase4Server.jar /apps' >> $dockerfile
#echo 'ADD lib /apps/lib/' >> $dockerfile
#echo '' >> $dockerfile
#echo 'ENTRYPOINT ["java", "-jar", "Phase4Server.jar"]' >> $dockerfile
## docker build -f $dockerfile -t phase4server .
#
## Phase4Client: EDHOC client using method 3 and the optimized request
## Assumes container name "phase4-server" for server-side
#dockerfile=Dockerfile-Phase4Client
#cp ../Dockerfile.base $dockerfile
#echo 'ADD Phase4Client.jar /apps' >> $dockerfile
#echo 'ADD lib /apps/lib/' >> $dockerfile
#echo '' >> $dockerfile
#echo 'ENTRYPOINT ["java", "-jar", "Phase4Client.jar", "-server", "coap://phase4-server:5697", "-dht"]' >> $dockerfile
## docker build -f $dockerfile -t phase4client .
#
#
### Actually build the images. Note that only some of the images are built.
## The below part should be done in the Github Actions to push the images to Docker Hub.
#
## If indicated actually build the images, otherwise exit here
#if [ "$1" != "--build-images" ]
#then
#  exit
#fi
#
## Build images for the Group Applications
#
#cd ../group
#
#docker build -f Dockerfile-OscoreAsServer -t authorization-server .
#
#docker build -f Dockerfile-OscoreRsServer -t group-manager .
#
#docker build -f Dockerfile-OscoreAsRsClient-Client1 -t group-client1 .
#
#docker build -f Dockerfile-OscoreAsRsClient-Client2 -t group-client2 .
#
#docker build -f Dockerfile-OscoreAsRsClient-Server1 -t group-server1 .
#
#docker build -f Dockerfile-OscoreAsRsClient-Server2 -t group-server2 .
#
#docker build -f Dockerfile-OscoreAsRsClient-Server3 -t group-server3 .
#
#docker build -f Dockerfile-OscoreAsRsClient-Server4 -t group-server4 .
#
#docker build -f Dockerfile-OscoreAsRsClient-Server5 -t group-server5 .
#
#docker build -f Dockerfile-OscoreAsRsClient-Server6 -t group-server6 .
#
#docker build -f Dockerfile-Adversary -t group-adversary .
#
#
## Build images for the EDHOC Applications
#
#cd ../edhoc
#
#docker build -f Dockerfile-Phase0Client -t phase0-client .
#
#docker build -f Dockerfile-Phase1Server -t phase1-server .
#
#docker build -f Dockerfile-Phase1Client -t phase1-client .
#
#docker build -f Dockerfile-Phase2Server -t phase2-server .
#
#docker build -f Dockerfile-Phase2Client -t phase2-client .
#
#docker build -f Dockerfile-Phase3Server -t phase3-server .
#
#docker build -f Dockerfile-Phase3Client -t phase3-client .
#
#docker build -f Dockerfile-Phase4Server -t phase4-server .
#
#docker build -f Dockerfile-Phase4Client -t phase4-client .
#
