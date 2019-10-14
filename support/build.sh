#!/usr/bin/env bash

NEXIAL_DIST_HOME=~/projects/nexial/nexial-core/build/install/nexial-core
NEXIAL_LIB=${NEXIAL_DIST_HOME}/lib
NEXIAL_JAR=${NEXIAL_LIB}/nexial*.jar


NEXIALSERVICE_HOME=$(cd `dirname $0`/..; pwd -P)
NEXIALSERVICE_LIB=${NEXIALSERVICE_HOME}/lib-nexial


# add all the nexial jars to /lib-nexial
cp -f ${NEXIAL_JAR} ${NEXIALSERVICE_LIB}

cd ${NEXIALSERVICE_HOME}

# just in case the previous ep-nexial build was copied into NEXIAL_LIB, we must remove it before ep-nexial build
rm -fv ${NEXIALSERVICE_LIB}/*.jar

# run nexial-service build

gradle clean installDist

build_ret=$?

if [[ ${build_ret} != 0 ]] ; then
    echo [NEXIAL-SERVICE] gradle build failed!
    exit ${build_ret}
fi