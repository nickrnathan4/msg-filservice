(defproject msg-fileservice "0.1.0-SNAPSHOT"
  :description "CRUD file service backed by S3 and Datomic"
  :url "http://msg-fileservice.herokuapp.com"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [clj-time "0.8.0"]
                 [environ "1.0.0"]
                 [ring/ring-defaults "0.1.3"]
                 [com.palletops/leaven "0.2.1"]
                 [com.palletops/bakery-httpkit "0.2.0" :exclude [http-kit]]
                 [bidi "1.12.0" ]
                 [ring-basic-authentication "1.0.5"]
                 [fogus/ring-edn "0.2.0"]
                 [liberator "0.12.2"]
                 [clj-aws-s3 "0.3.10" :exclusions [joda-time]]
                 [datomic-schema "1.1.0"]
                 [com.datomic/datomic-pro "0.9.5052" :exclusions [joda-time]]]

  :uberjar-name "msg-fileservice-standalone.jar"
  :main msg-fileservice.core
  :profiles {
             :uberjar {:aot [msg-fileservice.core]}
             :dev {:source-paths ["dev" "src"]
                   :dependencies [[org.clojure/tools.namespace "0.2.3"]]}}
  :min-lein-version "2.0.0"
  :repositories {"my.datomic.com"
                 {:url      "https://my.datomic.com/repo"
                  :username ~(System/getenv "DATOMIC_USERNAME")
                  :password ~(System/getenv "DATOMIC_PASSWORD")}
                 }
  )
