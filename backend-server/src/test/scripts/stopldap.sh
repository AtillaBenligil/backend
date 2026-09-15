#!/bin/bash

# Stoppt den vorher mit startldap.sh gestarteten LDAP-Server. Der Name wird aus
# ldap.txt gelesen. Der Container wird anschliessend umbenannt, damit auf
# demselben Port ein neuer Container erstellt werden kann.

name=`cat ldap.txt`
docker stop $name

name2=$name
rename=1
count=0
while [[ "$rename" -ne 0 && "$count" -le 100 ]]; do
  name2=$name"_old_"$count
  echo "Benenne um zu: $name2"
  docker rename $name $name2
  rename=$?
  count=`expr $count + 1`
done
echo "Umbenennen abgeschlossen"
