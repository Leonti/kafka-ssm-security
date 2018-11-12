#!/usr/bin/env bash
hash aws 2>/dev/null            || { echo "missing aws cli :("; exit 1; }
hash docker-compose 2>/dev/null || { echo "missing docker-compose :("; exit 1; }

# this is not run in the pipeline because it's a real test.
# you will need to be authenticated to a live aws account to run this suite.
echo "--- killing any live containers"
docker-compose down

echo "--- deploying the integration test data"
aws cloudformation deploy \
    --stack-name 'kafka-ssm-ci-setup' \
    --region 'ap-southeast-2' \
    --template-file 'src/it/resources/ci-setup.yml' \
    --no-fail-on-empty-changeset

echo "--- running integration tests"
docker-compose run integration-tests

echo "--- cleaning up"
docker-compose down

echo "--- complete"
