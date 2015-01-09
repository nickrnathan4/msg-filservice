(ns msg-fileservice.utils
  (:require [clojure.java.io :as io])
  (:import [org.apache.commons.io IOUtils]))

(defn object->file [in-stream filename]

  "Takes S3ObjectInputStream, converts to byte array
   and writes to file"

  (with-open [out (io/output-stream filename)]
    (.write out (IOUtils/toByteArray in-stream))))
