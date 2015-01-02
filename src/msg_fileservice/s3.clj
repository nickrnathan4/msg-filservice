(ns msg-fileservice.s3
  (:require [aws.sdk.s3 :as s3]
            [clojure.java.io :refer :all]
            [environ.core :refer [env]]))


(defn list-files []

  "Returns a list of maps containing the metadata for each file."

  (let [credentials {:access-key (env :aws-access-key)
                     :secret-key (env :aws-secret-key)}
        bucket      (env :s3-bucket)]
    (s3/list-objects credentials bucket)))



(defn download-file [file-key]

  "Takes a file key (UUID).
  Returns the content of a file stored on S3."

  (let [credentials {:access-key (env :aws-access-key)
                     :secret-key (env :aws-secret-key)}
        bucket      (env :s3-bucket)]
    (slurp (:content (s3/get-object credentials bucket file-key)))))



(defn upload-file [file-path file-content]

  "Creates a new file with all parent folders specified in the path provided.
   The specified file content is written to that file.
   The value of the file's key is the file name."

  (let [credentials {:access-key (env :aws-access-key)
                     :secret-key (env :aws-secret-key)}
        bucket      (env :s3-bucket)]
    (s3/put-object credentials bucket file-path file-content)))



(defn upload-existing-file  [file-path]

  "Takes a file path and S3 bucket name.
  Uploads the file to S3 and returns the UUID."

  (let [s3-key (java.util.UUID/randomUUID)]
    (upload-file (str s3-key)
                 (input-stream file-path))
    s3-key))



(defn delete-file! [file-key]

  "Deletes a file."

  (let  [credentials {:access-key (env :aws-access-key)
                      :secret-key (env :aws-secret-key)}
         bucket      (env :s3-bucket)]
    (s3/delete-object credentials bucket file-key)))
