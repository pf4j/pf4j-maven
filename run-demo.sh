#!/bin/sh

#
# This script creates and run the pf4j demo.
#

# create artifacts using maven
mvn clean package -DskipTests

# create demo-dist folder
rm -fr demo-dist
mkdir -p demo-dist/plugins

# copy artifacts to demo-dist folder
cp demo/maven/app/target/pf4j-maven-demo-*.zip demo-dist/
#cp demo/maven/plugins/plugin1/target/pf4j-maven-demo-plugin1-*-all.jar demo-dist/plugins/
#cp demo/maven/plugins/plugin2/target/pf4j-maven-demo-plugin2-*-all.jar demo-dist/plugins/
cp demo/maven/plugins/enabled.txt demo-dist/plugins/
cp demo/maven/plugins/disabled.txt demo-dist/plugins/
cp demo/maven/app/plugins.txt demo-dist/

cd demo-dist

# unzip app
jar xf pf4j-maven-demo-app-*.zip
rm pf4j-maven-demo-app-*.zip

# run demo
mv pf4j-maven-demo-app-*-SNAPSHOT.jar pf4j-maven-demo.jar
java -jar pf4j-maven-demo.jar

cd -
