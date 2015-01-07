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
            [msg-fileservice.s3 :as s3]
            [clojure.java.io :as io]
            [msg-fileservice.utils :as utils]
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
