> ## My contribution: LDAP authentication and access control (2026)
>
> This fork of the [IRPsim backend](https://github.com/IRPsim/backend) (an energy-system
> modelling platform from Leipzig University) adds security to a Java/JAX-RS backend
> that had none. University project, built over three sprints.
>
> - **LDAP login** (`server/security`): login, logout and password change endpoints; a
>   JNDI-based LDAP authenticator; session tokens with a request filter. Groups and email
>   addresses are read from OpenLDAP through a service account.
> - **Role-based access control:** an administrator role, groups, and per-resource
>   grants for scenarios and simulation jobs, enforced by a JAX-RS authorization
>   filter and in the service layer.
> - **Standing data ACLs:** the old "responsible person" ownership was replaced with
>   read/write grants, plus a migration that converts existing records without data loss.
> - **Build and tests:** an OpenLDAP server (Docker Compose, LDIF seed data) is part of
>   the build automation; unit and integration tests cover login, authorization
>   decisions and the migration.
>
> 14 commits, about 5,700 lines added including tests. Frontend counterpart:
> [ui-client fork](https://github.com/AtillaBenligil/ui-client).
> Commit messages are in German, the project's working language.

# IRPsim backend

This repository provides the backend for the IRPsim infrastructure, which enables input and output data management for models in the energy domain, model execution management and model coupling.

# Starting

To start the backend, please execute the following steps:

* Build the projects using `mvn clean package -DskipTests=true`. It is required that you got the project `com.gams:gamsjavaapi:jar:24.7.1` installed in your local maven repository.
* Start MariaDB: `cd backend-server/target/backend-production && source startEnvironmenManually.sh`. This starts MariaDB with the default password 1rps1m. If there is a problem with creation of the database, please run `docker exec -it $container mysql -u root -pb4s3l -e "CREATE DATABASE irpsim"`
* Optional: Check the values of the environment variables `$IRPSIM_MYSQL_URL`, `$IRPSIM_PORT`
* Start the server: Call `./startup.sh`. The server will be available under `localhost:$IRPSIM_PORT`

## Execution with GAMS

If you need GAMS instance for your model, please execute the following commands before starting the backend:

```
wget https://d37drm4t2jghv5.cloudfront.net/distributions/24.7.1/linux/linux_x64_64_sfx.exe
chmod 755 linux_x64_64_sfx.exe
./linux_x64_64_sfx.exe
gamsinst -a
export LD_LIBRARY_PATH=$(pwd)
```

Please note that without a suitable GAMS license, you will only be able to run minimal models.

## Configuration

If you want to configure IRPsim yourself (instead using the startup script), please set the following variables:
* `IRPSIM_MYSQL_USER`: MySQL user
* `IRPSIM_MYSQL_PASSWORD`: MySQL password
* `IRPSIM_MYSQL_URL`: URL of the database
* `IRPSIM_PORT`: Port of the application
* `IRPSIM_MYSQL_SAVEPATH` and `IRPSIM_MYSQL_LOADPATH` for the path for saving and loading temporary csv files for sql (the loadpath might be inside of a container and therefore different than the savepath)

# Licensing

This project is licensed under GPLv3. 
