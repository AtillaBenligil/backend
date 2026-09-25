# Dieses Skript startet eine Datenbank und richtet sie ein, um schnell auf einem Ubuntu-Rechner entwicklungsfähig zu sein.

echo "Creating upload-folder ../import"
mkdir -p ../import/
docker network create irpsimtest
echo "Starting mariadb container"
docker run --name mariadb \
       -e MYSQL_ROOT_PASSWORD=1rps1m \
       -e MYSQL_DATABASE=irpsim \
       -e MYSQL_INITDB_SKIP_TZINFO=1 \
       --volume $(pwd)/../import:/var/lib/import/ \
       	--network irpsimtest \
       -p 5030:3306 -d mariadb:10.4
container=$(docker ps | grep mariadb | awk '{print $1}')
echo "Database created: $container"

export IRPSIM_MYSQL_USER=root
export IRPSIM_MYSQL_PASSWORD=1rps1m
export IRPSIM_MYSQL_URL=jdbc:mysql://localhost:5030/irpsim
export IRPSIM_PORT=8282
export IRPSIM_PERSISTENCEFOLDER=irpsim_persistence
export IRPSIM_MYSQL_JAVAPATH=$(pwd)/../import

# LDAP-Verzeichnis aus docker-compose.yml (docker compose up -d ldap)
export IRPSIM_LDAP_URL=ldap://localhost:10389
export IRPSIM_LDAP_BIND_DN=cn=readonly,dc=irpsim,dc=uni-leipzig,dc=de
export IRPSIM_LDAP_BIND_PASSWORD=r3ad0nly
