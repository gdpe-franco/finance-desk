@echo off
set MAVEN_PROJECTBASEDIR=%~dp0
set WRAPPER_JAR=%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar
if not exist "%WRAPPER_JAR%" (
  echo Maven Wrapper JAR is missing: %WRAPPER_JAR% 1>&2
  exit /b 1
)
java -classpath "%WRAPPER_JAR%" -Dmaven.multiModuleProjectDirectory="%MAVEN_PROJECTBASEDIR%" org.apache.maven.wrapper.MavenWrapperMain %*
