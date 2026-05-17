#! /bin/bash

#CURRENT_VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
#NEW_VERSION=$(echo $CURRENT_VERSION | perl -pe 's/(parar-test)(\d+)/$1.($2+1)/ge')
#mvn versions:set -DnewVersion=$NEW_VERSION

NUMBER_FILE=".build_number"

if [ -f "$NUMBER_FILE" ]; then
    test_number=$(cat $NUMBER_FILE)
else
    test_number=1
fi

mvn package -Dbuild_name="-build$test_number"


if [ $? -eq 0 ]; then
    echo $((test_number + 1)) > $NUMBER_FILE
fi