(ns msg-fileservice.core
  (:require [com.palletops.leaven :as leaven]
            [com.palletops.leaven.protocols :as protocols]
            [com.palletops.bakery.httpkit :as httpkit]
            [environ.core :as environ]
            [bidi.ring :as bidi-ring]
            [liberator.core :as liberator]
            [ring.middleware.edn :as edn]
            [ring.middleware.basic-authentication :as basic-authentication]
            [datomic.api :as d]
            [msg-fileservice.s3 :as s3]))

;; Component Dependencies
(defn service-definition []
  {
   :environment {:httpkit-port 3006
                 :http-basic-credentials [(environ/env :msg-http-basic-username)
                                          (environ/env :msg-http-basic-password)]
                 :db-uri "datomic:mem://msg-fileservice"}

   ::norms [[{:db/id                 #db/id[:db.part/db]
              :db/ident              :msg-fileservice
              :db.install/_partition :db.part/db}]

            ;; File Schema
            [{:db/id                 #db/id[:db.part/db]
              :db/ident              ::bucket
              :db/valueType          :db.type/string
              :db/cardinality        :db.cardinality/one
              :db.install/_attribute :db.part/db}

             {:db/id                 #db/id[:db.part/db]
              :db/ident              ::version
              :db/valueType          :db.type/bigint
              :db/cardinality        :db.cardinality/one
              :db.install/_attribute :db.part/db}

             {:db/id                 #db/id[:db.part/db]
              :db/ident              ::s3-key
              :db/valueType          :db.type/string
              :db/cardinality        :db.cardinality/one
              :db.install/_attribute :db.part/db}]]

   :routes ["/"
            {""
             (liberator/resource
              {:available-media-types ["text/html"]
               :allowed-methods [:get]
               :handle-ok "<html>Main Street Genome File Service</html>"})}

            {""
             (liberator/resource
              {:available-media-types ["text/html"]
               :allowed-methods [:get]
               :handle-ok "<html>Main Street Genome File Service</html>"}
              )
             }
            ]
   }
  )

;; Database Component
(defrecord Datomic [uri norms]

  protocols/Startable
  (start [this]
    (println "Creating db")
    (d/create-database uri)
    (println "DB created")
    (let [conn (d/connect uri)]
      (doseq [n norms]
        @(d/transact conn n)))
    this)

  protocols/Stoppable
  (stop [this]
    (when (re-find #"^datomic:mem://" uri)
      (println "Deleting db: " uri)
      (d/delete-database uri))
    this))

;; Middleware
(defn wrap-service-data
  [h service-data]
  (fn [req]
    (let [service-data-req (assoc req :service-data service-data)]
      (h service-data-req))))


;; System Definition
(leaven/defsystem Server [:datomic :httpkit])


;; System Constructor
(defn server
  [ {:keys [::norms :environment :routes]}]
  (map->Server

   ;;Create database component
   {:datomic (map->Datomic
              {:uri  (:db-uri environment )
               :norms norms})

    ;;Create webserver component
    :httpkit (httpkit/httpkit
              {:config  {:port (:httpkit-port environment)}
               :handler (-> (bidi-ring/make-handler routes)
                            )})

    }))


(defn -main
  [& args]
  (leaven/start (server (service-definition))))
