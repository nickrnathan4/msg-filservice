(ns msg-fileservice.core
  (:require [com.palletops.leaven :as leaven]
            [com.palletops.leaven.protocols :as protocols]
            [datomic.api :as d]
            [msg-fileservice.s3 :as s3]))


(defn service-definition []
  {
   :environment {:db-uri "datomic:mem://msg-fileservice"}
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
              :db.install/_attribute :db.part/db}]

            ]
   }
  )


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


(leaven/defsystem Server [:datomic])

(defn server
  [ {:keys [::norms :environment]}]
  (map->Server
   {:datomic
    (map->Datomic
     {:uri  (:db-uri environment)
      :norms norms})
    }))

(defn -main
  [& args]
  (leaven/start (server (service-definition))))
