-- Database-per-service: each microservice owns its schema; no service reads another's tables.
CREATE DATABASE authdb;
CREATE DATABASE accountdb;
CREATE DATABASE transactiondb;
