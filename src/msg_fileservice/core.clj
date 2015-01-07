(ns msg-fileservice.core
  (:require [com.palletops.leaven :as leaven]
            [com.palletops.leaven.protocols :as protocols]
            [com.palletops.bakery.httpkit :as httpkit]
            [environ.core :as environ]
            [bidi.ring :as bidi-ring]
            [liberator.core :as liberator]
            [ring.middleware.edn :as edn]
            [ring.middleware.defaults :refer [wrap-defaults]]
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
              :db/ident              ::filename
              :db/valueType          :db.type/string
              :db/cardinality        :db.cardinality/one
              :db.install/_attribute :db.part/db}

             {:db/id                 #db/id[:db.part/db]
              :db/ident              ::s3-key
              :db/valueType          :db.type/uuid
              :db/cardinality        :db.cardinality/one
              :db/unique             :db.unique/identity
              :db.install/_attribute :db.part/db}]]

   ::routes ["/"
             {""
              (liberator/resource
               {:available-media-types ["text/html"]
                :allowed-methods [:get]
                :handle-ok
                "<html>Main Street Genome File Service</html>"})

              "files"
              (liberator/resource
               {:available-media-types ["application/edn"]
                :allowed-methods [:get :post]
                :handle-ok
                (fn [{{{{:keys [db-uri]} :environment} :service-data} :request}]
                  (d/q '[:find [(pull ?e [*]) ... ]
                         :where
                         [?e ::bucket]
                         [?e ::version]
                         [?e ::filename]
                         [?e ::s3-key]]
                       (d/db (d/connect db-uri))))
                :post!
                (fn [{{:keys [params]
                              { {:keys [db-uri]} :environment} :service-data}
                             :request}]
                         (if-let [ file-map
                                  (doall (map (fn [file]
                                                {:filename (:filename (file params))
                                                 :s3-key (s3/upload-existing-file
                                                          (:tempfile (file params)))})
                                              (keys params)))]
                           @(d/transact (d/connect db-uri)
                                        (mapv (fn [file]
                                                {:db/id (d/tempid :db.part/user)
                                                 ::bucket "msg-fileservice"
                                                 ::version (bigint 1)
                                                 ::filename (:filename file)
                                                 ::s3-key (:s3-key file )})
                                              file-map))))})

              ["file/" :s3-key]
              (liberator/resource
               {:available-media-types ["application/edn"]
                :allowed-methods [:get :delete]
                :handle-ok
                (fn [{{{:keys [s3-key]}                  :params
                       {{:keys [db-uri]} :environment}   :service-data
                       } :request }]
                  (d/q '[:find [(pull ?e [*]) ... ]
                         :in $ ?key
                         :where
                         [?e ::s3-key ?key]]
                       (d/db (d/connect db-uri))
                       (java.util.UUID/fromString s3-key)))
                :delete!
                (fn [{{{:keys [s3-key]}                  :params
                       {{:keys [db-uri]} :environment}   :service-data
                       } :request }]
                  (if-let [deleted-key (s3/delete-file! s3-key)]
                    @(d/transact (d/connect db-uri)
                                 [[:db.fn/retractEntity
                                   [::s3-key (java.util.UUID/fromString deleted-key)]]])))})

              ["filename/" :filename]
              (liberator/resource
               {:available-media-types ["application/edn"]
                :allowed-methods [:get]
                :handle-ok
                (fn [{{{:keys [filename]}                  :params
                       {{:keys [db-uri]} :environment}     :service-data
                       } :request }]
                  (d/q '[:find [(pull ?e [*]) ... ]
                         :in $ ?fname
                         :where
                         [?e ::filename ?fname]]
                       (d/db (d/connect db-uri))
                       filename))})

              ["download/" :s3-key]
              (liberator/resource
               {:available-media-types ["application/edn"]
                :allowed-methods [:get]
                :handle-ok
                (fn [{{{:keys [s3-key]}                  :params
                       {{:keys [db-uri]} :environment}   :service-data
                       } :request }]
                  (if-let [filename (d/q '[:find [(pull ?e [::filename]) ... ]
                                           :in $ ?key
                                           :where
                                           [?e ::s3-key ?key]]
                                         (d/db (d/connect db-uri))
                                         (java.util.UUID/fromString s3-key))]
                    (s3/download-file s3-key (::filename (first filename)))))})

              ["update"]
              (liberator/resource
               {:available-media-types ["application/edn"]
                :allowed-methods [:patch :get]
                :handle-ok
                (fn [req] (:request req))
                :patch!
                (fn [{{:keys [params]
                      { {:keys [db-uri]} :environment} :service-data}
                     :request}]
                  (if-let [updates
                           {:old-entity-data
                            (d/q '[:find [(pull ?e [:db/id
                                                    ::version
                                                    ::s3-key]) ... ]
                                   :in $ ?s3-key
                                   :where [?e ::s3-key ?s3-key]]
                                 (d/db (d/connect db-uri))
                                 (java.util.UUID/fromString
                                  ((first (vec (keys params))) params)))

                            :new-filename (:filename ((second (vec (keys params)))
                                                      params))
                            :new-key      (s3/upload-existing-file
                                           (:tempfile ((second (vec (keys params)))
                                                       params)))}]

                    @(d/transact (d/connect db-uri)
                                 [{:db/id (:db/id (first (:old-entity-data updates)))
                                   ::version (+ 1 (::version (first (:old-entity-data updates))))}
                                  {:db/id (:db/id (first (:old-entity-data updates)))
                                   ::filename (:new-filename updates)}

                                  {:db/id (:db/id (first (:old-entity-data updates)))
                                   ::s3-key (:new-key updates)}])))})
              }]})

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
(defn wrap-service-data [h service-data]
  "Adds service data to request map."
  (fn [req]
    (let [service-data-req (assoc req :service-data service-data)]
      (h service-data-req))))

;; Custom ring middleware settings
(def custom-wrap
  {:params    {:urlencoded true
               :multipart  true
               :nested     true
               :keywordize true}
   :responses {:not-modified-responses true
               :absolute-redirects     true
               :content-types          true}})

;; System Definition
(leaven/defsystem Server [:datomic :httpkit])


;; System Constructor
(defn server
  [ {:keys [::norms ::routes]
     {:keys [httpkit-port http-basic-credentials db-uri]} :environment
     :as service-data}]

  (map->Server
   ;;Create database component
   {:datomic (map->Datomic
              {:uri db-uri
               :norms norms})

    ;;Create webserver component
    :httpkit (httpkit/httpkit
              {:config  {:port httpkit-port}
               :handler (-> (bidi-ring/make-handler routes)
                            (wrap-service-data service-data)
                            (wrap-defaults custom-wrap)
                             edn/wrap-edn-params
                             )})

    }))

(defn -main
  [& args]
  (leaven/start (server (service-definition))))
