# quarkus-performer

This project is a proof of concept to "port" over our Java FIT Performer to use the Couchbase Quarkus extension.
It starts a gRPC server which executes FIT Driver (gRPC client) requests against a Couchbase server.

> As of August 2024, the official Quarkus Couchbase Extension is not native-compatible and will not work with this project.
> This project uses an [experimental fork](https://github.com/emilienbev/quarkus-embev) which adds native compilation compatibility.  

## Running the tests
You'll need:
- quarkus-performer (FIT Performer, a gRPC Server)
- transaction-fit-performer (FIT Driver, a gRPC Client)
- Couchbase Server (Local or Capella)
- Mandrel 24.0.2.r22 or Graal 22.0.2 (or latest for both)

> Using [SDKMAN!](https://sdkman.io/) is recommended to install and manage different JDKs.

### Step 1
Start a Couchbase Server

With Docker:
```shell script
docker run -d --name MyCouchbaseCluster -p 8091-8097:8091-8097 -p 11210:11210 couchbase:latest
```
Head to https://localhost:8091 to finish setting up your cluster.

### Step 2
Clone this repository and `cd` into it:
```shell script
git clone git@github.com:emilienbev/quarkus-performer.git
cd quarkus-performer
```
To install/use Mandrel, do:
```shell script
sdk install java 24.0.2.r22-mandrel
sdk use java 24.0.2.r22-mandrel    
```
> Note that `sdk use java {version}` will use that version in your current shell. Opening a new terminal session will use the default Java version, which you can specify with `sdk default java {version}`.   

Open `application.properties` in `quarkus-performer/src/main/resources` and modify the credentials to the ones you chose when setting up your Cluster (or change the connection string if your Cluster is remote).

> Cluster configurations in `application.properties` are set at build-time and cannot be modified at run-time. Changing them will require re-compiling. 

Still with `quarkus-performer` open in a terminal, run:
```shell script
mvn clean install -Dnative -DskipTests
```
Once it is compiled run the performer:
```shell script
cd cd target/quarkus-couchbase-demo-1.0.0-SNAPSHOT-native-image-source-jar
java -jar quarkus-couchbase-demo-1.0.0-SNAPSHOT-runner.jar
```

### Step 3
Clone transactions-fit-performer:
```shell script
git clone git clone git@github.com:couchbaselabs/transactions-fit-performer.git
```
Open the project in another IDE window and follow the README.md to configure FITConfirguration.json.

Right click and run any test in `test-driver/src/test/java/com/couchbase`.

# Other Infos 

## Packaging and running the application

The application can be packaged using:
```shell script
./mvnw package
```
It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:
```shell script
./mvnw package -Dquarkus.package.jar.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar target/*-runner.jar`.

## Creating a native executable

You can create a native executable using: 
```shell script
./mvnw package -Dnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using: 
```shell script
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/quarkus-couchbase-demo-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult https://quarkus.io/guides/maven-tooling.