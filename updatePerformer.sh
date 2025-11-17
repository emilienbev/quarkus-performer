counter=1

echo "$counter - Fetching Couchbase Java SDK version and Quarkus version from quarkus-couchbase"
((counter++))
POM_CONTENT=$(curl -s https://raw.githubusercontent.com/quarkiverse/quarkus-couchbase/main/pom.xml)
SDK_VERSION=$(echo "$POM_CONTENT" | awk -F'[<>]' '/couchbase.client.version/ {print $3}' | tr -d '[:space:]')
QUARKUS_VERSION=$(echo "$POM_CONTENT" | grep -E '<quarkus\.version>' | grep -v 'platform' | awk -F'[<>]' '{print $3}' | tr -d '[:space:]')
if [ -z "$SDK_VERSION" ]; then
  echo "Error: Failed to fetch SDK version"
  exit 1
fi
if [ -z "$QUARKUS_VERSION" ]; then
  echo "Error: Failed to fetch Quarkus version"
  exit 1
fi
echo "Detected SDK version: $SDK_VERSION"
echo "Detected Quarkus version: $QUARKUS_VERSION"

echo "$counter - Updating Quarkus version in local pom.xml"
((counter++))
sed -i '' "s|<quarkus.platform.version>[^<]*</quarkus.platform.version>|<quarkus.platform.version>${QUARKUS_VERSION}</quarkus.platform.version>|" pom.xml

echo "$counter - Cloning couchbase-jvm-clients (tag: $SDK_VERSION)"
((counter++))

# Step 1: Clone the repository with no files checked out
git clone -n --filter=blob:none --sparse git@github.com:couchbase/couchbase-jvm-clients.git
cd couchbase-jvm-clients || exit
# Fetch the specific tag
git fetch origin tag "$SDK_VERSION" --depth=1
git sparse-checkout set --no-cone /core-fit-performer/src/main/java/ /java-fit-performer/src/main/java/ /core-io/src/main/java/com/couchbase/client/core/transaction/forwards/
git checkout "tags/$SDK_VERSION"

cd ..
echo "$counter - Cloning transactions-fit-performer"
((counter++))

git clone -n --depth=1 --filter=blob:none --sparse git@github.com:couchbaselabs/transactions-fit-performer.git
cd transactions-fit-performer || exit
git sparse-checkout set --no-cone /gRPC
git checkout

cd ..

echo "$counter - Copying files from Core and Java Performers"
((counter++))
cp -r couchbase-jvm-clients/core-fit-performer/src/main/java/com/couchbase/* src/main/java/com/couchbase/
cp -r couchbase-jvm-clients/java-fit-performer/src/main/java/com/couchbase/* src/main/java/com/couchbase/

echo "$counter - Copying .proto files from transactions-fit-performer"
((counter++))
cp -r transactions-fit-performer/gRPC/*.proto src/main/proto/

echo "$counter - Cloning and copying internal classes from core-io"
((counter++))
mkdir -p "src/main/java/com/couchbase/client/core/transaction/forwards"
cp -r couchbase-jvm-clients/core-io/src/main/java/com/couchbase/client/core/transaction/forwards/*.java src/main/java/com/couchbase/client/core/transaction/forwards/

echo "$counter - Removing main method from JavaPerformer"
((counter++))
./deleteMethod.sh "public static void main" "src/main/java/com/couchbase/JavaPerformer.java" "false"

echo "$counter - Replacing broken InsecureTrustManagerFactory import"
((counter++))
sed -i '' 's/import io.grpc.netty.shaded.io.netty.handler.ssl.util.InsecureTrustManagerFactory;/import com.couchbase.client.core.deps.io.netty.handler.ssl.util.InsecureTrustManagerFactory;/' src/main/java/com/couchbase/utils/OptionsUtil.java

echo "$counter - Adjusting performer for Quarkus compatibility"
((counter++))
sed -i '' '/response\.addPerformerCaps(Caps\.OBSERVABILITY_1);/d' src/main/java/com/couchbase/JavaPerformer.java
#Deleting the 3 lines configuring the custom scheduler
sed -i '' '/var userExecutorAndScheduler = UserSchedulerUtil\.userExecutorAndScheduler();/ {
  N
  N
  d
}' src/main/java/com/couchbase/JavaPerformer.java

#Make logger public
sed -i '' 's/private static final Logger logger = LoggerFactory.getLogger(JavaPerformer.class);/public static final Logger logger = LoggerFactory.getLogger(JavaPerformer.class);/' src/main/java/com/couchbase/JavaPerformer.java

#Deletes the body of UserSchedulerUtil.assertInCustomUserSchedulerThread
./deleteMethod.sh "private static void assertInCustomUserSchedulerThread" "src/main/java/com/couchbase/utils/UserSchedulerUtil.java" "true"

echo "$counter - Delete cloned repositories"
((counter++))
rm -rf couchbase-jvm-clients
rm -rf transactions-fit-performer


