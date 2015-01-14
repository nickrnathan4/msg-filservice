(ns msg-fileservice.utils
  (:require [clojure.java.io :as io])
  (:import [org.apache.commons.io IOUtils]))

(defn object->file [in-stream filename]

  "Takes S3ObjectInputStream, converts to byte array
   and writes to file"

  (with-open [out (io/output-stream filename)]
    (.write out (IOUtils/toByteArray in-stream))))

(defn string->time [timestamp]

  "Takes a date time string formatted in the following format:
   yyyy-MM-dd'T'HH:mm:ss.SSSZ
   and returns a java.util.Date object"

  (.parse (java.text.SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ss.SSSZ") timestamp))

(defn time->string [java-time]

  "Takes a java.util.Date object in the following format:
   yyyy-MM-dd'T'HH:mm:ss.SSSZ
   and returns a string"

  (.format (java.text.SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ss.SSSZ") java-time))
