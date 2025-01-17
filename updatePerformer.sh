counter=1

echo "$counter - Cloning couchbase-jvm-clients"
((counter++))

# Step 1: Clone the repository with no files checked out
git clone -n --depth=1 --filter=blob:none --sparse git@github.com:couchbase/couchbase-jvm-clients.git
cd couchbase-jvm-clients || exit
git sparse-checkout set --no-cone /core-fit-performer/src/main/java/ /java-fit-performer/src/main/java/ /core-io/src/main/java/com/couchbase/client/core/transaction/forwards/
git checkout

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
cp -rn couchbase-jvm-clients/core-fit-performer/src/main/java/com/couchbase/* src/main/java/com/couchbase/
cp -rn couchbase-jvm-clients/java-fit-performer/src/main/java/com/couchbase/* src/main/java/com/couchbase/

echo "$counter - Copying .proto files from transactions-fit-performer"
((counter++))
cp -r transactions-fit-performer/gRPC/*.proto src/main/proto/

echo "$counter - Cloning and copying internal classes from core-io"
((counter++))
mkdir -p "src/main/java/com/couchbase/client/core/transaction/forwards"
cp -r couchbase-jvm-clients/core-io/src/main/java/com/couchbase/client/core/transaction/forwards/*.java src/main/java/com/couchbase/client/core/transaction/forwards/

echo "$counter - Removing main method from JavaPerformer"
((counter++))
./deleteMethod.sh "public static void main(String[] args) throws IOException, InterruptedException" "src/main/java/com/couchbase/JavaPerformer.java"

echo "$counter - Adjusting performer for Quarkus compatibility"
((counter++))
sed -i '/response\.addPerformerCaps(Caps\.OBSERVABILITY_1);/d' src/main/java/com/couchbase/JavaPerformer.java
sed -i '/var userExecutorAndScheduler = UserSchedulerUtil\.userExecutorAndScheduler();/{N;N;d}' src/main/java/com/couchbase/JavaPerformer.java

#Deletes the body of UserSchedulerUtil.assertInCustomUserSchedulerThread
sed -E '
/^\s*private static void assertInCustomUserSchedulerThread\(/, /^\s*}/ {
  # Skip the method declaration line
  /^\s*private static void assertInCustomUserSchedulerThread\(/ b;
  # Skip the closing brace line
  /^\s*}/ b;
  # Delete everything else (the method body)
  d;
}' src/main/java/com/couchbase/utils/UserSchedulerUtil.java

echo "$counter - Delete cloned repositories"
((counter++))
rm -rf couchbase-jvm-clients
rm -rf transactions-fit-performer


