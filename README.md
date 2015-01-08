# msg-fileservice

The MSG fileservice is a RESTful webservice backed by S3 and Datomic.
The service supports CRUD operatations and is protected by basic
HTTP authentication.

## File Resources

File resources are defined by the following attributes:
* datomic id
* unique uuid (corresponds to S3 key)
* file name
* file version

## Usage

Use the service to create, read, update and delete file resources.
File resources can be accessed via the following HTTP endpoints.

###Create
* **POST "files":** takes a file or multiple files as parameters, saves the files on S3, creates a database file entity

###Read
* **GET "files":** returns a vector of a maps containing all files stored in the database
* **GET "file/":** takes a url parameter specifying a unique file uuid, returns corresponding database entity
* **GET "filename/":** takes a url parameter specifying a file name, returns all corresponding database entities
* **GET "download/":** takes a url parameter specifying a file uuid, downloads a copy of the file locally, returns Java file object

###Update
* **PATCH "update":** takes a file and that file entity's uuid (in that order) as arguments, uploads the new version of the file,  updates the file version, file name and uuid in the database

###Delete
* **DELETE "file/<uuid>":** takes a url parameter specifying a unique file uuid, deletes the file and retracts the database entity


## Environment

Set the following environment variables.

```clojure
AWS_ACCESS_KEY
AWS_SECRET_KEY
S3_BUCKET

MSG_HTTP_BASIC_USERNAME
MSG_HTTP_BASIC_PASSWORD

DATOMIC_USERNAME
DATOMIC_PASSWORD
```
