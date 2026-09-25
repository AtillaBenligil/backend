#!/bin/bash

# Dieses Skript startet einen LDAP-Server fuer die Integrationstests auf einem
# freien Port, der anschliessend ueber stopldap.sh beendet werden kann.
# Der Aufbau folgt starttestdb.sh, damit Datenbank und Verzeichnis auf dieselbe
# Weise in die Buildautomatisierung eingebunden sind.
# Die URL des Servers wird in ldapurl.txt geschrieben, der Name in ldap.txt.
#
# Der Server wird mit denselben Benutzern und Gruppen befuellt wie die
# Entwicklungsumgebung (src/main/scripts/ldap) und erhaelt das lesende
# Dienstkonto cn=readonly, ueber das das Backend die Gruppen liest.

LDIF_DIR="$(cd "$(dirname "$0")/../../main/scripts/ldap" && pwd)"
BASE_DN="dc=irpsim,dc=uni-leipzig,dc=de"
READONLY_PASSWORD=r3ad0nly
MAX_ATTEMPTS=20

# Sucht einen freien Port ab 11000.
function getPort {
  port=$(( 11000 ))
  quit=0

  while [ "$quit" -ne 1 ]; do
    if command -v ss > /dev/null; then
      ss -tan | grep ":$port " >> /dev/null
    else
      netstat -na | grep $port >> /dev/null
    fi
    if [ $? -gt 0 ]; then
      quit=1
    else
      port=`expr $port + 1`
    fi
  done
  echo $port;
}

port=$(getPort)

# Die Zahl der Versuche ist begrenzt: Ist Docker nicht erreichbar, bricht der
# Build mit einer Fehlermeldung ab, statt endlos neue Ports zu probieren.
attempt=1
success=1
while [ "$success" -ne 0 ]; do
	if [ "$attempt" -gt "$MAX_ATTEMPTS" ]; then
		echo "LDAP-Server konnte nach $MAX_ATTEMPTS Versuchen nicht gestartet werden; ist Docker erreichbar?" >&2
		exit 1
	fi
	name="Testldap_$port"
    echo "Starte $name auf $port"
    echo $name > ldap.txt

    docker run -d --name=$name \
		-e LDAP_ORGANISATION=IRPsim \
		-e LDAP_DOMAIN=irpsim.uni-leipzig.de \
		-e LDAP_ADMIN_PASSWORD=1rps1m \
		-e LDAP_REMOVE_CONFIG_AFTER_SETUP=false \
		-e LDAP_READONLY_USER=true \
		-e LDAP_READONLY_USER_USERNAME=readonly \
		-e LDAP_READONLY_USER_PASSWORD=$READONLY_PASSWORD \
		--volume "$LDIF_DIR":/container/service/slapd/assets/config/bootstrap/ldif/custom \
		-p $port:389 osixia/openldap:1.5.0 --copy-service
     success=$?
     if [ "$success" -ne 0 ]; then
     	docker rm -f $name > /dev/null 2>&1
     	port=`expr $port + 1`
     	attempt=`expr $attempt + 1`
     fi
done

# Statt einer festen Wartezeit wird geprueft, ob das Dienstkonto die Gruppen
# bereits lesen kann; erst dann sind auch die LDIF-Dateien eingespielt.
ready=1
for i in $(seq 1 60); do
	docker exec $name ldapsearch -x -H ldap://localhost -D "cn=readonly,$BASE_DN" -w $READONLY_PASSWORD \
		-b "ou=groups,$BASE_DN" "(cn=irpsim-admins)" cn 2>/dev/null | grep -q "^cn: irpsim-admins"
	if [ $? -eq 0 ]; then
		ready=0
		break
	fi
	sleep 1s
done
if [ "$ready" -ne 0 ]; then
	echo "LDAP-Server $name ist nach 60 Sekunden nicht bereit" >&2
	exit 1
fi

export IRPSIM_LDAP_URL=ldap://localhost:$port
export IRPSIM_LDAP_BASE_DN=$BASE_DN

echo $IRPSIM_LDAP_URL > ldapurl.txt
echo "LDAP-Server $name bereit unter $IRPSIM_LDAP_URL"
