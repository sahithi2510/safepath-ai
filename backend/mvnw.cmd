@REM ----------------------------------------------------------------------------
@REM Maven Wrapper startup batch script
@REM ----------------------------------------------------------------------------
@SET MAVEN_PROJECTBASEDIR=%~dp0
@SET MVN_WRAPPER_BIN=%MAVEN_PROJECTBASEDIR%.mvn\wrapper\apache-maven-3.9.9\bin\mvn.cmd
@IF EXIST "%MVN_WRAPPER_BIN%" GOTO found_dist
@IF EXIST "%MAVEN_PROJECTBASEDIR%.mvn\wrapper\mvn.cmd" GOTO found
@ECHO Maven wrapper not found. Running system mvn.
mvn %*
@GOTO end
:found_dist
"%MVN_WRAPPER_BIN%" %*
@GOTO end
:found
"%MAVEN_PROJECTBASEDIR%.mvn\wrapper\mvn.cmd" %*
:end
