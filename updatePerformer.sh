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

# Copy files from CorePerformer and JavaPerformer without breaking imports
cp -rn couchbase-jvm-clients/core-fit-performer/src/main/java/com/couchbase/* src/main/java/com/couchbase/
cp -rn couchbase-jvm-clients/java-fit-performer/src/main/java/com/couchbase/* src/main/java/com/couchbase/

# Copy proto files
cp -r transactions-fit-performer/gRPC/*.proto src/main/proto/

# Copy internal core-io classes
echo "$counter - Cloning and copying internal classes from core-io"
mkdir -p "src/main/java/com/couchbase/client/core/transaction/forwards"
cp -r couchbase-jvm-clients/core-io/src/main/java/com/couchbase/client/core/transaction/forwards/*.java src/main/java/com/couchbase/client/core/transaction/forwards/

echo "$counter - Removing main method from JavaPerformer"
((counter++))
# Next, need to delete the main method from JavaPerformer
./deleteMethod.sh "public static void main(String[] args) throws IOException, InterruptedException" "src/main/java/com/couchbase/JavaPerformer.java"