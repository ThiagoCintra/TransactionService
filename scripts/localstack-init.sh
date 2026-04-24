#!/bin/bash
set -e

echo "Initializing LocalStack SQS resources..."

awslocal sqs create-queue \
    --queue-name transactions \
    --region us-east-1

echo "SQS queue 'transactions' created successfully"

awslocal sqs list-queues --region us-east-1
echo "LocalStack initialization complete"
