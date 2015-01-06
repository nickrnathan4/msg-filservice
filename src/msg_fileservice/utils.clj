(ns msg-fileservice.utils
  (:require [clojure.java.io :as io]))

(defn write-file [filename lines]

  "Uses the Java BufferedWriter. Takes the name and optionally
   the file path as the first argument. Takes a list of lines
   as the second argument. Writes lines to the file specified."

  (with-open [wtr (io/writer filename)]
    (doseq [line lines] (.write wtr line))))
