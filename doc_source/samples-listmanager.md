# List manager sample application for AWS Lambda<a name="samples-listmanager"></a>

The list manager sample application demonstrates the use of AWS Lambda to process records in an Amazon Kinesis data stream\. A Lambda event source mapping reads records from the stream in batches and invokes a Lambda function\. The function uses information from the records to update documents in Amazon DynamoDB and stores the records it processes in Amazon Relational Database Service \(Amazon RDS\)\.

![\[Image NOT FOUND\]](http://docs.aws.amazon.com/lambda/latest/dg/images/sample-listmanager.png)

Clients send records to a Kinesis stream, which stores them and makes them available for processing\. The Kinesis stream is used like a queue to buffer records until they can be processed\. Unlike an Amazon SQS queue, records in a Kinesis stream are not deleted after they are processed, so multiple consumers can process the same data\. Records in Kinesis are also processed in order, where queue items can be delivered out of order\. Records are deleted from the stream after 7 days\.

In addition to the function that processes events, the application includes a second function for performing administrative tasks on the database\. Function code is available in the following files:
+ Processor – [processor/index\.js](https://github.com/awsdocs/aws-lambda-developer-guide/blob/master/sample-apps/list-manager/processor/index.js)
+ Database admin – [dbadmin/index\.js](https://github.com/awsdocs/aws-lambda-developer-guide/blob/master/sample-apps/list-manager/dbadmin/index.js)

You can deploy the sample in a few minutes with the AWS CLI and AWS CloudFormation\. To download, configure, and deploy it in your account, follow the instructions in the [README](https://github.com/awsdocs/aws-lambda-developer-guide/tree/master/sample-apps/list-manager)\.

**Topics**
+ [Architecture and event structure](#samples-listmanager-architecture)
+ [Instrumentation with AWS X\-Ray](#samples-listmanager-instrumentation)
+ [AWS CloudFormation templates and additional resources](#samples-listmanager-template)

## Architecture and event structure<a name="samples-listmanager-architecture"></a>

The sample application uses the following AWS services:
+ Kinesis – Receives events from clients and stores them temporarily for processing\.
+ AWS Lambda – Reads from the Kinesis stream and sends events to the function's handler code\.
+ DynamoDB – Stores lists generated by the application\.
+ Amazon RDS – Stores a copy of processed records in a relational database\.
+ AWS Secrets Manager – Stores the database password\.
+ Amazon VPC – Provides a private local network for communication between the function and database\.

**Pricing**  
Standard charges apply for each service\.

The application processes JSON documents from clients that contain information necessary to update a list\. It supports two types of list: tally and ranking\. A *tally* contains values that are added to the current value for key if it exists\. Each entry processed for a user increases the value of a key in the specified table\.

The following example shows a document that increases the `xp` \(experience points\) value for a user's `stats` list\.

**Example record – Tally type**  

```
{
  "title": "stats",
  "user": "bill",
  "type": "tally",
  "entries": {
    "xp": 83
  }
}
```

A *ranking* contains a list of entries where the value is the order in which they are ranked\. A ranking can be updated with different values that overwrite the current value, instead of incrementing it\. The following example shows a ranking of favorite movies:

**Example record – Ranking type**  

```
{
  "title": "favorite movies",
  "user": "mike",
  "type": "rank",
  "entries": {
    "blade runner": 1,
    "the empire strikes back": 2,
    "alien": 3
  }
}
```

A Lambda [event source mapping](invocation-eventsourcemapping.md) read records from the stream in batches and invokes the processor function\. The event that the function handler received contains an array of objects that each contain details about a record, such as when it was received, details about the stream, and an encoded representation of the original record document\.

**Example [events/kinesis\.json](https://github.com/awsdocs/aws-lambda-developer-guide/blob/master/sample-apps/list-manager/events/kinesis.json) – Record**  

```
{
  "Records": [
    {
      "kinesis": {
        "kinesisSchemaVersion": "1.0",
        "partitionKey": "0",
        "sequenceNumber": "49598630142999655949899443842509554952738656579378741250",
        "data": "eyJ0aXRsZSI6ICJmYXZvcml0ZSBtb3ZpZXMiLCAidXNlciI6ICJyZGx5c2N0IiwgInR5cGUiOiAicmFuayIsICJlbnRyaWVzIjogeyJibGFkZSBydW5uZXIiOiAyLCAidGhlIGVtcGlyZSBzdHJpa2VzIGJhY2siOiAzLCAiYWxpZW4iOiAxfX0=",
        "approximateArrivalTimestamp": 1570667770.615
      },
      "eventSource": "aws:kinesis",
      "eventVersion": "1.0",
      "eventID": "shardId-000000000000:49598630142999655949899443842509554952738656579378741250",
      "eventName": "aws:kinesis:record",
      "invokeIdentityArn": "arn:aws:iam::123456789012:role/list-manager-processorRole-7FYXMPLH7IUS",
      "awsRegion": "us-east-2",
      "eventSourceARN": "arn:aws:kinesis:us-east-2:123456789012:stream/list-manager-stream-87B3XMPLF1AZ"
    },
    ...
```

When it's decoded, the data contains a record\. The function uses the record to update the user's list and an aggregate list that stores accumulated values across all users\. It also stores a copy of the event in the application's database\.

## Instrumentation with AWS X\-Ray<a name="samples-listmanager-instrumentation"></a>

The application uses [AWS X\-Ray](services-xray.md) to trace function invocations and the calls that functions make to AWS services\. X\-Ray uses the trace data that it receives from functions to create a service map that helps you identify errors\. The following service map shows the function communicating with two DynamoDB tables and a MySQL database\.

![\[Image NOT FOUND\]](http://docs.aws.amazon.com/lambda/latest/dg/images/listmanager-servicemap.png)

The Node\.js function is configured for active tracing in the template, and is instrumented with the AWS X\-Ray SDK for Node\.js in code\. The X\-Ray SDK records a subsegment for each call made with an AWS SDK or MySQL client\.

![\[Image NOT FOUND\]](http://docs.aws.amazon.com/lambda/latest/dg/images/listmanager-trace.png)

The function uses the AWS SDK for JavaScript in Node\.js to read and write to two tables for each record\. The primary table stores the current state of each combination of list name and user\. The aggregate table stores lists that combine data from multiple users\.

## AWS CloudFormation templates and additional resources<a name="samples-listmanager-template"></a>

The application is implemented in Node\.js modules and deployed with an AWS CloudFormation template and shell scripts\. The application template creates two functions, a Kinesis stream, DynamoDB tables and the following supporting resources\.

**Application resources**
+ Execution role – An IAM role that grants the functions permission to access other AWS services\.
+ Lambda event source mapping – Reads records from the data stream and invokes the function\.

View the [application template](https://github.com/awsdocs/aws-lambda-developer-guide/blob/master/sample-apps/list-manager/template.yml) on GitHub\.

A second template, [template\-vpcrds\.yml](https://github.com/awsdocs/aws-lambda-developer-guide/blob/master/sample-apps/list-manager/template.yml), creates the Amazon VPC and database resources\. While it is possible to create all of the resources in one template, separating them makes it easier to clean up the application and allows the database to be reused with multiple applications\.

**Infrastructure resources**
+ VPC – A virtual private cloud network with private subnets, a route table, and a VPC endpoint that allows the function to communicate with DynamoDB without an internet connection\.
+ Database – An Amazon RDS database instance and a subnet group that connects it to the VPC\.