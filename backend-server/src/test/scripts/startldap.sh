#!/bin/bash

# Dieses Skript startet einen LDAP-Server fuer die Integrationstests auf einem
# freien Port, der anschliessend ueber stopldap.sh beendet werden kann.
# Der Aufbau folgt starttestdb.sh, damit Datenbank und Verzeichnis auf dieselbe
# Weise in die Buildautomatisierung eingebunden sind.
# Die URL des Servers wird in ldapurl.txt geschrieben, der Name in ldap.txt.

# Sucht einen freien Port ab 11000.
function getPort {
  port=$(( 11000 ))
  quit=0

  while [ "$quit" -ne 1 ]; do
    netstat -na | grep $port >> /dev/null
    if [ $? -gt 0 ]; then
      quit=1
    else
      port=`expr $port + 1`
    fi
  done
  echo $port;
}

port=$(getPort)

success=1
while [ "$success" -ne 0 ]; do
	name="Testldap_$port"
    echo "Starte $name auf $port"
    echo $name > ldap.txt

    docker run -d --name=$name \
		-e LDAP_ORGANISATION=IRPsim \
		-e LDAP_DOMAIN=irpsim.uni-leipzig.de \
		-e LDAP_ADMIN_PASSWORD=1rps1m \
		-e LDAP_REMOVE_CONFIG_AFTER_SETUP=false \
		-p $port:389 osixia/openldap:1.5.0 --copy-service
     success=$?
     if [ "$success" -ne 0 ]; then
     	port=`expr $port + 1`
     fi
done

sleep 10s

export IRPSIM_LDAP_URL=ldap://localhost:$port
export IRPSIM_LDAP_BASE_DN=dc=irpsim,dc=uni-leipzig,dc=de

echo $IRPSIM_LDAP_URL > ldapurl.txt
