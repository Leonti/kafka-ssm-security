#!/usr/bin/env bash
set -eu

# You can just deploy this with a regular cloudformation deploy command
# (and --no-fail-on-empty-changeset makes this idempotent).
aws cloudformation deploy \
    --stack-name 'kafka-ssm-security-example' \
    --region 'ap-southeast-2' \
    --template-file 'ssm.yml' \
    --no-fail-on-empty-changeset
