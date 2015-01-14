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
            [msg-fileservice.utils :as utils]
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
                (fn [{{:keys [params]
                       { {:keys [db-uri]} :environment} :service-data}
                      :request}]
                  (if (not (nil? (:filename params)))
                    ;; Return file info based on filename parameter
                    (d/q '[:find [(pull ?e [*]) ... ]
                           :in $ ?fname
                           :where
                           [?e ::filename ?fname]]
                         (d/db (d/connect db-uri))
                         (:filename params))
                    ;; Return all files
                    (d/q '[:find [(pull ?e [*]) ... ]
                           :where
                           [?e ::bucket]
                           [?e ::filename]
                           [?e ::s3-key]]
                         (d/db (d/connect db-uri)))))

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
                                                 ::bucket (environ/env :s3-bucket)
                                                 ::filename (:filename file)
                                                 ::s3-key (:s3-key file )})
                                              file-map))))})

              ["files/" :id]
              (liberator/resource
               {:available-media-types ["application/edn"]
                :allowed-methods [:get :patch :delete]
                :handle-ok
                (fn [{{:keys [params content-type]
                      { {:keys [db-uri]} :environment} :service-data}
                     :request}]
                 ;; Return file edn
                 (if (= "application/edn" content-type)
                   (let [entity (:id params)
                         qry '[:find [(pull ?e [:msg-fileservice.core/s3-key
                                                :msg-fileservice.core/filename])...]
                               :in $ ?e
                               :where
                               [?e :msg-fileservice.core/s3-key]
                               [?e :msg-fileservice.core/filename]]]
                     (cond
                      ;; Return file history based on db id parameter
                      (and (not (nil? (:history params))) (read-string (:history params)))
                      (->> (d/q '[:find ?tx :in $ ?e :where [?e _ _ ?tx]]
                                (d/history (d/db (d/connect db-uri)))
                                (BigInteger. entity))
                           (map #(d/entity (d/db (d/connect db-uri)) (first %)))
                           (sort-by :db/txInstant)
                           (map (fn [tx]
                                  (-> (first (d/q qry
                                                  (d/as-of (d/db (d/connect db-uri)) (:db/txInstant tx))
                                                  (BigInteger. entity)))
                                      (assoc :db/txInstant (:db/txInstant tx))
                                      ))))

                      ;; Return file history as of a time parameter
                      (not (nil? (:as-of params)))
                      (d/q qry
                           (d/as-of (d/db (d/connect db-uri))
                                    (utils/string->time (:as-of params)))
                           (BigInteger. entity))
                      ;; Return file history since a time parameter
                      (not (nil? (:since params)))
                      (d/q qry
                           (d/since (d/db (d/connect db-uri))
                                    (utils/string->time (:since params)))
                           (BigInteger. entity))
                      :else
                      ;; Return current file edn
                      (d/pull (d/db (d/connect db-uri)) '[*] (BigInteger. entity))))

                   ;; Download File
                   (if (nil? (:s3-key params))
                     (let [file (d/pull (d/db (d/connect db-uri)) '[*] (BigInteger. (:id params)))]
                       (s3/download-file (str (::s3-key file)) (::filename file)))
                     (s3/download-file (:s3-key params) (:s3-key params)))))

                :patch!
                (fn [{{:keys [params]
                       { {:keys [db-uri]} :environment} :service-data}
                      :request}]
                  (if-let [updates
                           {:old-entity-data
                            (d/pull (d/db (d/connect db-uri)) '[*] (BigInteger. (:id params)))
                            :new-filename (:filename (:file params))
                            :new-key      (s3/upload-existing-file
                                           (:tempfile (:file params)))}]

                    @(d/transact (d/connect db-uri)
                                 [{:db/id (:db/id (:old-entity-data updates))
                                   ::filename (:new-filename updates)}
                                  {:db/id (:db/id (:old-entity-data updates))
                                   ::s3-key (:new-key updates)}])))

                :delete!
                (fn [{{{:keys [id]}                      :params
                       {{:keys [db-uri]} :environment}   :service-data
                       } :request }]
                  @(d/transact (d/connect db-uri)
                               [[:db.fn/retractEntity (BigInteger. id)]]))})

              ["echo"]
              (liberator/resource
               {:available-media-types ["application/edn"]
                :allowed-methods [:get]
                :handle-ok
                (fn [request] (:request request))})

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
                            (basic-authentication/wrap-basic-authentication
                             (fn [name pass]
                               (= [name pass] http-basic-credentials)))
                            )})
    }))

(defn -main
  [& args]
  (leaven/start (server (service-definition))))
