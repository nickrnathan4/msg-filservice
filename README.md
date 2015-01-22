# msg-fileservice

The MSG fileservice is a RESTful webservice backed by S3 and Datomic.
The service supports CRUD operatations and is protected by basic
HTTP authentication.

## File Resources

File resources are defined by the following attributes:
* unique file id
* uuid (corresponds to file version)
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
Takes a url parameter specifying a unique file id, returns an input stream. It is the responsibility of the client application to write the file and close the input stream. By default the service will return an inputstream however in order to retrieve the database entity or file history the "Content-Type" in the request header must be set to "application/edn".

Retrieve the complete history of a file by setting the url parameter "history" to "true".
Retrieve the version of a file since a particular time by setting the url parameter "since" to the desired time.
Retrieve the version of a file as-of a particular time by setting the url parameter "as-of" to the desired time.
Time parameters must take the following format: "yyyy-MM-dd'T'HH:mm:ss.SSSZ"

###Update
* **PATCH "files/:id":** takes a url parameter specifying a unique file id, and a form parameter specifying the new file with the form key "file", and updates the file

###Delete
* **DELETE "files/:id":** takes a url parameter specifying a unique file id, retracts the database entity


##Examples

```clojure
(require '[clj-http.client :as client])

; Get all files in database
(client/get "http://msg-fileservice.herokuapp.com/files"
    {:basic-auth ["user" "pass"]})

; Get single file edn
(client/get "http://msg-fileservice.herokuapp.com/files/17592186045419"
            {:basic-auth ["user" "pass"]
             :headers {"Content-Type" "application/edn"}})

; Download file
(:import [org.apache.commons.io IOUtils])

(defn get-file [id file-destination]
  (let [resp (client/get (str "http://msg-fileservice.herokuapp.com/files/" id)
                         {:basic-auth ["user" "pass"]})
        filename (second (clojure.string/split
                          (get (:headers resp) "Content-Disposition") #"="))]
    (with-open [out (clojure.java.io/output-stream (str file-destination filename ))]
    (.write out (IOUtils/toByteArray (:body resp))))))

; Upload file
(client/post "http://msg-fileservice.herokuapp.com/files"
               {:basic-auth ["user" "pass"]
                :multipart [{:name "file" :content (clojure.java.io/file file-path)}]})

; Upload multiple files
(client/post "http://msg-fileservice.herokuapp.com/files"
             {:basic-auth ["user" "pass"]
              :multipart [{:name "first-file" :content (clojure.java.io/file "/test-files/test-file-excel.xlsx")}
                          {:name "second-file" :content (clojure.java.io/file "/test-files/beach.jpg")}]})

; Update file
(client/patch "http://msg-fileservice.herokuapp.com/files/17592186045419"
               {:basic-auth ["user" "pass"
               :multipart [{:name "file" :content (clojure.java.io/file "/test-files/updated-file.docx")}]})

; Delete file
(client/delete "http://msg-fileservice.herokuapp.com/files/17592186045419"
    {:basic-auth ["user" "pass"]})

```


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
