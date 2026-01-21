@echo off
REM
REM This script creates and run the pf4j-maven demo.
REM

REM create artifacts using maven
call mvn clean package -DskipTests

REM create demo-dist folder
rmdir demo-dist /s /q 2>nul
mkdir demo-dist
mkdir demo-dist\plugins

REM copy artifacts to demo-dist folder
xcopy demo\app\target\pf4j-maven-demo-*.zip demo-dist /s /i /q
xcopy demo\plugins\enabled.txt demo-dist\plugins /s /q
xcopy demo\plugins\disabled.txt demo-dist\plugins /s /q
xcopy demo\app\plugins.txt demo-dist /s /q

cd demo-dist

REM unzip app
jar xf pf4j-maven-demo-app-*.zip
del pf4j-maven-demo-app-*.zip

REM run demo
rename pf4j-maven-demo-app-*-SNAPSHOT.jar pf4j-maven-demo.jar
java -jar pf4j-maven-demo.jar

cd ..
