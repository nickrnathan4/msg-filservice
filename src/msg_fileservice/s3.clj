(ns msg-fileservice.s3
  (:require [aws.sdk.s3 :as s3]
            [clojure.java.io :as io]
            [environ.core :refer [env]]
            [msg-fileservice.utils :as utils]))


(defn list-files []

  "Returns a list of maps containing the metadata for each file."

  (let [credentials {:access-key (env :aws-access-key)
                     :secret-key (env :aws-secret-key)}
        bucket      (env :s3-bucket)]
    (s3/list-objects credentials bucket)))


(defn download-file [s3-key filename]

  "Takes a file key (UUID) and file name [& file path].
   Writes the file to disk.
   Returns the corresponding Java file object."

  (let [credentials {:access-key (env :aws-access-key)
                     :secret-key (env :aws-secret-key)}
        bucket      (env :s3-bucket)]
    (io/input-stream
     (:content (s3/get-object credentials bucket s3-key)))))

(defn download-file-contents [s3-key]

  "Takes a file key (UUID).
  Returns the content of a file stored on S3."

  (let [credentials {:access-key (env :aws-access-key)
                     :secret-key (env :aws-secret-key)}
        bucket      (env :s3-bucket)]
    (slurp (:content (s3/get-object credentials bucket s3-key)))))



(defn upload-file [file-path file-content]

  "Creates a new file with all parent folders specified in the path provided.
   The specified file content is written to that file.
   The value of the file's key is the file name."

  (let [credentials {:access-key (env :aws-access-key)
                     :secret-key (env :aws-secret-key)}
        bucket      (env :s3-bucket)]
    (s3/put-object credentials bucket file-path file-content)))



(defn upload-existing-file [file-path]

  "Takes a file path and S3 bucket name.
  Uploads the file to S3 and returns the UUID."

  (let [s3-key (java.util.UUID/randomUUID)]
    (with-open [in (io/input-stream file-path)]
      (upload-file (str s3-key) in)
      s3-key)))



(defn delete-file! [s3-key]

  "Deletes a file and returns key"

  (let  [credentials {:access-key (env :aws-access-key)
                      :secret-key (env :aws-secret-key)}
         bucket      (env :s3-bucket)]
    (s3/delete-object credentials bucket s3-key)
    s3-key
    ))
