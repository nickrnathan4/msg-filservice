# msg-fileservice

The MSG fileservice is a RESTful webservice backed by S3 and Datomic.
The service supports CRUD operatations and is protected by basic
HTTP authentication.

## File Resources

File resources are defined by the following attributes:
* datomic id
* unique uuid (corresponds to S3 key)
* file name

## Usage

Use the service to create, read, update and delete file resources.
File resources can be accessed via the following HTTP endpoints.

###Create
* **POST "files":** takes a file or multiple files as form parameters, saves the files on S3, creates a database file entity

###Read
* **GET "files":** returns a vector of maps containing all files stored in the database
* **GET "files?filename="example.txt":** takes a url parameter  "filename", returns a vector of maps containing all files stored in the database with the specified file name

* **GET "files/:id":**

Takes a url parameter specifying a unique file uuid, returns an input stream. It is the responsibility of the client application to write the file and close the input stream. The following function, for example, takes the unique file id and downloads the file in the users Donwloads folder.

```clojure
(defn download-file [id]
  (let [resp (client/get (str "http://msg-fileservice.herokuapp.com/files/" id)
                         {:basic-auth [(:user basic-auth) (:pass basic-auth)]})
        filename (second (clojure.string/split
                          (get (:headers resp) "Content-Disposition") #"="))]
    (with-open [out (io/output-stream (str "/Users/exampleuser/Downloads/" filename ))]
      (.write out (IOUtils/toByteArray (:body resp))))))

```
By default the service will return an inputstream however in order to retrieve the database entity or file history the "Content-Type" in the request header must be set to "application/edn".
Retrieve the complete history of a file by setting the url parameter "history" to "true".
Retrieve the version of a file since a particular time by setting the url parameter "since" to the desired time.
Retrieve the version of a file as-of a particular time by setting the url parameter "as-of" to the desired time.
Time parameters must take the following format: "yyyy-MM-dd'T'HH:mm:ss.SSSZ"

###Update
* **PATCH "files/:id":** takes a url parameter specifying a unique file id, and a form parameter specifying the new file with the form key "file", and updates the file

###Delete
* **DELETE "files/:id":** takes a url parameter specifying a unique file id, retracts the database entity


## Environment

Set the following environment variables.

```clojure
AWS_ACCESS_KEY
AWS_SECRET_KEY
S3_BUCKET

PORT
MSG_HTTP_BASIC_USERNAME
MSG_HTTP_BASIC_PASSWORD

DATOMIC_USERNAME
DATOMIC_PASSWORD
```
