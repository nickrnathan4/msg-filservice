(ns user
  (:require [clojure.tools.namespace.repl :as repl]
            [com.palletops.leaven :as leaven]
            [com.palletops.bakery.httpkit :as httpkit]
            [bidi.ring :as bidi-ring]
            [ring.middleware.edn :as edn]
            [msg-fileservice.core :as core]
            [liberator.core :as liberator]
            [environ.core :as environ]
            [clj-time.core :as t]
            [datomic.api :as d]
            ))

(def system nil)


(defn init []
  (alter-var-root #'system
                  (constantly (core/server (core/service-definition)))))

(defn start []
  (alter-var-root #'system
                  leaven/start))

(defn stop []
  (alter-var-root #'system
                  (fn [s] (when s (leaven/stop s)))))

(defn go []
  (init)
  (start))

(defn reset []
  (stop)
  (repl/refresh :after 'user/go))



(clojure.core/comment

  (def testmap {:key1 "a" :key2 "b"})
  (vec (keys testmap) )


  (def mylist '("file1" "file2"))

  @(d/transact (d/connect "datomic:mem://msg-fileservice")
               (mapv (fn [file]
                       {:db/id #db/id[:db.part/user]
                        :msg-fileservice.core/bucket "msg-fileservice"
                        :msg-fileservice.core/version (bigint 1)
                        :msg-fileservice.core/s3-key file}
                       ) mylist))
  (def txr (mapv (fn [file]
                   {:db/id (d/tempid :db.part/user)
                 :msg-fileservice.core/bucket "msg-fileservice"
                 :msg-fileservice.core/version (bigint 1)
                 :msg-fileservice.core/s3-key file}
                ) mylist))

  (prn txr)


  (map (fn [file]
         (d/transact (d/connect "datomic:mem://msg-fileservice")
                     [{:db/id #db/id[:db.part/user]
                       :msg-fileservice.core/bucket "msg-fileservice"
                       :msg-fileservice.core/version (bigint 1)
                       :msg-fileservice.core/s3-key file}]
                     ))
       mylist)


  (map (d/transact (d/connect "datomic:mem://msg-fileservice")
                    [{:db/id #db/id[:db.part/user]
                      :msg-fileservice.core/bucket "msg-fileservice"
                      :msg-fileservice.core/version (bigint 1)
                      :msg-fileservice.core/s3-key "SUMASUMA"}])
       mylist)

  (fn [{:keys            [::routes
                          ::norms
                          tasks]
        {:keys [httpkit-port
                http-basic-credentials
                db-uri]} :environment
        :as              service-data}]
    db-uri
    )

  (fn [{{:keys [multipart-params]
         {:keys [db-uri]} :environment}
        :request} ]

    (map (fn [file]
           @(d/transact (d/connect db-uri)
                        [{:db/id #db/id[:db.part/user]
                          :msg-fileservice.core/bucket "msg-fileservice"
                          :msg-fileservice.core/version (bigint 1)
                          :msg-fileservice.core/s3-key file}]
                        ))
         (keys multipart-params))

    )

    :post!
    (fn [{{{{:keys [db-uri]} :environment} :service-data
           {:keys [key path]}      :params} :request}]
      @(d/transact (d/connect "datomic:mem://msg-fileservice")
                   [{:db/id #db/id[:db.part/user]
                     ::bucket "msg-fileservice"
                     ::version (bigint 1)
                     ::s3-key "YYYY"}]
                   )
      )

 (:Httpkit system)

(defn upload-file [file]
  @(d/transact (d/connect (:uri (:datomic system))) file))

(def sample-file [{:db/id #db/id[:db.part/user]
                   :msg-fileservice.core/bucket "msg-fileservice"
                   :msg-fileservice.core/version (bigint 1)
                   :msg-fileservice.core/s3-key "ABC"}])

(def sample-file2 [{:db/id #db/id[:db.part/user]
                    :msg-fileservice.core/bucket "msg-fileservice"
                    :msg-fileservice.core/version (bigint 1)
                    :msg-fileservice.core/s3-key "CDE"}])

(upload-file sample-file2)

(defn pull-files []
  (let [results (d/q '[:find [(pull ?e [:msg-fileservice.core/bucket
                                        :msg-fileservice.core/version
                                        :msg-fileservice.core/s3-key]) ... ]
                       :where
                       [?e :msg-fileservice.core/bucket]
                       [?e :msg-fileservice.core/version]
                       [?e :msg-fileservice.core/s3-key]]
                     (d/db (d/connect (:uri (:datomic system)))))]
    results))

(pull-files)

(def db-uri "datomic:mem://msg-fileservice")
 (def norms [[{:db/id                 #db/id[:db.part/db]
                :db/ident              :msg-fileservice
                :db.install/_partition :db.part/db}]

              ;; File
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
    )

(defn build-db [uri norms]
       (println "Creating db")
       (d/create-database uri)
       (println "DB created")
       (let [conn (d/connect uri)]
         (doseq [n norms]
           @(d/transact conn n)))
       )


(build-db db-uri norms)

(d/delete-database db-uri)

(def db (d/connect db-uri))

(def sample-file [{:db/id #db/id[:db.part/user]
                   ::bucket "msg-fileservice"
                   ::version (bigint 1)
                   ::s3-key "ABC"}])

(def sample-file2 [{:db/id #db/id[:db.part/user]
                   ::bucket "msg-fileservice"
                   ::version (bigint 1)
                   ::s3-key "CDEFG"}])

@(d/transact db sample-file)


(defn pull-files []
  (let [results (d/q '[:find [(pull ?e [::bucket
                                        ::s3-key
                                        ::version]) ... ]
                       :where
                       [?e ::bucket ?b]
                       [?e ::version ?v]
                       [?e ::s3-key ?s]]
                     (d/db db))]
    results))

(pull-files)

(defn pull-file
  [key]
  (let [results    (d/q
                    '[:find ?e
                      :in $ ?key
                      :where
                      [?e ::s3-key ?key]
                      ]
                    (d/db db)
                    key)]
    results))

(pull-file "ABC")

(d/q '[:find [(pull ?e [::bucket
                        ::s3-key
                        ::version]) ... ]
       :where
       [?e ::bucket ?b]
       [?e ::version ?v]
       [?e ::s3-key ?s]]
     (d/db db))

(d/pull (d/db db) '[::bucket ::s3-key] 17592186045419)

 )
